package io.github.motimotinyanko.otobako.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class AppSettings(
    val bitrate: Int = 320,
    /** mp3gain の「目標ノーマル音量」と同じ基準 (ReplayGain 89dB) */
    val targetDb: Float = 89f,
    /** 音量を上げると音割れする曲はリミッターで抑える */
    val clipGuard: Boolean = true,
    val outputUri: String? = null,
    val outputName: String? = null,
    val ytdlpCheckedAt: Long = 0,
)

val BITRATES = listOf(128, 160, 192, 256, 320)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state

    private fun load() = AppSettings(
        bitrate = prefs.getInt("bitrate", 320),
        targetDb = prefs.getFloat("targetDb", 89f),
        clipGuard = prefs.getBoolean("clipGuard", true),
        outputUri = prefs.getString("outputUri", null),
        outputName = prefs.getString("outputName", null),
        ytdlpCheckedAt = prefs.getLong("ytdlpCheckedAt", 0),
    )

    fun edit(f: (AppSettings) -> AppSettings) {
        _state.update(f)
        val s = _state.value
        prefs.edit()
            .putInt("bitrate", s.bitrate)
            .putFloat("targetDb", s.targetDb)
            .putBoolean("clipGuard", s.clipGuard)
            .putString("outputUri", s.outputUri)
            .putString("outputName", s.outputName)
            .putLong("ytdlpCheckedAt", s.ytdlpCheckedAt)
            .apply()
    }
}
