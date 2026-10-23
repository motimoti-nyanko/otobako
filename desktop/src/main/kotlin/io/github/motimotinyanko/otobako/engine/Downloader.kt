package io.github.motimotinyanko.otobako.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.IOException

data class SourceInfo(
    val title: String,
    val uploader: String,
    val description: String,
    val duration: Double,
    val webpageUrl: String,
    val acodec: String,
    val abr: Double,
    /** YouTube Music 等が持っている楽曲メタデータ */
    val track: String,
    val artist: String,
    val album: String,
    val year: String,
    /** 良い順 */
    val thumbnails: List<String>,
)

object Downloader {
    private val percent = Regex("""\[download]\s+([\d.]+)%""")

    /**
     * 元の音声をそのまま (再エンコードせず) dir/audio.<ext> に保存する。
     * mp3 への変換は書き出し時に 1 回だけ行うので、音質の劣化はその 1 回だけ。
     */
    suspend fun download(
        url: String,
        dir: File,
        onInfo: (SourceInfo) -> Unit,
        onProgress: (Float, String) -> Unit,
    ): Pair<SourceInfo, File> = coroutineScope {
        val infoFile = File(dir, "audio.info.json")
        infoFile.delete()
        var info: SourceInfo? = null

        // info.json はダウンロード開始前に書かれるので、出来たらすぐ一覧にタイトルを出す
        val poller = launch(Dispatchers.IO) {
            while (isActive && info == null) {
                if (infoFile.length() > 0) runCatching { parse(infoFile) }.onSuccess { info = it; onInfo(it) }
                delay(300)
            }
        }

        val args = listOf(
            "--no-playlist", "-f", "bestaudio/best", "-x",
            "--write-info-json", "--no-mtime", "--concurrent-fragments", "4",
            "--newline", "--no-color",
            "--ffmpeg-location", Engine.binDir.absolutePath,
            "--js-runtimes", "deno:${Engine.denoExe.absolutePath}",
            "--cache-dir", File(Engine.home, "cache").absolutePath,
            "-o", File(dir, "audio.%(ext)s").absolutePath,
            url,
        )

        val r = try {
            Engine.ytdlp(args) { line ->
                when {
                    line.contains("[ExtractAudio]") -> onProgress(1f, "音声を取り出し中")
                    else -> percent.find(line)?.groupValues?.get(1)?.toFloatOrNull()?.let {
                        onProgress((it / 100f).coerceIn(0f, 1f), "ダウンロード中")
                    }
                }
            }
        } finally {
            poller.cancel()
        }
        if (!r.ok) throw IOException(r.log.filter { it.contains("ERROR") }.ifEmpty { r.log.takeLast(3) }.joinToString("\n"))

        val parsed = info ?: parse(infoFile).also(onInfo)
        val audio = dir.listFiles().orEmpty()
            .filter {
                it.name.startsWith("audio.") && !it.name.endsWith(".json") &&
                    !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") && !it.name.contains(".temp")
            }
            .maxByOrNull { it.length() }
            ?: error("音声ファイルが見つかりません")
        parsed to audio
    }

    fun parse(f: File): SourceInfo {
        val o = JSONObject(f.readText(Charsets.UTF_8))
        val thumbs = mutableListOf<String>()
        // yt-dlp の thumbnails は悪い順に並んでいる
        o.optJSONArray("thumbnails")?.let { arr ->
            for (i in arr.length() - 1 downTo 0) arr.optJSONObject(i)?.optString("url")?.takeIf { it.isNotEmpty() }?.let(thumbs::add)
        }
        o.optString("thumbnail").takeIf { it.isNotEmpty() }?.let { thumbs.add(0, it) }

        val year = o.optInt("release_year", 0).takeIf { it > 0 }?.toString()
            ?: o.optString("upload_date").take(4).takeIf { it.length == 4 }.orEmpty()
        return SourceInfo(
            title = o.optString("title").ifEmpty { o.optString("fulltitle") },
            uploader = listOf("uploader", "channel", "creator", "uploader_id")
                .map { o.optString(it) }.firstOrNull { it.isNotEmpty() && it != "null" }.orEmpty(),
            description = o.optString("description").takeIf { it != "null" }.orEmpty(),
            duration = o.optDouble("duration", 0.0).takeIf { !it.isNaN() } ?: 0.0,
            webpageUrl = o.optString("webpage_url"),
            acodec = o.optString("acodec").takeIf { it != "null" && it != "none" }.orEmpty(),
            abr = o.optDouble("abr", 0.0).takeIf { !it.isNaN() } ?: 0.0,
            track = o.optString("track").takeIf { it != "null" }.orEmpty(),
            artist = (o.optString("artist").takeIf { it != "null" } ?: "")
                .ifEmpty { o.optJSONArray("artists")?.optString(0).orEmpty() },
            album = o.optString("album").takeIf { it != "null" }.orEmpty(),
            year = year,
            thumbnails = thumbs.distinct(),
        )
    }

    /** yt-dlp のエラー出力を短い日本語に */
    fun friendly(e: Throwable): String {
        val msg = e.message.orEmpty()
        val err = msg.lines().lastOrNull { it.contains("ERROR") }?.substringAfter("ERROR:")?.trim() ?: msg.lines().firstOrNull().orEmpty()
        return when {
            msg.contains("Unsupported URL", true) -> "対応していないリンクです"
            msg.contains("Private video", true) -> "非公開の動画です"
            msg.contains("Sign in to confirm your age", true) -> "年齢制限のある動画です (ログインが必要)"
            msg.contains("Sign in", true) || msg.contains("login", true) -> "ログインが必要な動画です"
            msg.contains("Video unavailable", true) -> "動画が見つかりません (削除・非公開など)"
            msg.contains("Unable to download webpage", true) || msg.contains("Failed to resolve", true) ||
                msg.contains("timed out", true) -> "通信エラー。ネットワークを確認して再試行してください"
            msg.contains("HTTP Error 403", true) -> "アクセスを拒否されました (設定で yt-dlp を更新すると直ることがあります)"
            else -> err.take(160).ifEmpty { "ダウンロードに失敗しました" }
        }
    }
}
