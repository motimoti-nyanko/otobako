package io.github.motimotinyanko.otobako.engine

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import io.github.motimotinyanko.otobako.data.Tags
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile

/** 端末内の mp3 の読み込み */
object LocalMp3 {
    data class Info(val duration: Double, val bitrate: Double, val tags: Tags)

    /** 元ファイルをアプリ内の作業用コピー (dir/audio.mp3) にする */
    fun copyIn(ctx: Context, uri: Uri, out: File) {
        ctx.contentResolver.openInputStream(uri).use { input ->
            input ?: error("ファイルを開けません")
            out.outputStream().use { input.copyTo(it) }
        }
        if (out.length() == 0L) error("空のファイルです")
    }

    suspend fun probe(audio: File): Info {
        val r = Engine.ffprobe(listOf("-v", "quiet", "-print_format", "json", "-show_format", "-show_streams", audio.path))
        if (!r.ok) error("mp3 を読めません")
        val o = JSONObject(r.log.joinToString("\n"))
        val format = o.optJSONObject("format") ?: JSONObject()
        val tagsJson = format.optJSONObject("tags") ?: JSONObject()
        // ID3 のキーは ffmpeg が小文字に揃えるが、念のため大小を区別しない
        val tags = tagsJson.keys().asSequence().associate { it.lowercase() to tagsJson.optString(it) }
        val streams = o.optJSONArray("streams")
        var abr = 0.0
        for (i in 0 until (streams?.length() ?: 0)) {
            val s = streams!!.getJSONObject(i)
            if (s.optString("codec_type") == "audio") abr = s.optString("bit_rate").toDoubleOrNull()?.div(1000) ?: 0.0
        }
        if (abr == 0.0) abr = format.optString("bit_rate").toDoubleOrNull()?.div(1000) ?: 0.0
        return Info(
            duration = format.optString("duration").toDoubleOrNull() ?: 0.0,
            bitrate = abr,
            tags = Tags(
                title = tags["title"].orEmpty(),
                artist = tags["artist"].orEmpty(),
                album = tags["album"].orEmpty(),
                albumArtist = tags["album_artist"] ?: tags["albumartist"].orEmpty(),
                composer = tags["composer"].orEmpty(),
                year = (tags["date"] ?: tags["year"]).orEmpty().take(4),
                genre = tags["genre"].orEmpty(),
                track = tags["track"].orEmpty(),
                comment = tags["comment"].orEmpty(),
            ),
        )
    }

    /** 埋め込みのアートワークを thumb.jpg に取り出す。@return (幅, 高さ) / 無ければ null */
    suspend fun extractCover(audio: File, out: File): Pair<Int, Int>? {
        out.delete()
        val r = Engine.ffmpeg(listOf("-hide_banner", "-nostdin", "-y", "-i", audio.path, "-map", "0:v:0", "-c:v", "copy", "-frames:v", "1", "-f", "image2", out.path))
        if (!r.ok || out.length() == 0L) { out.delete(); return null }
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(out.path, o)
        if (o.outWidth <= 0) { out.delete(); return null }
        return o.outWidth to o.outHeight
    }

    /** 元ファイルを書き出した mp3 で置き換える */
    fun overwrite(ctx: Context, uri: Uri, src: File) {
        val os = runCatching { ctx.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: error("元のファイルに書き込めません (書き込みの許可がありません)")
        os.use { o -> src.inputStream().use { it.copyTo(o) } }
    }
}

/**
 * mp3gain と同じ無劣化の音量変更。各グラニュールの global_gain (1 段 = 1.5dB) を書き換える。
 * 音声データは再エンコードしないので音質は一切変わらない。
 */
object Mp3Gain {
    const val STEP_DB = 1.5

    private val BR1 = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
    private val BR2 = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)
    private val SR = intArrayOf(44100, 48000, 32000)

    /** @return 書き換えたフレーム数 */
    fun apply(file: File, steps: Int): Int {
        if (steps == 0) return 0
        var frames = 0
        RandomAccessFile(file, "rw").use { f ->
            val len = f.length()
            var pos = skipId3(f)
            val hdr = ByteArray(4)
            var first = true
            while (pos + 4 <= len) {
                f.seek(pos)
                f.readFully(hdr)
                val h = ((hdr[0].toInt() and 0xFF) shl 24) or ((hdr[1].toInt() and 0xFF) shl 16) or
                    ((hdr[2].toInt() and 0xFF) shl 8) or (hdr[3].toInt() and 0xFF)
                val frame = parse(h)
                if (frame == null) { pos++; continue }
                if (pos + frame.length > len) break
                // 偶然 0xFFF に見えるデータを誤って書き換えないよう、次のフレームも正しく並んでいるか確かめる
                if (pos + frame.length + 4 <= len) {
                    f.seek(pos + frame.length)
                    val nx = ByteArray(4)
                    f.readFully(nx)
                    val nh = ((nx[0].toInt() and 0xFF) shl 24) or ((nx[1].toInt() and 0xFF) shl 16) or
                        ((nx[2].toInt() and 0xFF) shl 8) or (nx[3].toInt() and 0xFF)
                    val tail = String(nx, 0, 3, Charsets.ISO_8859_1)
                    if (parse(nh) == null && tail != "TAG" && tail != "APE" && tail != "LYR") { pos++; continue }
                }
                val buf = ByteArray(frame.length)
                f.seek(pos)
                f.readFully(buf)
                // 先頭の Xing/Info (LAME) フレームは音声ではないので触らない
                val isTag = first && String(buf, 4 + frame.crcBytes + frame.sideInfoBytes, 4, Charsets.ISO_8859_1).let { it == "Xing" || it == "Info" }
                first = false
                if (!isTag) {
                    adjust(buf, frame, steps)
                    f.seek(pos)
                    f.write(buf)
                    frames++
                }
                pos += frame.length
            }
        }
        return frames
    }

