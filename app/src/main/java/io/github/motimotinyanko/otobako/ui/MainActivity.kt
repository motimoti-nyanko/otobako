package io.github.motimotinyanko.otobako.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import io.github.motimotinyanko.otobako.App
import io.github.motimotinyanko.otobako.data.Repo

sealed interface Screen {
    data object Home : Screen
    data class Edit(val id: String) : Screen
    data class Crop(val id: String) : Screen
    data object Settings : Screen
}

class MainActivity : ComponentActivity() {
    private val repo: Repo get() = App.instance.repo
    /** クリップボードにある未追加のリンク (ワンタップ追加の候補) */
    val clipSuggestion = mutableStateOf<String?>(null)
    private var dismissedClip: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        C.isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleShare(intent)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContent {
            AppTheme {
                Root(repo, clipSuggestion.value, onAddClip = { u ->
                    repo.addFromText(u); clipSuggestion.value = null; dismissedClip = u
                }, onDismissClip = { dismissedClip = clipSuggestion.value; clipSuggestion.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND) {
            i.getStringExtra(Intent.EXTRA_TEXT)?.let { repo.addFromText(it) }
            i.action = null
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        // Android 10 以降はフォーカスを持っている時だけクリップボードを読める
        val cm = getSystemService(ClipboardManager::class.java)
        val text = runCatching { cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() }.getOrNull() ?: return
        val url = Regex("""https?://\S+""").find(text)?.value ?: return
        clipSuggestion.value = if (url != dismissedClip && !repo.hasUrl(url) && isSupported(url)) url else null
    }

    private fun isSupported(u: String) = listOf("youtube.com", "youtu.be", "nicovideo.jp", "nico.ms", "soundcloud.com", "twitter.com", "x.com")
        .any { u.contains(it, ignoreCase = true) }
}

@Composable
private fun Root(repo: Repo, clip: String?, onAddClip: (String) -> Unit, onDismissClip: () -> Unit) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val top = stack.last()
    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun replaceTop(s: Screen) { stack[stack.lastIndex] = s }

    BackHandler(enabled = stack.size > 1) { pop() }

    AnimatedContent(
        targetState = top,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        contentKey = { it::class },
        label = "screen",
    ) { s ->
        when (s) {
            Screen.Home -> HomeScreen(
                repo, clip, onAddClip, onDismissClip,
                onOpen = { push(Screen.Edit(it)) },
                onSettings = { push(Screen.Settings) },
            )
            is Screen.Edit -> EditScreen(
                repo, s.id,
                onBack = { pop() },
                onSwitch = { replaceTop(Screen.Edit(it)) },
                onCrop = { push(Screen.Crop(s.id)) },
            )
            is Screen.Crop -> CropScreen(repo, s.id, onClose = { pop() })
            Screen.Settings -> SettingsScreen(repo, onBack = { pop() })
        }
    }
}
