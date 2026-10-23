package io.github.motimotinyanko.otobako.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.motimotinyanko.otobako.data.BITRATES
import io.github.motimotinyanko.otobako.data.Repo
import io.github.motimotinyanko.otobako.engine.Exporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

/** 保存先フォルダを選んで設定に保存 */
@Composable
fun rememberFolderPicker(repo: Repo): () -> Unit = {
    Desk.pickFolder("保存先フォルダを選ぶ", repo.settings.state.value.outputDir)?.let(repo::setOutput)
}

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(title, color = C.Sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Surface).padding(16.dp),
            content = content,
        )
    }
}

/** 目標音量 (mp3gain の「目標ノーマル音量」と同じ意味) */
@Composable
fun TargetDbControl(value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("目標音量", color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text("mp3gain の目標ノーマル音量と同じ基準 (標準 89.0 dB)", color = C.Faint, fontSize = 11.sp)
        }
        FilledTonalIconButton(onClick = { onChange(((value - 0.5f) * 2).roundToInt() / 2f) }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.Remove, "下げる")
        }
        Text(
            String.format(Locale.US, "%.1f", value), color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        FilledTonalIconButton(onClick = { onChange(((value + 0.5f) * 2).roundToInt() / 2f) }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.Add, "上げる")
        }
    }
    Slider(
        value = value, onValueChange = { onChange((it * 10).roundToInt() / 10f) }, valueRange = 80f..100f,
        colors = SliderDefaults.colors(thumbColor = C.Accent, activeTrackColor = C.Accent, inactiveTrackColor = C.Surface3),
    )
}

@Composable
fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(sub, color = C.Faint, fontSize = 11.sp)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = C.Accent, checkedThumbColor = C.Bg))
    }
}

@Composable
fun FolderRow(name: String?, onPick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onPick), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Folder, null, tint = if (name == null) C.Warn else C.Accent)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("保存先フォルダ", color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(name ?: "未設定 (タップして選ぶ)", color = if (name == null) C.Warn else C.Sub, fontSize = 12.sp)
        }
        TextButton(onClick = onPick) { Text(if (name == null) "選ぶ" else "変更", color = C.Accent) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BitrateSelector(value: Int, keepOption: Boolean = false, onChange: (Int) -> Unit) {
    // keepOption: PC 内の mp3 用に「元のまま」(= 0, 再エンコードしない) を先頭に出す
    val options = (if (keepOption) listOf(0) else emptyList()) + BITRATES
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, b ->
            SegmentedButton(
                selected = b == value,
                onClick = { onChange(b) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = C.Accent.copy(alpha = 0.22f), activeContentColor = C.Text,
                    inactiveContainerColor = C.Surface, inactiveContentColor = C.Sub,
                    activeBorderColor = C.Line, inactiveBorderColor = C.Line,
                ),
                icon = {},
            ) { Text(if (b == 0) "元のまま" else "$b", fontSize = if (b == 0) 11.sp else 13.sp, maxLines = 1) }
        }
    }
}

@Composable
fun SettingsPane(repo: Repo, onBack: () -> Unit) {
    val s by repo.settings.state.collectAsState()
    val ytdlp by repo.ytdlp.collectAsState()
    val tracks by repo.tracks.collectAsState()
    val pickFolder = rememberFolderPicker(repo)
    var bytes by remember { mutableLongStateOf(0L) }
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(tracks.size) { bytes = withContext(Dispatchers.IO) { repo.storageBytes() } }

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("設定", color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onBack) { Icon(Icons.Rounded.Close, "閉じる (Esc)", tint = C.Sub) }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Section("ダウンロード") {
                Text("新しく追加する曲のビットレート (kbps)", color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                BitrateSelector(s.bitrate) { b -> repo.settings.edit { it.copy(bitrate = b) } }
                Spacer(Modifier.height(8.dp))
                Text(
                    "元の音声のままアプリ内に保留し、書き出す時に 1 回だけ mp3 に変換します。曲ごとに編集画面でも変えられます。",
                    color = C.Faint, fontSize = 11.sp, lineHeight = 16.sp,
                )
            }
            Section("書き出し") {
                TargetDbControl(s.targetDb) { v -> repo.settings.edit { it.copy(targetDb = v.coerceIn(80f, 100f)) } }
                Spacer(Modifier.height(8.dp))
                SwitchRow("音割れ防止", "音量を上げると音割れする曲はリミッターで抑える", s.clipGuard) { v -> repo.settings.edit { it.copy(clipGuard = v) } }
                Spacer(Modifier.height(12.dp))
                FolderRow(s.outputName, pickFolder)
                Spacer(Modifier.height(8.dp))
                Text("ファイル名: 曲名 - アーティスト.mp3", color = C.Faint, fontSize = 11.sp)
            }
            Section("yt-dlp (ダウンロードエンジン)") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("バージョン", color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(ytdlp.ifEmpty { "読み込み中…" }, color = C.Sub, fontSize = 12.sp)
                        Text("1 日 1 回自動で更新します。ダウンロードに失敗する時は手動で更新してください。", color = C.Faint, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                    Button(
                        onClick = { repo.updateYtdlp(manual = true) },
                        colors = ButtonDefaults.buttonColors(containerColor = C.Surface3, contentColor = C.Text),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) { Icon(Icons.Rounded.SystemUpdate, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("更新") }
                }
            }
            Section("保留中のデータ") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${tracks.size}曲 · ${formatBytes(bytes)}", color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text("アプリ専用の領域 (%LOCALAPPDATA%\\Otobako) に保存。ダウンロードフォルダやピクチャには表示されません。", color = C.Faint, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                    IconButton(
                        onClick = { confirmClear = true }, enabled = tracks.isNotEmpty(),
                        colors = IconButtonDefaults.iconButtonColors(contentColor = C.Danger),
                    ) { Icon(Icons.Rounded.DeleteSweep, "すべて削除") }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("保留中の曲をすべて削除") },
            text = { Text("${tracks.size}曲のダウンロード済みデータと編集内容が消えます。") },
            confirmButton = {
                TextButton(onClick = { tracks.forEach { repo.remove(it.id) }; confirmClear = false }) { Text("削除", color = C.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("やめる") } },
            containerColor = C.Surface2,
        )
    }
}

