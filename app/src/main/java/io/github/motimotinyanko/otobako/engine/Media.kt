package io.github.motimotinyanko.otobako.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.palette.graphics.Palette
import io.github.motimotinyanko.otobako.data.CropRect
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object Thumbs {
    private const val MAX_SHORT_SIDE = 1080

    /**
     * 候補を良い順に試し、一番大きい画像を out (JPEG) に保存する。
     * 短辺が 1080 を超える画像は 1080p 相当まで縮小、それ以下はそのまま。
     * @return (幅, 高さ) / 取れなければ null
     */
    fun fetch(candidates: List<String>, out: File): Pair<Int, Int>? {
        var best: ByteArray? = null
        var bestArea = 0
        for (url in candidates.take(10)) {
            val bytes = runCatching { get(url) }.getOrNull() ?: continue
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            if (o.outWidth <= 0) continue
            val area = o.outWidth * o.outHeight
            if (area > bestArea) { best = bytes; bestArea = area }
            if (min(o.outWidth, o.outHeight) >= MAX_SHORT_SIDE) break
            // YouTube は maxresdefault (1280x720) が最大。720 以上が取れたら以降は小さくなるだけ
            if (min(o.outWidth, o.outHeight) >= 720 && url.contains("ytimg.com")) break
        }
        val bytes = best ?: return null

        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        val isJpeg = o.outMimeType == "image/jpeg"
        val short = min(o.outWidth, o.outHeight)
        if (isJpeg && short <= MAX_SHORT_SIDE) {
            out.writeBytes(bytes)
            return o.outWidth to o.outHeight
        }
        val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val bmp = if (short > MAX_SHORT_SIDE) {
            val s = MAX_SHORT_SIDE.toFloat() / short
            Bitmap.createScaledBitmap(src, (src.width * s).roundToInt(), (src.height * s).roundToInt(), true)
        } else src
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        return bmp.width to bmp.height
    }

    private fun get(url: String): ByteArray? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36")
        return try {
            if (c.responseCode != 200) null else c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    /** 正方形に切り抜いて art.jpg に保存 (元の解像度のまま) */
    fun crop(thumb: File, rect: CropRect, out: File) {
        val src = BitmapFactory.decodeFile(thumb.path) ?: error("サムネイルを読めません")
        val size = rect.size.coerceIn(1, min(src.width, src.height))
        val x = rect.x.coerceIn(0, src.width - size)
        val y = rect.y.coerceIn(0, src.height - size)
        val bmp = Bitmap.createBitmap(src, x, y, size, size)
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    }

    fun centerSquare(w: Int, h: Int): CropRect {
        val s = min(w, h)
        return CropRect((w - s) / 2, (h - s) / 2, s)
    }

    /** 暗い背景で映える代表色 */
    fun accentOf(file: File): Int {
        val o = BitmapFactory.Options().apply { inSampleSize = 4 }
        val bmp = BitmapFactory.decodeFile(file.path, o) ?: return 0
        val p = Palette.from(bmp).maximumColorCount(16).generate()
        val c = p.vibrantSwatch?.rgb ?: p.lightVibrantSwatch?.rgb ?: p.dominantSwatch?.rgb ?: return 0
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        hsv[1] = min(hsv[1], 0.75f)
        hsv[2] = max(hsv[2], 0.85f)
        return Color.HSVToColor(hsv)
    }
}

object Waveform {
    const val BUCKETS = 800

    /** 一覧・トリミング画面用の波形 (BUCKETS 個のピーク 0..1) を wave.bin に作る */
    suspend fun build(audio: File, out: File) {
        val pcm = File(out.parentFile, "wave.pcm")
        val r = Engine.ffmpeg(
            listOf(
                "-hide_banner", "-nostdin", "-y", "-i", audio.path, "-vn",
                "-ac", "1", "-ar", "4000", "-f", "s16le", "-c:a", "pcm_s16le", pcm.path,
            )
        )
        if (!r.ok) { pcm.delete(); error("波形の作成に失敗: ${r.tail(2)}") }
        val peaks = FloatArray(BUCKETS)
        RandomAccessFile(pcm, "r").use { raf ->
            val buf = raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length()).order(ByteOrder.LITTLE_ENDIAN)
            val n = (raf.length() / 2).toInt()
            if (n > 0) {
                for (i in 0 until n) {
                    val b = (i.toLong() * BUCKETS / n).toInt()
                    val v = abs(buf.getShort(i * 2).toInt()) / 32768f
                    if (v > peaks[b]) peaks[b] = v
                }
            }
        }
        pcm.delete()
        val mx = peaks.maxOrNull()?.takeIf { it > 0f } ?: 1f
        DataOutputStream(out.outputStream().buffered()).use { d ->
            d.writeInt(BUCKETS)
            peaks.forEach { d.writeFloat(it / mx) }
        }
    }

    fun read(f: File): FloatArray? = runCatching {
        DataInputStream(f.inputStream().buffered()).use { d ->
            FloatArray(d.readInt()) { d.readFloat() }
        }
    }.getOrNull()
}
