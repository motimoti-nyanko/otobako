package io.github.motimotinyanko.otobako.data

import org.json.JSONObject

enum class Status { QUEUED, DOWNLOADING, PROCESSING, READY, FAILED }

data class Tags(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val composer: String = "",
    val year: String = "",
    val genre: String = "",
    val track: String = "",
    val comment: String = "",
) {
    fun toJson() = JSONObject()
        .put("title", title).put("artist", artist).put("album", album)
        .put("albumArtist", albumArtist).put("composer", composer).put("year", year)
        .put("genre", genre).put("track", track).put("comment", comment)

    companion object {
        fun fromJson(o: JSONObject?) = if (o == null) Tags() else Tags(
            title = o.optString("title"), artist = o.optString("artist"), album = o.optString("album"),
            albumArtist = o.optString("albumArtist"), composer = o.optString("composer"),
            year = o.optString("year"), genre = o.optString("genre"), track = o.optString("track"),
            comment = o.optString("comment"),
        )
    }
}

/** thumb.jpg 上の正方形 (ピクセル) */
data class CropRect(val x: Int, val y: Int, val size: Int)

data class Track(
    val id: String,
    val url: String,
    val addedAt: Long,
    val status: Status = Status.QUEUED,
    /** 0..1 */
    val progress: Float = 0f,
    val stage: String = "",
    val error: String? = null,
    val exportError: String? = null,

    // 元の動画の情報
    val sourceTitle: String = "",
    val uploader: String = "",
    val description: String = "",
    val site: String = "",
    val webpageUrl: String = "",
    val duration: Double = 0.0,
    val sourceCodec: String = "",
    val sourceAbr: Double = 0.0,

    // track ディレクトリ内のファイル
    val audioFile: String? = null,
    val thumbW: Int = 0,
    val thumbH: Int = 0,
    /** 0 = 未トリミング。値は画像キャッシュのキーにも使う */
    val artVersion: Long = 0,
    val crop: CropRect? = null,
    val accent: Int = 0,

    /** 端末内の mp3 から追加した場合の元ファイル (SAF のドキュメント URI) */
    val localUri: String? = null,
    val localName: String? = null,
    /** 端末内の動画 (mp4 等) から音声を取り出して作る曲の元ファイル。元ファイルは変えず、mp3 は保存先に新しく作る */
    val sourceFile: String? = null,

    // 編集内容
    val edited: Boolean = false,
    /** 0 = 元のまま (端末内の mp3 を再エンコードしない) */
    val bitrate: Int = 320,
    val tags: Tags = Tags(),
    val trimStart: Double = 0.0,
    /** 0 = 最後まで */
    val trimEnd: Double = 0.0,
    val fadeIn: Double = 0.0,
    val fadeOut: Double = 0.0,
) {
    val hasThumb get() = thumbW > 0
    val hasArt get() = artVersion > 0
    val isReady get() = status == Status.READY
    val isBusy get() = status == Status.QUEUED || status == Status.DOWNLOADING || status == Status.PROCESSING
    val endOrDuration get() = if (trimEnd > 0) trimEnd else duration
    val outLength get() = (endOrDuration - trimStart).coerceAtLeast(0.0)
    val isTrimmed get() = trimStart > 0.05 || (trimEnd > 0 && trimEnd < duration - 0.05)
    val isLocal get() = localUri != null
    val isVideoImport get() = sourceFile != null
    /** 端末内の mp3 で、再エンコードせずに保存できる (フェード無し・ビットレート元のまま) */
    val isLossless get() = isLocal && bitrate == 0 && fadeIn <= 0 && fadeOut <= 0

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("url", url).put("addedAt", addedAt)
        .put("status", status.name).put("error", error).put("exportError", exportError)
        .put("sourceTitle", sourceTitle).put("uploader", uploader).put("description", description)
        .put("site", site).put("webpageUrl", webpageUrl).put("duration", duration)
        .put("sourceCodec", sourceCodec).put("sourceAbr", sourceAbr)
        .put("audioFile", audioFile).put("thumbW", thumbW).put("thumbH", thumbH)
        .put("artVersion", artVersion)
        .put("crop", crop?.let { JSONObject().put("x", it.x).put("y", it.y).put("size", it.size) })
        .put("localUri", localUri).put("localName", localName).put("sourceFile", sourceFile)
        .put("accent", accent).put("edited", edited).put("bitrate", bitrate)
        .put("tags", tags.toJson())
        .put("trimStart", trimStart).put("trimEnd", trimEnd)
        .put("fadeIn", fadeIn).put("fadeOut", fadeOut)

    companion object {
        fun fromJson(o: JSONObject) = Track(
            id = o.getString("id"),
            url = o.getString("url"),
            addedAt = o.optLong("addedAt"),
            status = runCatching { Status.valueOf(o.optString("status")) }.getOrDefault(Status.QUEUED),
            error = o.optStringOrNull("error"),
            exportError = o.optStringOrNull("exportError"),
            sourceTitle = o.optString("sourceTitle"),
            uploader = o.optString("uploader"),
            description = o.optString("description"),
            site = o.optString("site"),
            webpageUrl = o.optString("webpageUrl"),
            duration = o.optDouble("duration", 0.0),
            sourceCodec = o.optString("sourceCodec"),
            sourceAbr = o.optDouble("sourceAbr", 0.0),
            audioFile = o.optStringOrNull("audioFile"),
            thumbW = o.optInt("thumbW"),
            thumbH = o.optInt("thumbH"),
            artVersion = o.optLong("artVersion"),
            crop = o.optJSONObject("crop")?.let { CropRect(it.optInt("x"), it.optInt("y"), it.optInt("size")) },
            localUri = o.optStringOrNull("localUri"),
            localName = o.optStringOrNull("localName"),
            sourceFile = o.optStringOrNull("sourceFile"),
            accent = o.optInt("accent"),
            edited = o.optBoolean("edited"),
            bitrate = o.optInt("bitrate", 320),
            tags = Tags.fromJson(o.optJSONObject("tags")),
            trimStart = o.optDouble("trimStart", 0.0),
            trimEnd = o.optDouble("trimEnd", 0.0),
            fadeIn = o.optDouble("fadeIn", 0.0),
            fadeOut = o.optDouble("fadeOut", 0.0),
        )
    }
}

fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

/** 書き出し時のファイル名に使う曲名とアーティスト */
val Track.outTitle get() = tags.title.ifBlank { sourceTitle }.ifBlank { "untitled" }

fun siteOf(url: String): String {
    val host = runCatching { java.net.URI(url).host.orEmpty().lowercase() }.getOrDefault("")
    return when {
        host.endsWith("youtube.com") || host == "youtu.be" -> "YouTube"
        host.endsWith("nicovideo.jp") || host == "nico.ms" -> "ニコニコ"
        host.endsWith("soundcloud.com") || host == "on.soundcloud.com" -> "SoundCloud"
        host.endsWith("twitter.com") || host == "x.com" || host.endsWith(".x.com") -> "X"
        host.endsWith("tiktok.com") -> "TikTok"
        else -> host.removePrefix("www.")
    }
}

fun formatTime(sec: Double, tenths: Boolean = false): String {
    val s = sec.coerceAtLeast(0.0)
    val m = (s / 60).toInt()
    val r = s - m * 60
    return if (tenths) "%d:%04.1f".format(java.util.Locale.US, m, r)
    else "%d:%02d".format(java.util.Locale.US, m, r.toInt())
}

/** 音声を取り出して mp3 にできる動画ファイルの拡張子 */
val VIDEO_EXTS = setOf("mp4", "m4v", "mov", "mkv", "webm", "avi", "3gp", "ts", "flv", "wmv")
