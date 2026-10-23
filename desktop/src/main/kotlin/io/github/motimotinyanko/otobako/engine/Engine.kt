package io.github.motimotinyanko.otobako.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.io.File

/** 同梱ツール (ffmpeg / ffprobe / yt-dlp / deno) の場所と実行 */
object Engine {
    /** アプリのデータ置き場。ダウンロードフォルダやピクチャには出さない */
    val home: File = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "Otobako").apply { mkdirs() }

    /** compose.application.resources.dir = 配布フォルダ内の app\resources (開発時の run も同じ仕組み) */
    val binDir: File by lazy {
        listOfNotNull(
            System.getProperty("compose.application.resources.dir")?.let { File(it, "bin") },
            File("res/windows/bin"),
            File("vendor/bin"),
        ).firstOrNull { File(it, "ffmpeg.exe").exists() } ?: error("同梱ツールが見つかりません")
    }
    val ffmpegExe get() = File(binDir, "ffmpeg.exe")
    val ffprobeExe get() = File(binDir, "ffprobe.exe")
    val denoExe get() = File(binDir, "deno.exe")

    /** yt-dlp は自己更新できるよう、書き込める %LOCALAPPDATA%\Otobako\bin に複製して使う */
    val ytdlpExe: File by lazy {
        val dst = File(home, "bin/yt-dlp.exe")
        if (!dst.exists()) {
            dst.parentFile.mkdirs()
            File(binDir, "yt-dlp.exe").copyTo(dst, overwrite = true)
        }
        dst
    }

    class Result(val code: Int, val log: List<String>) {
        val ok get() = code == 0
        fun tail(n: Int = 6) = log.takeLast(n).joinToString("\n")
    }

    suspend fun ffmpeg(args: List<String>, onLine: ((String) -> Unit)? = null) = run(ffmpegExe, args, 300, onLine)

    suspend fun ffprobe(args: List<String>) = run(ffprobeExe, args, Int.MAX_VALUE, null)

    suspend fun ytdlp(args: List<String>, onLine: ((String) -> Unit)? = null) = run(ytdlpExe, args, 400, onLine)

    /** 実行してログを集める。キャンセルされたら子プロセスごと止める */
    private suspend fun run(exe: File, args: List<String>, maxLines: Int, onLine: ((String) -> Unit)?): Result {
        val pb = ProcessBuilder(listOf(exe.absolutePath) + args).redirectErrorStream(true)
        pb.environment()["PYTHONUTF8"] = "1"
        pb.environment()["PYTHONIOENCODING"] = "utf-8"
        // ffmpeg の DLL は exe と同じフォルダ
        pb.environment()["PATH"] = binDir.absolutePath + File.pathSeparator + (System.getenv("PATH") ?: "")
        pb.directory(home)
        val p = pb.start()
        return coroutineScope {
            val killer = launch {
                try { awaitCancellation() } finally {
                    p.descendants().forEach { it.destroyForcibly() }
                    p.destroyForcibly()
                }
            }
            try {
                runInterruptible(Dispatchers.IO) {
                    val log = ArrayDeque<String>()
                    p.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { line ->
                        if (line.isBlank()) return@forEachLine
                        log.addLast(line)
                        if (log.size > maxLines) log.removeFirst()
                        onLine?.invoke(line)
                    }
                    Result(p.waitFor(), log.toList())
                }
            } finally {
                killer.cancel()
            }
        }
    }
}
