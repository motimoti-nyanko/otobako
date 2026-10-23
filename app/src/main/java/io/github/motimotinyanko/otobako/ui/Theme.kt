package io.github.motimotinyanko.otobako.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 配色。本体のライト/ダークに合わせる (isDark は MainActivity が起動時に設定。
 * 本体の設定が変わると Activity が作り直されるので、その時に切り替わる)
 */
object C {
    var isDark = true

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

/**
 * 曲ごとのアートワーク色 (無ければ既定の青)。保存してある色は暗い背景向けに明るくしてあるので、
 * ライトでは白地で読める濃さまで落とす
 */
fun accentColor(argb: Int): Color {
    if (argb == 0) return C.Accent
    if (C.isDark) return Color(argb)
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    hsv[1] = maxOf(hsv[1], 0.55f)
    hsv[2] = minOf(hsv[2], 0.62f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
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
        content = content,
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

// ---- 画像の読み込み (ローカルファイルのみなのでライブラリは使わない) ----

object Images {
    private val cache = object : LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun key(file: File, version: Any, maxPx: Int) = "${file.path}#$version#$maxPx"
    fun cached(k: String): ImageBitmap? = cache.get(k)

    fun decode(file: File, maxPx: Int): ImageBitmap? {
        if (!file.exists()) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, o)
        var sample = 1
        while (o.outWidth / (sample * 2) >= maxPx && o.outHeight / (sample * 2) >= maxPx) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }

    fun put(k: String, img: ImageBitmap) { cache.put(k, img) }
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
