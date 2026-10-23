package io.github.motimotinyanko.otobako.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.io.File

data class AppSettings(
    val bitrate: Int = 320,
    /** mp3gain の「目標ノーマル音量」と同じ基準 (ReplayGain 89dB) */
    val targetDb: Float = 89f,
    /** 音量を上げると音割れする曲はリミッターで抑える */
    val clipGuard: Boolean = true,
    /** 保存先フォルダ (絶対パス) */
    val outputDir: String? = null,
    val ytdlpCheckedAt: Long = 0,
) {
    val outputName get() = outputDir?.let { File(it).name.ifEmpty { it } }
}

val BITRATES = listOf(128, 160, 192, 256, 320)

/** 設定は %LOCALAPPDATA%\Otobako\settings.json に保存 */
class SettingsStore(private val file: File) {
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state

    private fun load(): AppSettings = runCatching {
        val o = JSONObject(file.readText())
        AppSettings(
            bitrate = o.optInt("bitrate", 320),
            targetDb = o.optDouble("targetDb", 89.0).toFloat(),
            clipGuard = o.optBoolean("clipGuard", true),
            outputDir = o.optStringOrNull("outputDir"),
            ytdlpCheckedAt = o.optLong("ytdlpCheckedAt"),
        )
    }.getOrDefault(AppSettings())

    @Synchronized
    fun edit(f: (AppSettings) -> AppSettings) {
        _state.update(f)
        val s = _state.value
        val o = JSONObject()
            .put("bitrate", s.bitrate).put("targetDb", s.targetDb.toDouble()).put("clipGuard", s.clipGuard)
            .put("outputDir", s.outputDir).put("ytdlpCheckedAt", s.ytdlpCheckedAt)
        runCatching { file.parentFile.mkdirs(); file.writeText(o.toString(2)) }
    }
}
