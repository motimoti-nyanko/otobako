package io.github.motimotinyanko.otobako.engine

import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.thread

/**
 * 試聴用のプレイヤー。ffmpeg で指定位置から PCM に変換し、Java のサウンド出力に流す。
 * volume はフェードの試聴用 (再生中に変えられる)
 */
class Player(private val file: File) {
    private val rate = 44100f
    private val format = AudioFormat(rate, 16, 2, true, false)

    @Volatile var volume = 1f
    @Volatile var isPlaying = false
        private set
    @Volatile private var startSec = 0.0
    @Volatile private var line: SourceDataLine? = null
    private var proc: Process? = null
    private var worker: Thread? = null
    private var pausedAt = 0.0

    /** 現在の再生位置 (秒) */
    val position: Double
        get() {
            val l = line
            return if (isPlaying && l != null) startSec + l.longFramePosition / rate.toDouble() else pausedAt
        }

    @Synchronized
    fun play(fromSec: Double) {
        stopInternal()
        startSec = fromSec.coerceAtLeast(0.0)
        pausedAt = startSec
        val p = ProcessBuilder(
            Engine.ffmpegExe.absolutePath, "-hide_banner", "-loglevel", "error", "-nostdin",
            "-ss", String.format(java.util.Locale.US, "%.3f", startSec), "-i", file.absolutePath,
            "-vn", "-ac", "2", "-ar", "44100", "-f", "s16le", "-",
        ).redirectError(ProcessBuilder.Redirect.DISCARD)
        p.environment()["PATH"] = Engine.binDir.absolutePath + File.pathSeparator + (System.getenv("PATH") ?: "")
        val pr = p.start()
        proc = pr
        val l = AudioSystem.getSourceDataLine(format).apply { open(format, 44100 * 4 / 5); start() }
        line = l
        isPlaying = true
        worker = thread(isDaemon = true, name = "otobako-player") {
            val buf = ByteArray(4096)
            try {
                pr.inputStream.use { input ->
                    while (isPlaying) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        val v = volume
                        if (v < 0.999f) {
                            var i = 0
                            while (i + 1 < n) {
                                val s = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xFF)).toShort()
                                val o = (s * v).toInt()
                                buf[i] = o.toByte(); buf[i + 1] = (o shr 8).toByte()
                                i += 2
                            }
                        }
                        l.write(buf, 0, n - (n % 4))
                    }
                }
                if (isPlaying) l.drain()
            } catch (_: Exception) {
            } finally {
                // 最後まで再生し終えた場合 (pause/stop された場合は line が差し替わっている)
                if (line === l) { pausedAt = startSec + l.longFramePosition / rate.toDouble(); isPlaying = false }
            }
        }
    }

    @Synchronized
    fun pause() {
        pausedAt = position
        stopInternal()
    }

    fun seekIdle(sec: Double) { if (!isPlaying) pausedAt = sec }

    private fun stopInternal() {
        isPlaying = false
        proc?.destroyForcibly()
        proc = null
        line?.let { runCatching { it.stop(); it.flush(); it.close() } }
        line = null
        worker = null
    }

    @Synchronized
    fun release() = stopInternal()
}
