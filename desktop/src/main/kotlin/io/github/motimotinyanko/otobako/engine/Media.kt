package io.github.motimotinyanko.otobako.engine

import io.github.motimotinyanko.otobako.data.CropRect
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 画像処理 (Skia: JPEG / PNG / WebP を読める) */
object Imaging {
    private val sampling = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR)

    fun decode(bytes: ByteArray): Image? = runCatching { Image.makeFromEncoded(bytes) }.getOrNull()
    fun decode(file: File): Image? = if (file.exists()) decode(file.readBytes()) else null

    /** 画像の (x, y, w, h) を (outW, outH) に描いた新しい画像 */
    fun draw(src: Image, x: Int, y: Int, w: Int, h: Int, outW: Int, outH: Int): Image {
        val s = Surface.makeRasterN32Premul(outW, outH)
        s.canvas.drawImageRect(src, Rect.makeXYWH(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat()), Rect.makeWH(outW.toFloat(), outH.toFloat()), sampling, null, true)
        return s.makeImageSnapshot()
    }

    fun scaled(src: Image, maxPx: Int): Image {
        val s = min(1f, maxPx.toFloat() / max(src.width, src.height))
        if (s >= 1f) return src
        return draw(src, 0, 0, src.width, src.height, (src.width * s).roundToInt().coerceAtLeast(1), (src.height * s).roundToInt().coerceAtLeast(1))
    }

    fun writeJpeg(img: Image, out: File, quality: Int = 95) {
        val data = img.encodeToData(EncodedImageFormat.JPEG, quality) ?: error("JPEG に変換できません")
        out.writeBytes(data.bytes)
    }
}

object Thumbs {
    private const val MAX_SHORT_SIDE = 1080

    /**
     * 候補を良い順に試し、一番大きい画像を out (JPEG) に保存する。
     * 短辺が 1080 を超える画像は 1080p 相当まで縮小、それ以下はそのまま。
     * @return (幅, 高さ) / 取れなければ null
     */
    fun fetch(candidates: List<String>, out: File): Pair<Int, Int>? {
        var best: ByteArray? = null
        var bestImg: Image? = null
        for (url in candidates.take(10)) {
            val bytes = runCatching { get(url) }.getOrNull() ?: continue
            val img = Imaging.decode(bytes) ?: continue
            if (bestImg == null || img.width * img.height > bestImg.width * bestImg.height) { best = bytes; bestImg = img }
            if (min(img.width, img.height) >= MAX_SHORT_SIDE) break
            // YouTube は maxresdefault (1280x720) が最大。720 以上が取れたら以降は小さくなるだけ
            if (min(img.width, img.height) >= 720 && url.contains("ytimg.com")) break
        }
        val img = bestImg ?: return null
        val bytes = best!!
        val isJpeg = bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
        val short = min(img.width, img.height)
        if (isJpeg && short <= MAX_SHORT_SIDE) {
            out.writeBytes(bytes)
            return img.width to img.height
        }
        val outImg = if (short > MAX_SHORT_SIDE) {
            val s = MAX_SHORT_SIDE.toFloat() / short
            Imaging.draw(img, 0, 0, img.width, img.height, (img.width * s).roundToInt(), (img.height * s).roundToInt())
        } else img
        Imaging.writeJpeg(outImg, out)
        return outImg.width to outImg.height
    }

    private fun get(url: String): ByteArray? {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/126 Safari/537.36")
        return try {
            if (c.responseCode != 200) null else c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    /** 正方形に切り抜いて art.jpg に保存 (元の解像度のまま) */
    fun crop(thumb: File, rect: CropRect, out: File) {
        val src = Imaging.decode(thumb) ?: error("サムネイルを読めません")
        val size = rect.size.coerceIn(1, min(src.width, src.height))
        val x = rect.x.coerceIn(0, src.width - size)
        val y = rect.y.coerceIn(0, src.height - size)
        Imaging.writeJpeg(Imaging.draw(src, x, y, size, size, size, size), out)
    }

    fun centerSquare(w: Int, h: Int): CropRect {
        val s = min(w, h)
        return CropRect((w - s) / 2, (h - s) / 2, s)
    }

    /** 暗い背景で映える代表色 (鮮やかで多い色相を選ぶ) */
    fun accentOf(file: File): Int {
        val src = Imaging.decode(file) ?: return 0
        val small = Imaging.draw(src, 0, 0, src.width, src.height, 32, 32)
        val bmp = Bitmap.makeFromImage(small)
        val weight = DoubleArray(24)
        val sum = Array(24) { DoubleArray(3) }
        val hsb = FloatArray(3)
        for (y in 0 until 32) for (x in 0 until 32) {
            val c = bmp.getColor(x, y)
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
            java.awt.Color.RGBtoHSB(r, g, b, hsb)
            val w = (hsb[1] * hsb[2]).toDouble().let { it * it }
            if (w < 0.02) continue
            val bin = (hsb[0] * 24).toInt().coerceIn(0, 23)
            weight[bin] += w
            sum[bin][0] += r * w; sum[bin][1] += g * w; sum[bin][2] += b * w
        }
        val best = weight.indices.maxByOrNull { weight[it] } ?: return 0
        if (weight[best] <= 0) return 0
        val r = (sum[best][0] / weight[best]).toInt(); val g = (sum[best][1] / weight[best]).toInt(); val b = (sum[best][2] / weight[best]).toInt()
        java.awt.Color.RGBtoHSB(r, g, b, hsb)
        return java.awt.Color.HSBtoRGB(hsb[0], min(hsb[1], 0.75f), max(hsb[2], 0.85f)) or (0xFF shl 24)
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
        // Windows ではメモリマップしたファイルを消せないので普通に読む (4kHz モノラルなので 10 分でも約 5MB)
        val buf = ByteBuffer.wrap(pcm.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        val n = buf.capacity() / 2
        for (i in 0 until n) {
            val b = (i.toLong() * BUCKETS / n).toInt()
            val v = abs(buf.getShort(i * 2).toInt()) / 32768f
            if (v > peaks[b]) peaks[b] = v
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
