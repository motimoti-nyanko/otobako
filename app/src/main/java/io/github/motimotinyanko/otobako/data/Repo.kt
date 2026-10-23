package io.github.motimotinyanko.otobako.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.yausername.youtubedl_android.YoutubeDL
import io.github.motimotinyanko.otobako.engine.Downloader
import io.github.motimotinyanko.otobako.engine.Engine
import io.github.motimotinyanko.otobako.engine.Exporter
import io.github.motimotinyanko.otobako.engine.LocalMp3
import io.github.motimotinyanko.otobako.engine.SourceInfo
import io.github.motimotinyanko.otobako.engine.Thumbs
import io.github.motimotinyanko.otobako.engine.Waveform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ExportState(
    val index: Int,
    val total: Int,
    val name: String,
    val stage: String,
    val progress: Float,
)

/** 保留中の曲の一覧と、ダウンロード・書き出しのジョブ */
class Repo(private val app: Context) {
    val root = File(app.filesDir, "tracks").apply { mkdirs() }
    val settings = SettingsStore(app)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _tracks = MutableStateFlow(loadAll())
    /** 新しい順 */
    val tracks: StateFlow<List<Track>> = _tracks

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages

    private val _export = MutableStateFlow<ExportState?>(null)
    val export: StateFlow<ExportState?> = _export

    private val _ytdlp = MutableStateFlow("")
    /** yt-dlp の版 / 更新状況の表示用 */
    val ytdlp: StateFlow<String> = _ytdlp

    private val jobs = ConcurrentHashMap<String, Job>()
    private val running = MutableStateFlow(0)
    private val slots = Semaphore(2)
    private val saveLock = Mutex()
    private var exportJob: Job? = null
    /** yt-dlp の更新中はダウンロードを始めない (実行ファイルを書き換えるため) */
    @Volatile private var updating: Job? = null

    val busy = combine(running, _export) { n, e -> n > 0 || e != null }

    fun dir(id: String) = File(root, id)
    fun get(id: String) = _tracks.value.find { it.id == id }
    fun say(msg: String) { _messages.tryEmit(msg) }

    private fun loadAll(): List<Track> =
        root.listFiles().orEmpty().mapNotNull { d ->
            runCatching { Track.fromJson(JSONObject(File(d, "track.json").readText())) }.getOrNull()
        }.sortedByDescending { it.addedAt }

    private suspend fun save(id: String) = saveLock.withLock {
        val t = get(id) ?: return@withLock
        val d = dir(id).apply { mkdirs() }
        val tmp = File(d, "track.json.tmp")
        tmp.writeText(t.toJson().toString())
        tmp.renameTo(File(d, "track.json"))
    }

    fun update(id: String, persist: Boolean = true, f: (Track) -> Track) {
        _tracks.update { list -> list.map { if (it.id == id) f(it) else it } }
        if (persist) scope.launch { save(id) }
    }

    // ---- 追加 ----

    private val urlRegex = Regex("""https?://[^\s<>"'「」『』]+""")

    private fun normalize(u: String) = u.trim().trimEnd('/').replace("://www.", "://").replace("://m.", "://")

    /** 貼り付けた文字列からリンクを全部拾って追加する。@return 追加した数 */
    fun addFromText(text: String): Int {
        val urls = urlRegex.findAll(text).map { it.value.trimEnd('.', ',', ')', '）', '」', '。', '、') }.distinct().toList()
        if (urls.isEmpty()) { say("リンクが見つかりません"); return 0 }
        val have = _tracks.value.map { normalize(it.url) }.toSet()
        val fresh = urls.filter { normalize(it) !in have }
        if (fresh.isEmpty()) { say("すでに追加済みです"); return 0 }
        val now = System.currentTimeMillis()
        val bitrate = settings.state.value.bitrate
        val added = fresh.mapIndexed { i, u ->
            Track(id = UUID.randomUUID().toString().take(12), url = u, addedAt = now + i, site = siteOf(u), bitrate = bitrate, stage = "待機中")
        }
        _tracks.update { (added.reversed() + it) }
        added.forEach { t -> scope.launch { save(t.id) }; startDownload(t.id) }
        if (urls.size > fresh.size) say("${fresh.size}件追加 (${urls.size - fresh.size}件は追加済み)")
        return added.size
    }

    fun hasUrl(u: String) = _tracks.value.any { normalize(it.url) == normalize(u) }