fun formatBytes(b: Long): String = when {
    b >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", b / (1L shl 20).toDouble())
    else -> "${b / 1024} KB"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDialog(repo: Repo, onDismiss: () -> Unit) {
    val s by repo.settings.state.collectAsState()
    val tracks by repo.tracks.collectAsState()
    val pickFolder = rememberFolderPicker(repo)
    val ready = tracks.filter { it.isReady }
    val busy = tracks.count { it.isBusy }
    val noArt = ready.count { !it.hasArt && !it.isLocal }
    val local = ready.filter { it.isLocal }
    val noTag = ready.count { !it.edited }

    Dialog(onDismissRequest = onDismiss) { Surface(shape = RoundedCornerShape(24.dp), color = C.Surface2, modifier = Modifier.width(540.dp)) {
        Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("まとめて書き出し", color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "${ready.size}曲を、目標音量にそろえて mp3 で保存します。" + if (busy > 0) "\nダウンロード中の${busy}曲は対象外です。" else "",
                color = C.Sub, fontSize = 13.sp, lineHeight = 19.sp,
            )
            TargetDbControl(s.targetDb) { v -> repo.settings.edit { it.copy(targetDb = v.coerceIn(80f, 100f)) } }
            SwitchRow("音割れ防止", "音量を上げると音割れする曲はリミッターで抑える", s.clipGuard) { v -> repo.settings.edit { it.copy(clipGuard = v) } }
            FolderRow(s.outputName, pickFolder)
            if (noArt > 0 || noTag > 0) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Warn.copy(alpha = 0.10f)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (noArt > 0) Text("・アートワーク未トリミング ${noArt}曲 → サムネイルの中央を正方形で使います", color = C.Warn, fontSize = 12.sp)
                    if (noTag > 0) Text("・タグ未編集 ${noTag}曲 → 動画タイトルとアカウント名のまま保存します", color = C.Warn, fontSize = 12.sp)
                }
            }
            if (local.isNotEmpty()) {
                val lossless = local.count { it.isLossless }
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Accent.copy(alpha = 0.10f)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("・PC 内の mp3 ${local.size}曲は元のファイルを上書きします", color = C.Text, fontSize = 12.sp)
                    Text("・保存先フォルダには新しく追加しません", color = C.Text, fontSize = 12.sp)
                    if (lossless > 0) Text("・${lossless}曲は再エンコードせず無劣化で保存 (音量は mp3gain と同じ 1.5dB 刻み)", color = C.Sub, fontSize = 12.sp)
                }
            }
            Text("ファイル名: 曲名 - アーティスト.mp3 · 書き出した曲は一覧から消えます", color = C.Faint, fontSize = 11.sp)
            // 保存先フォルダが要るのはダウンロードした曲があるときだけ
            val needFolder = s.outputDir == null && ready.any { !it.isLocal }
            Button(
                onClick = {
                    if (needFolder) pickFolder() else { repo.startExport(ready.map { it.id }); onDismiss() }
                },
                enabled = ready.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = C.Accent, contentColor = C.Bg),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(
                    if (needFolder) "保存先フォルダを選ぶ" else "${ready.size}曲を ${Exporter.dbLabel(s.targetDb)} で書き出す",
                    fontWeight = FontWeight.Bold, fontSize = 15.sp,
                )
            }
        }
    }
}
}
