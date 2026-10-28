package io.github.motimotinyanko.otobako.engine

import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import org.json.JSONObject
import java.io.File

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
    /**
     * 元の音声をそのまま (再エンコードせず) dir/audio.<ext> に保存する。
     * mp3 への変換は書き出し時に 1 回だけ行うので、音質の劣化はその 1 回だけ。
     */
    suspend fun download(
        processId: String,
        url: String,
        dir: File,
        onInfo: (SourceInfo) -> Unit,
        onProgress: (Float, String) -> Unit,
    ): Pair<SourceInfo, File> = coroutineScope {
        Engine.await()
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

        val req = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("-f", "bestaudio/best")
            addOption("-x")
            addOption("--write-info-json")
            addOption("--no-mtime")
            addOption("--concurrent-fragments", 4)
            addOption("-o", File(dir, "audio.%(ext)s").absolutePath)
        }

        var finished = false
        val killer = launch {
            try { awaitCancellation() } finally { if (!finished) YoutubeDL.getInstance().destroyProcessById(processId) }
        }
        try {
            runInterruptible(Dispatchers.IO) {
                YoutubeDL.getInstance().execute(req, processId) { p, _, line ->
                    val stage = when {
                        line.contains("[ExtractAudio]") -> "音声を取り出し中"
                        line.contains("[download]") -> "ダウンロード中"
                        else -> null
                    }
                    if (stage != null) onProgress((p / 100f).coerceIn(0f, 1f), stage)
                }
            }
        } finally {
            finished = true
            killer.cancel()
            poller.cancel()
        }

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
        val o = JSONObject(f.readText())
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
            msg.contains("IP address is blocked", true) || msg.contains("not available in your country", true) ||
                msg.contains("geo restrict", true) -> "この投稿はお住まいの地域からは見られません"
            msg.contains("Sign in to confirm your age", true) -> "年齢制限のある動画です (ログインが必要)"
            msg.contains("Sign in", true) || msg.contains("login", true) -> "ログインが必要な動画です"
            msg.contains("Video unavailable", true) -> "動画が見つかりません (削除・非公開など)"
            msg.contains("Unable to download webpage", true) || msg.contains("Failed to resolve", true) ||
                msg.contains("timed out", true) -> "通信エラー。電波を確認して再試行してください"
            msg.contains("HTTP Error 403", true) -> "アクセスを拒否されました (設定で yt-dlp を更新すると直ることがあります)"
            else -> err.take(160).ifEmpty { "ダウンロードに失敗しました" }
        }
    }
}