    private class Frame(val length: Int, val mpeg1: Boolean, val mono: Boolean, val crcBytes: Int) {
        val sideInfoBytes get() = if (mpeg1) (if (mono) 17 else 32) else (if (mono) 9 else 17)
    }

    private fun parse(h: Int): Frame? {
        if ((h ushr 21) and 0x7FF != 0x7FF) return null
        val ver = (h ushr 19) and 3 // 3=MPEG1, 2=MPEG2, 0=MPEG2.5
        if (ver == 1) return null
        if ((h ushr 17) and 3 != 1) return null // Layer III
        val protected = (h ushr 16) and 1 == 0
        val bri = (h ushr 12) and 0xF
        val sri = (h ushr 10) and 3
        if (bri == 0 || bri == 15 || sri == 3) return null
        val pad = (h ushr 9) and 1
        val mono = (h ushr 6) and 3 == 3
        val mpeg1 = ver == 3
        val sr = SR[sri] / (if (mpeg1) 1 else if (ver == 2) 2 else 4)
        val br = (if (mpeg1) BR1 else BR2)[bri] * 1000
        val length = (if (mpeg1) 144 else 72) * br / sr + pad
        if (length < 24) return null
        return Frame(length, mpeg1, mono, if (protected) 2 else 0)
    }

    private fun adjust(buf: ByteArray, f: Frame, steps: Int) {
        val side = 4 + f.crcBytes
        val nch = if (f.mono) 1 else 2
        val start: Int
        val block: Int
        val granules: Int
        if (f.mpeg1) {
            start = 9 + (if (f.mono) 5 else 3) + 4 * nch
            block = 59
            granules = 2
        } else {
            start = 8 + (if (f.mono) 1 else 2)
            block = 63
            granules = 1
        }
        for (gr in 0 until granules) for (ch in 0 until nch) {
            val bit = side * 8 + start + (gr * nch + ch) * block + 21
            val g = readBits(buf, bit, 8)
            writeBits(buf, bit, 8, (g + steps).coerceIn(0, 255))
        }
        if (f.crcBytes > 0) {
            // CRC はヘッダの後半 2 バイト + サイド情報が対象
            var crc = 0xFFFF
            fun feed(b: Int) {
                for (i in 7 downTo 0) {
                    val bitIn = (b ushr i) and 1
                    val top = (crc ushr 15) and 1
                    crc = (crc shl 1) and 0xFFFF
                    if (top xor bitIn == 1) crc = crc xor 0x8005
                }
            }
            feed(buf[2].toInt() and 0xFF)
            feed(buf[3].toInt() and 0xFF)
            for (i in 0 until f.sideInfoBytes) feed(buf[side + i].toInt() and 0xFF)
            buf[4] = (crc ushr 8).toByte()
            buf[5] = crc.toByte()
        }
    }

    private fun readBits(b: ByteArray, bit: Int, n: Int): Int {
        var v = 0
        for (i in 0 until n) {
            val p = bit + i
            v = (v shl 1) or ((b[p ushr 3].toInt() ushr (7 - (p and 7))) and 1)
        }
        return v
    }

    private fun writeBits(b: ByteArray, bit: Int, n: Int, value: Int) {
        for (i in 0 until n) {
            val p = bit + i
            val mask = 1 shl (7 - (p and 7))
            val on = (value ushr (n - 1 - i)) and 1 == 1
            b[p ushr 3] = (if (on) b[p ushr 3].toInt() or mask else b[p ushr 3].toInt() and mask.inv()).toByte()
        }
    }

    private fun skipId3(f: RandomAccessFile): Long {
        val h = ByteArray(10)
        f.seek(0)
        if (f.length() < 10) return 0
        f.readFully(h)
        if (h[0] != 'I'.code.toByte() || h[1] != 'D'.code.toByte() || h[2] != '3'.code.toByte()) return 0
        val size = ((h[6].toInt() and 0x7F) shl 21) or ((h[7].toInt() and 0x7F) shl 14) or ((h[8].toInt() and 0x7F) shl 7) or (h[9].toInt() and 0x7F)
        val footer = if (h[5].toInt() and 0x10 != 0) 10 else 0
        return 10L + size + footer
    }
}