    /** 端末内の mp3 を追加 (複数可)。元ファイルは書き出し時に上書きする */
    fun addLocal(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val have = _tracks.value.mapNotNull { it.localUri }.toSet()
        val fresh = uris.filter { it.toString() !in have }
        if (fresh.isEmpty()) { say("すでに追加済みです"); return }
        val now = System.currentTimeMillis()
        val added = fresh.mapIndexed { i, u ->
            // 書き出し時に上書きできるよう、読み書きの権限を保持しておく
            runCatching {
                app.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }.recoverCatching { app.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = DocumentFile.fromSingleUri(app, u)?.name ?: "audio.mp3"
            Track(
                id = UUID.randomUUID().toString().take(12), url = u.toString(), addedAt = now + i,
                site = "端末", localUri = u.toString(), localName = name,
                sourceTitle = name.substringBeforeLast('.'), bitrate = 0, edited = true,
                status = Status.PROCESSING, stage = "読み込み中",
            )
        }
        _tracks.update { added.reversed() + it }
        added.forEach { t -> scope.launch { save(t.id) }; startDownload(t.id) }
        if (uris.size > fresh.size) say("${fresh.size}曲追加 (${uris.size - fresh.size}曲は追加済み)")
    }

    private suspend fun runImport(id: String) {
        val t = get(id) ?: return
        val d = dir(id).apply { mkdirs() }
        update(id, persist = false) { it.copy(status = Status.PROCESSING, stage = "読み込み中") }
        val audio = File(d, "audio.mp3")
        LocalMp3.copyIn(app, Uri.parse(t.localUri), audio)
        val info = LocalMp3.probe(audio)
        update(id) {
            it.copy(
                audioFile = audio.name, duration = info.duration, sourceCodec = "mp3", sourceAbr = info.bitrate,
                tags = info.tags.copy(title = info.tags.title.ifBlank { it.sourceTitle }),
                uploader = info.tags.artist,
            )
        }

        update(id, persist = false) { it.copy(stage = "アートワークを読み込み中") }
        val thumb = File(d, "thumb.jpg")
        LocalMp3.extractCover(audio, thumb)?.let { (w, h) ->
            val accent = runCatching { Thumbs.accentOf(thumb) }.getOrDefault(0)
            update(id) { it.copy(thumbW = w, thumbH = h, accent = accent) }
            // もともと正方形のジャケットならトリミング済みとして扱う
            if (w == h) {
                Thumbs.crop(thumb, CropRect(0, 0, w), File(d, "art.jpg"))
                update(id) { it.copy(crop = CropRect(0, 0, w), artVersion = System.currentTimeMillis()) }
            }
        }

        update(id, persist = false) { it.copy(stage = "波形を作成中") }
        runCatching { Waveform.build(audio, File(d, "wave.bin")) }
        update(id) { it.copy(status = Status.READY, stage = "", progress = 1f) }
    }

    // ---- ダウンロード ----

    fun startDownload(id: String) {
        if (jobs.containsKey(id)) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            running.update { it + 1 }
            try {
                update(id) { it.copy(status = Status.QUEUED, error = null, progress = 0f, stage = "待機中") }
                slots.withPermit {
                    if (get(id)?.isLocal == true) runImport(id) else { updating?.join(); runDownload(id) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                update(id) { it.copy(status = Status.FAILED, error = Downloader.friendly(e), stage = "") }
            } finally {
                jobs.remove(id)
                running.update { it - 1 }
            }
        }
        jobs[id] = job
        job.start()
    }

    private suspend fun runDownload(id: String) {
        val t = get(id) ?: return
        val d = dir(id).apply { mkdirs() }
        update(id, persist = false) { it.copy(status = Status.DOWNLOADING, stage = "情報を取得中") }
        val (info, audio) = Downloader.download(
            processId = "$id-${System.nanoTime()}",
            url = t.url,
            dir = d,
            onInfo = { applyInfo(id, it) },
            onProgress = { p, stage -> update(id, persist = false) { it.copy(progress = p, stage = stage) } },
        )
        applyInfo(id, info)
        update(id) { it.copy(status = Status.PROCESSING, stage = "サムネイルを取得中", audioFile = audio.name) }

        val thumb = File(d, "thumb.jpg")
        val size = runCatching { Thumbs.fetch(info.thumbnails, thumb) }.getOrNull()
        if (size != null) {
            val accent = runCatching { Thumbs.accentOf(thumb) }.getOrDefault(0)
            update(id) { it.copy(thumbW = size.first, thumbH = size.second, accent = accent) }
        }

        update(id, persist = false) { it.copy(stage = "波形を作成中") }
        runCatching { Waveform.build(audio, File(d, "wave.bin")) }
        // info.json は大きいので不要になったら消す
        File(d, "audio.info.json").delete()
        update(id) { it.copy(status = Status.READY, stage = "", progress = 1f) }
    }

    private fun applyInfo(id: String, info: SourceInfo) = update(id) { t ->
        val uploader = info.uploader
        val tags = if (t.edited || t.tags.title.isNotEmpty()) t.tags else Tags(
            title = info.track.ifEmpty { info.title },
            artist = info.artist.ifEmpty { TitleGuess.cleanUploader(uploader) },
            album = info.album,
            year = info.year,
        )
        t.copy(
            sourceTitle = info.title, uploader = uploader, description = info.description,
            webpageUrl = info.webpageUrl.ifEmpty { t.url }, duration = info.duration,
            sourceCodec = info.acodec, sourceAbr = info.abr, tags = tags,
        )
    }

    fun retry(id: String) = startDownload(id)

    fun remove(id: String) {
        jobs.remove(id)?.cancel()
        _tracks.update { list -> list.filterNot { it.id == id } }
        scope.launch { saveLock.withLock { dir(id).deleteRecursively() } }
    }

    // ---- 編集 ----

    fun saveArt(id: String, rect: CropRect) = scope.launch {
        val d = dir(id)
        try {
            Thumbs.crop(File(d, "thumb.jpg"), rect, File(d, "art.jpg"))
            val accent = runCatching { Thumbs.accentOf(File(d, "art.jpg")) }.getOrDefault(0)
            update(id) { it.copy(crop = rect, artVersion = System.currentTimeMillis(), accent = if (accent != 0) accent else it.accent, edited = true) }
        } catch (e: Throwable) {
            say("切り抜きに失敗しました: ${e.message}")
        }
    }

    /** アルバム名などを保留中の全曲にコピー */
    fun applyToAll(field: (Tags) -> String, set: (Tags, String) -> Tags, from: String) {
        val value = get(from)?.tags?.let(field) ?: return
        _tracks.value.filter { it.id != from }.forEach { t -> update(t.id) { it.copy(tags = set(it.tags, value)) } }
        say("${_tracks.value.size - 1}曲に適用しました")
    }

    // ---- 書き出し ----

    fun startExport(ids: List<String>) {
        if (exportJob?.isActive == true) return
        val s = settings.state.value
        // 保存先フォルダが要るのはダウンロードした曲だけ (端末の mp3 は元のファイルを上書きする)
        if (s.outputUri == null && ids.any { get(it)?.isLocal == false }) { say("先に保存先フォルダを選んでください"); return }
        exportJob = scope.launch {
            var ok = 0
            var overwritten = 0
            val failed = mutableListOf<String>()
            try {
                ids.forEachIndexed { i, id ->
                    val t = get(id)?.takeIf { it.isReady } ?: return@forEachIndexed
                    _export.value = ExportState(i, ids.size, t.outTitle, "準備中", 0f)
                    try {
                        Exporter.export(app, t, dir(id), s) { stage, p ->
                            _export.value = ExportState(i, ids.size, t.outTitle, stage, p)
                        }
                        ok++
                        if (t.isLocal) overwritten++
                        remove(id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        failed += t.outTitle
                        update(id) { it.copy(exportError = e.message?.lines()?.firstOrNull() ?: "失敗しました") }
                    }
                }
            } finally {
                _export.value = null
                val folder = s.outputName ?: "保存先"
                say(
                    when {
                        failed.isEmpty() && overwritten > 0 -> "${ok}曲を保存しました (うち端末の${overwritten}曲は元のファイルを上書き)"
                        failed.isEmpty() -> "${ok}曲を「$folder」に保存しました"
                        else -> "${ok}曲を保存、${failed.size}曲は失敗しました"
                    }
                )
            }
        }
    }

    fun cancelExport() { exportJob?.cancel() }

    // ---- yt-dlp ----

    fun refreshYtdlpVersion() = scope.launch {
        runCatching { Engine.await(); _ytdlp.value = YoutubeDL.getInstance().version(app) ?: "" }
    }

    /** サイト側の仕様変更に追従するため yt-dlp 本体を更新する */
    fun updateYtdlp(manual: Boolean) {
        if (updating?.isActive == true) return
        if (manual && running.value > 0) { say("ダウンロードが終わってから更新してください"); return }
        updating = scope.launch { doUpdateYtdlp(manual) }
    }

    private suspend fun doUpdateYtdlp(manual: Boolean) {
        try {
            Engine.await()
            if (manual) _ytdlp.value = "更新を確認中…"
            val st = YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel.STABLE)
            settings.edit { it.copy(ytdlpCheckedAt = System.currentTimeMillis()) }
            val v = YoutubeDL.getInstance().version(app).orEmpty()
            _ytdlp.value = v
            if (manual) say(if (st == YoutubeDL.UpdateStatus.DONE) "yt-dlp を $v に更新しました" else "yt-dlp は最新です ($v)")
        } catch (e: Throwable) {
            refreshYtdlpVersion()
            if (manual) say("yt-dlp の更新に失敗しました")
        }
    }

    fun storageBytes(): Long = root.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    fun setOutput(uri: Uri, name: String?) {
        settings.edit { it.copy(outputUri = uri.toString(), outputName = name) }
    }

    init {
        Engine.start(app, scope)
        refreshYtdlpVersion()
        // 1 日 1 回、自動で yt-dlp を更新 (ダウンロードは更新が終わるまで待つ)
        if (System.currentTimeMillis() - settings.state.value.ytdlpCheckedAt > 24 * 3600_000L) updateYtdlp(manual = false)
        // 途中で終わっていたダウンロードを再開
        _tracks.value.filter { it.isBusy }.forEach { startDownload(it.id) }
    }
}
