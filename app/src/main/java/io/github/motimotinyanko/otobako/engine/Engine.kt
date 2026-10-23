package io.github.motimotinyanko.otobako.engine

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.io.File

/** yt-dlp / ffmpeg の初期化と、ffmpeg の直接実行 */
object Engine {
    private val ready = CompletableDeferred<Unit>()
    private lateinit var appContext: Context

    fun start(context: Context, scope: CoroutineScope) {
        appContext = context.applicationContext
        scope.launch(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().init(appContext)
                FFmpeg.getInstance().init(appContext)
                ready.complete(Unit)
            } catch (e: Throwable) {
                ready.completeExceptionally(e)
            }
        }
    }

    suspend fun await() = ready.await()

    class Result(val code: Int, val log: List<String>) {
        val ok get() = code == 0
        fun tail(n: Int = 6) = log.takeLast(n).joinToString("\n")
    }

    /**
     * ffmpeg を実行する。yt-dlp 用に展開された ffmpeg (nativeLibraryDir/libffmpeg.so) を、
     * youtubedl-android と同じ LD_LIBRARY_PATH で起動する。キャンセルでプロセスを殺す。
     */
    suspend fun ffmpeg(args: List<String>, onLine: ((String) -> Unit)? = null): Result = run("libffmpeg.so", args, 300, onLine)

    /** ffprobe (タグ・長さ・ビットレートの読み取り)。JSON を丸ごと返すので行数は切らない */
    suspend fun ffprobe(args: List<String>): Result = run("libffprobe.so", args, Int.MAX_VALUE, null)

    private suspend fun run(binName: String, args: List<String>, maxLines: Int, onLine: ((String) -> Unit)?): Result {
        await()
        val ctx = appContext
        val bin = File(ctx.applicationInfo.nativeLibraryDir, binName)
        val pkgs = File(ctx.noBackupFilesDir, "youtubedl-android/packages")
        val pb = ProcessBuilder(listOf(bin.absolutePath) + args).redirectErrorStream(true)
        pb.environment()["LD_LIBRARY_PATH"] =
            listOf("ffmpeg", "python", "aria2c").joinToString(":") { File(pkgs, "$it/usr/lib").absolutePath }
        pb.environment()["TMPDIR"] = ctx.cacheDir.absolutePath
        pb.environment()["HOME"] = ctx.cacheDir.absolutePath

        val p = pb.start()
        return coroutineScope {
            val killer = launch { try { awaitCancellation() } finally { p.destroy() } }
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
