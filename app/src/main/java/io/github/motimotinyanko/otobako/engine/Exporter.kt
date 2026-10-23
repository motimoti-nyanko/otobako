package io.github.motimotinyanko.otobako.engine

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.github.motimotinyanko.otobako.data.AppSettings
import io.github.motimotinyanko.otobako.data.BITRATES
import io.github.motimotinyanko.otobako.data.Track
import io.github.motimotinyanko.otobako.data.outTitle
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.pow

object Exporter {
    /** mp3gain / ReplayGain 1.0 の基準音量 */
    const val REFERENCE_DB = 89.0

    private fun n(v: Double) = String.format(Locale.US, "%.3f", v)

    /** カットとフェード (解析と本番で同じものを使う) */
    private fun fadeFilters(t: Track): List<String> {
        val len = t.outLength
        val f = mutableListOf<String>()
        if (t.fadeIn > 0) f += "afade=t=in:st=0:d=${n(t.fadeIn)}"
        if (t.fadeOut > 0) f += "afade=t=out:st=${n(max(0.0, len - t.fadeOut))}:d=${n(t.fadeOut)}"
        return f
    }

    private fun inputArgs(t: Track, audio: File): List<String> {
        val a = mutableListOf<String>()
        if (t.trimStart > 0) a += listOf("-ss", n(t.trimStart))
        if (t.trimEnd > 0) a += listOf("-to", n(t.trimEnd))
        a += listOf("-i", audio.path)
        return a
    }

    class Loudness(val gainDb: Double, val peak: Double)

    /** ffmpeg の replaygain フィルタで解析 (mp3gain と同じ ReplayGain のアルゴリズム) */
    suspend fun analyze(t: Track, audio: File): Loudness {
        val af = (fadeFilters(t) + "replaygain").joinToString(",")
        val r = Engine.ffmpeg(listOf("-hide_banner", "-nostdin") + inputArgs(t, audio) + listOf("-vn", "-af", af, "-f", "null", "-"))
        val gain = r.log.firstNotNullOfOrNull { Regex("""track_gain = ([+-]?[\d.]+) dB""").find(it)?.groupValues?.get(1)?.toDoubleOrNull() }
        val peak = r.log.firstNotNullOfOrNull { Regex("""track_peak = ([\d.]+)""").find(it)?.groupValues?.get(1)?.toDoubleOrNull() }
        if (!r.ok || gain == null) error("音量の解析に失敗しました\n${r.tail(3)}")
        return Loudness(gain, peak ?: 1.0)
    }

    fun fileName(t: Track): String {
        val title = t.outTitle
        val artist = t.tags.artist.trim()
        val base = if (artist.isEmpty()) title else "$title - $artist"
        return sanitize(base)
    }

    /** ファイル名に使えない文字は全角に置き換える */
    fun sanitize(s: String): String {
        val map = mapOf('/' to '／', '\\' to '＼', ':' to '：', '*' to '＊', '?' to '？', '"' to '”', '<' to '＜', '>' to '＞', '|' to '｜')
        return s.map { map[it] ?: if (it.code < 32) ' ' else it }.joinToString("").trim().trimEnd('.').take(150).ifEmpty { "untitled" }
    }

    /** ビットレート「元のまま」の端末内 mp3 を再エンコードする時の値 (元に近い標準値) */
    private fun encodeBitrate(t: Track) =
        if (t.bitrate > 0) t.bitrate else BITRATES.firstOrNull { it >= t.sourceAbr - 1 } ?: 320

