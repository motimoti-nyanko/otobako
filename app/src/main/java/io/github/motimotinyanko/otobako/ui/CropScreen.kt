package io.github.motimotinyanko.otobako.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.motimotinyanko.otobako.data.CropRect
import io.github.motimotinyanko.otobako.data.Repo
import io.github.motimotinyanko.otobako.engine.Thumbs
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

/** サムネイルを正方形に切り抜く。ドラッグで移動、ピンチで大きさ */
@Composable
fun CropScreen(repo: Repo, id: String, onClose: () -> Unit) {
    val t = repo.get(id)
    if (t == null || !t.hasThumb) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val W = t.thumbW.toFloat()
    val H = t.thumbH.toFloat()
    val maxSide = min(W, H)
    val init = t.crop ?: Thumbs.centerSquare(t.thumbW, t.thumbH)
    // 切り抜く正方形 (thumb.jpg のピクセル座標)
    var side by remember { mutableFloatStateOf(init.size.toFloat().coerceIn(1f, maxSide)) }
    var cx by remember { mutableFloatStateOf(init.x + init.size / 2f) }
    var cy by remember { mutableFloatStateOf(init.y + init.size / 2f) }
    fun clamp() {
        side = side.coerceIn(maxSide * 0.15f, maxSide)
        cx = cx.coerceIn(side / 2, W - side / 2)
        cy = cy.coerceIn(side / 2, H - side / 2)
    }

    val img = rememberImage(File(repo.dir(id), "thumb.jpg"), t.thumbW, 2048)

    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "閉じる", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("正方形に切り抜く", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("ドラッグで移動 · ピンチで大きさを変更", color = Color(0xFFB0B0BA), fontSize = 12.sp)
            }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            val density = LocalDensity.current
            val boxW = with(density) { maxWidth.toPx() }
            val boxH = with(density) { maxHeight.toPx() }
            val scale = min(boxW / W, boxH / H)
            val dw = W * scale
            val dh = H * scale
            val ox = (boxW - dw) / 2
            val oy = (boxH - dh) / 2

            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(scale) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            side *= zoom
                            cx += pan.x / scale
                            cy += pan.y / scale
                            clamp()
                        }
                    },
            ) {
                img?.let {
                    drawImage(
                        it, srcOffset = IntOffset.Zero, srcSize = IntSize(it.width, it.height),
                        dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()), dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
                    )
                }
                val l = ox + (cx - side / 2) * scale
                val tp = oy + (cy - side / 2) * scale
                val s = side * scale
                val dim = Color.Black.copy(alpha = 0.62f)
                drawRect(dim, Offset(ox, oy), Size(dw, tp - oy))
                drawRect(dim, Offset(ox, tp + s), Size(dw, oy + dh - tp - s))
                drawRect(dim, Offset(ox, tp), Size(l - ox, s))
                drawRect(dim, Offset(l + s, tp), Size(ox + dw - l - s, s))
                // 三分割線
                for (i in 1..2) {
                    drawLine(Color.White.copy(alpha = 0.3f), Offset(l + s * i / 3, tp), Offset(l + s * i / 3, tp + s), 1f)
                    drawLine(Color.White.copy(alpha = 0.3f), Offset(l, tp + s * i / 3), Offset(l + s, tp + s * i / 3), 1f)
                }
                drawRect(Color.White, Offset(l, tp), Size(s, s), style = Stroke(2.dp.toPx()))
                // 角の印
                val k = 18.dp.toPx()
                val sw = 4.dp.toPx()
                listOf(Offset(l, tp) to Offset(1f, 1f), Offset(l + s, tp) to Offset(-1f, 1f), Offset(l, tp + s) to Offset(1f, -1f), Offset(l + s, tp + s) to Offset(-1f, -1f))
                    .forEach { (p, d) ->
                        drawLine(Color.White, p, Offset(p.x + d.x * k, p.y), sw)
                        drawLine(Color.White, p, Offset(p.x, p.y + d.y * k), sw)
                    }
            }
        }

        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // 仕上がりのプレビュー
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(C.Surface3)) {
                img?.let { im ->
                    val r = im.width / W
                    Canvas(Modifier.fillMaxSize()) {
                        drawImage(
                            im,
                            srcOffset = IntOffset(((cx - side / 2) * r).roundToInt(), ((cy - side / 2) * r).roundToInt()),
                            srcSize = IntSize((side * r).roundToInt().coerceAtLeast(1), (side * r).roundToInt().coerceAtLeast(1)),
                            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${side.roundToInt()} × ${side.roundToInt()}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                TextButton(
                    onClick = { side = maxSide; cx = W / 2; cy = H / 2 },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Icon(Icons.Rounded.CenterFocusStrong, null, tint = Color(0xFFB0B0BA), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("中央で最大", color = Color(0xFFB0B0BA), fontSize = 12.sp)
                }
            }
            Button(
                onClick = {
                    val s = side.roundToInt().coerceIn(1, maxSide.toInt())
                    val x = (cx - side / 2).roundToInt().coerceIn(0, t.thumbW - s)
                    val y = (cy - side / 2).roundToInt().coerceIn(0, t.thumbH - s)
                    repo.saveArt(id, CropRect(x, y, s))
                    onClose()
                },
                colors = ButtonDefaults.buttonColors(containerColor = C.Accent, contentColor = C.Bg),
                modifier = Modifier.height(48.dp),
            ) { Text("決定", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        }
    }
}
