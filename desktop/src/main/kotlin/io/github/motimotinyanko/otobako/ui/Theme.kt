@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package io.github.motimotinyanko.otobako.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.SystemFont
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.motimotinyanko.otobako.engine.Imaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** 配色。Windows の「アプリ モード」(ライト/ダーク) に合わせ、変わったら追従する */
object C {
    var isDark by mutableStateOf(true)

    private fun pick(dark: Long, light: Long) = Color(if (isDark) dark else light)

    val Bg get() = pick(0xFF0B0B0E, 0xFFF4F4F7)
    val Surface get() = pick(0xFF15151A, 0xFFFFFFFF)
    val Surface2 get() = pick(0xFF1E1E25, 0xFFECECF1)
    val Surface3 get() = pick(0xFF282831, 0xFFE2E2E9)
    val Line get() = pick(0xFF2C2C36, 0xFFD9D9E1)
    val Text get() = pick(0xFFEDEDF2, 0xFF15151A)
    val Sub get() = pick(0xFF9D9DA8, 0xFF5D5D69)
    val Faint get() = pick(0xFF62626D, 0xFF9B9BA6)
    val Accent get() = pick(0xFF8FB3FF, 0xFF3A64D8)
    val Danger get() = pick(0xFFFF6B6B, 0xFFD43B3B)
    val Ok get() = pick(0xFF6BE3A4, 0xFF1E9A60)
    val Warn get() = pick(0xFFFFC46B, 0xFFB46A00)
}

/** レジストリの AppsUseLightTheme を読む (0 = ダーク) */
fun windowsDarkMode(): Boolean = runCatching {
    val p = ProcessBuilder("reg", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", "/v", "AppsUseLightTheme")
        .redirectErrorStream(true).start()
    val out = p.inputStream.bufferedReader().readText()
    p.waitFor(3, TimeUnit.SECONDS)
    Regex("""AppsUseLightTheme\s+REG_DWORD\s+0x(\d+)""").find(out)?.groupValues?.get(1)?.toInt(16) == 0
}.getOrDefault(false)

/**
 * 曲ごとのアートワーク色 (無ければ既定の青)。保存してある色は暗い背景向けに明るくしてあるので、
 * ライトでは白地で読める濃さまで落とす
 */
fun accentColor(argb: Int): Color {
    if (argb == 0) return C.Accent
    if (C.isDark) return Color(argb)
    val hsb = java.awt.Color.RGBtoHSB((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF, null)
    return Color(java.awt.Color.HSBtoRGB(hsb[0], maxOf(hsb[1], 0.55f), minOf(hsb[2], 0.62f)) or (0xFF shl 24))
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    // 本体の設定変更に 3 秒以内に追従
    LaunchedEffect(Unit) {
        while (true) {
            val d = withContext(Dispatchers.IO) { windowsDarkMode() }
            if (d != C.isDark) C.isDark = d
            delay(3000)
        }
    }
    MaterialTheme(
        colorScheme = (if (C.isDark) darkColorScheme() else lightColorScheme()).copy(
            primary = C.Accent,
            onPrimary = C.Bg,
            primaryContainer = C.Surface3,
            onPrimaryContainer = C.Text,
            secondary = C.Accent,
            secondaryContainer = C.Surface3,
            onSecondaryContainer = C.Text,
            background = C.Bg,
            onBackground = C.Text,
            surface = C.Surface,
            onSurface = C.Text,
            surfaceVariant = C.Surface2,
            onSurfaceVariant = C.Sub,
            surfaceContainer = C.Surface,
            surfaceContainerLow = C.Surface,
            surfaceContainerHigh = C.Surface2,
            surfaceContainerHighest = C.Surface3,
            outline = C.Line,
            outlineVariant = C.Line,
            error = C.Danger,
        ),
        typography = appTypography,
        content = content,
    )
}

/** 日本語は Windows 標準の Yu Gothic UI (英数字も同じ書体で揃える) */
private val appFont = FontFamily(
    SystemFont("Yu Gothic UI", FontWeight.Normal),
    SystemFont("Yu Gothic UI", FontWeight.SemiBold),
    SystemFont("Yu Gothic UI", FontWeight.Bold),
)

private val appTypography = Typography().let { t ->
    fun TextStyle.f() = copy(fontFamily = appFont)
    t.copy(
        displayLarge = t.displayLarge.f(), displayMedium = t.displayMedium.f(), displaySmall = t.displaySmall.f(),
        headlineLarge = t.headlineLarge.f(), headlineMedium = t.headlineMedium.f(), headlineSmall = t.headlineSmall.f(),
        titleLarge = t.titleLarge.f(), titleMedium = t.titleMedium.f(), titleSmall = t.titleSmall.f(),
        bodyLarge = t.bodyLarge.f(), bodyMedium = t.bodyMedium.f(), bodySmall = t.bodySmall.f(),
        labelLarge = t.labelLarge.f(), labelMedium = t.labelMedium.f(), labelSmall = t.labelSmall.f(),
    )
}

@Composable
fun Pill(text: String, color: Color = C.Sub, bg: Color = C.Surface3, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

// ---- 画像の読み込み ----

object Images {
    private val cache = object : java.util.LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 80
    }

    fun key(file: File, version: Any, maxPx: Int) = "${file.path}#$version#$maxPx"
    @Synchronized fun cached(k: String): ImageBitmap? = cache[k]
    @Synchronized fun put(k: String, img: ImageBitmap) { cache[k] = img }

    fun decode(file: File, maxPx: Int): ImageBitmap? =
        Imaging.decode(file)?.let { Imaging.scaled(it, maxPx) }?.toComposeImageBitmap()
}

@Composable
fun rememberImage(file: File?, version: Any, maxPx: Int = 512): ImageBitmap? {
    val k = file?.let { Images.key(it, version, maxPx) }
    var img by remember(k) { mutableStateOf(k?.let(Images::cached)) }
    LaunchedEffect(k) {
        if (img == null && file != null && k != null) {
            img = withContext(Dispatchers.IO) { Images.decode(file, maxPx) }?.also { Images.put(k, it) }
        }
    }
    return img
}