    /**
     * 1 曲を書き出す。
     * - ダウンロードした曲: 解析 → (カット・フェード・音量・mp3 エンコード・タグ・アートワーク) を 1 回で → 保存先へ
     * - 端末内の mp3: 元のファイルを上書きするだけ (保存先フォルダには追加しない)。
     *   フェード無し・ビットレート元のままなら再エンコードせず、音量は mp3gain と同じ 1.5dB 刻みで無劣化に変える
     * @return 結果の説明 (ファイル名など)
     */
    suspend fun export(
        ctx: Context,
        t: Track,
        dir: File,
        s: AppSettings,
        onProgress: (stage: String, Float) -> Unit,
    ): String {
        val audio = File(dir, t.audioFile ?: error("音声がありません"))
        val localUri = t.localUri?.let(Uri::parse)
        val needCopy = localUri == null
        val tree = if (needCopy) {
            val treeUri = Uri.parse(s.outputUri ?: error("保存先フォルダが未設定です"))
            (DocumentFile.fromTreeUri(ctx, treeUri) ?: error("保存先フォルダを開けません")).also {
                if (!it.canWrite()) error("保存先フォルダに書き込めません。設定で選び直してください")
            }
        } else null

        onProgress("音量を解析中", 0f)
        val loud = analyze(t, audio)
        val gain = loud.gainDb + (s.targetDb - REFERENCE_DB)
        val peakAfter = loud.peak * 10.0.pow(gain / 20.0)

        // アートワーク: トリミング済みならそれ。未トリミングなら、ダウンロードした曲はサムネイル中央の正方形、
        // 端末内の mp3 は元の埋め込み画像をそのまま使う
        var tempArt: File? = null
        val thumb = File(dir, "thumb.jpg").takeIf { it.exists() && t.hasThumb }
        val art = File(dir, "art.jpg").takeIf { t.hasArt && it.exists() }
            ?: if (t.isLocal) thumb
            else thumb?.let { File(ctx.cacheDir, "art_${t.id}.jpg").also { a -> Thumbs.crop(it, Thumbs.centerSquare(t.thumbW, t.thumbH), a); tempArt = a } }

        val lossless = t.isLossless
        var steps = 0
        val tmp = File(ctx.cacheDir, "out_${t.id}.mp3")
        val args = mutableListOf("-hide_banner", "-nostdin", "-y")
        args += inputArgs(t, audio)
        if (art != null) args += listOf("-i", art.path)
        args += listOf("-map", "0:a:0")
        if (art != null) args += listOf(
            "-map", "1:v:0", "-c:v", "copy", "-disposition:v:0", "attached_pic",
            "-metadata:s:v:0", "title=Album cover", "-metadata:s:v:0", "comment=Cover (front)",
        )
        if (lossless) {
            // 音声はそのままコピー (カットはフレーム単位)。音量は後で global_gain を書き換える
            steps = Math.round(gain / Mp3Gain.STEP_DB).toInt()
            if (s.clipGuard) while (steps > 0 && loud.peak * 10.0.pow(steps * Mp3Gain.STEP_DB / 20.0) > 1.0) steps--
            args += listOf("-c:a", "copy")
        } else {
            val af = fadeFilters(t).toMutableList()
            af += "volume=${n(gain)}dB"
            if (s.clipGuard && peakAfter > 0.98) af += "alimiter=limit=0.977:level=0:attack=5:release=50"
            args += listOf("-af", af.joinToString(","), "-c:a", "libmp3lame", "-b:a", "${encodeBitrate(t)}k", "-ar", "44100")
        }
        args += listOf("-map_metadata", "-1", "-map_chapters", "-1", "-id3v2_version", "3", "-write_id3v1", "0")
        val tg = t.tags
        listOf(
            "title" to t.outTitle, "artist" to tg.artist, "album" to tg.album, "album_artist" to tg.albumArtist,
            "composer" to tg.composer, "date" to tg.year, "genre" to tg.genre, "track" to tg.track, "comment" to tg.comment,
        ).forEach { (k, v) -> if (v.isNotBlank()) args += listOf("-metadata", "$k=${v.trim()}") }
        args += listOf("-progress", "pipe:1", "-nostats", tmp.path)

        val total = t.outLength.coerceAtLeast(0.1)
        val stage = if (lossless) "タグと音量を書き込み中" else "mp3 に変換中"
        onProgress(stage, 0f)
        val r = try {
            Engine.ffmpeg(args) { line ->
                if (line.startsWith("out_time_us=")) {
                    line.substringAfter('=').toLongOrNull()?.let { onProgress(stage, (it / 1e6 / total).toFloat().coerceIn(0f, 1f)) }
                }
            }
        } finally {
            tempArt?.delete()
        }
        if (!r.ok || tmp.length() == 0L) { tmp.delete(); error("変換に失敗しました\n${r.tail(3)}") }
        if (lossless && steps != 0) Mp3Gain.apply(tmp, steps)

        onProgress("保存中", 1f)
        try {
            val saved = mutableListOf<String>()
            if (localUri != null) {
                LocalMp3.overwrite(ctx, localUri, tmp)
                saved += "上書き: ${t.localName ?: "元のファイル"}"
            }
            if (tree != null) {
                val name = fileName(t)
                val doc = tree.createFile("audio/mpeg", name) ?: error("ファイルを作成できません")
                ctx.contentResolver.openOutputStream(doc.uri, "w").use { os ->
                    os ?: error("ファイルに書き込めません")
                    tmp.inputStream().use { it.copyTo(os) }
                }
                saved += "保存: ${doc.name ?: "$name.mp3"}"
            }
            return saved.joinToString(" / ")
        } finally {
            tmp.delete()
        }
    }


    fun dbLabel(v: Float) = String.format(Locale.US, "%.1f dB", v)
}
