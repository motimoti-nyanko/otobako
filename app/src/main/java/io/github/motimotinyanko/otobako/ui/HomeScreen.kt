package io.github.motimotinyanko.otobako.ui

import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import io.github.motimotinyanko.otobako.R
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.motimotinyanko.otobako.data.BITRATES
import io.github.motimotinyanko.otobako.data.Repo
import io.github.motimotinyanko.otobako.data.Status
import io.github.motimotinyanko.otobako.data.Track
import io.github.motimotinyanko.otobako.data.formatTime
import io.github.motimotinyanko.otobako.data.outTitle
import io.github.motimotinyanko.otobako.engine.Exporter
import java.io.File

@Composable
fun HomeScreen(
    repo: Repo,
    clip: String?,
    onAddClip: (String) -> Unit,
    onDismissClip: () -> Unit,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
) {
    val tracks by repo.tracks.collectAsState()
    val settings by repo.settings.state.collectAsState()
    val export by repo.export.collectAsState()
    val snack = remember { SnackbarHostState() }
    var showExport by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { repo.messages.collect { snack.showSnackbar(it) } }

    Scaffold(
        containerColor = C.Bg,
        snackbarHost = { SnackbarHost(snack, Modifier.padding(bottom = if (tracks.isNotEmpty()) 84.dp else 0.dp)) },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()).imePadding()) {
            Column(Modifier.fillMaxSize()) {
                Header(tracks, onSettings)
                PasteBar(repo, settings.bitrate)
                AnimatedVisibility(clip != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    clip?.let { ClipBanner(it, onAdd = { onAddClip(it) }, onDismiss = onDismissClip) }
                }
                if (tracks.isEmpty()) {
                    EmptyState()
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(tracks, key = { it.id }) { t ->
                            TrackCard(
                                repo, t,
                                modifier = Modifier.animateItem(),
                                onOpen = {
                                    if (t.isReady) onOpen(t.id) else repo.say("ダウンロードが終わると編集できます")
                                },
                            )
                        }
                    }
                }
            }
            if (tracks.isNotEmpty()) {
                ExportBar(
                    tracks, settings.targetDb, export,
                    onExport = { showExport = true },
                    onCancel = { repo.cancelExport() },
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
                )
            }
        }
    }
    if (showExport) ExportSheet(repo, onDismiss = { showExport = false })
}

@Composable
private fun Header(tracks: List<Track>, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Otobako", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            val ready = tracks.count { it.isReady }
            val busy = tracks.count { it.isBusy }
            Text(
                when {
                    tracks.isEmpty() -> "保留中の曲はありません"
                    busy > 0 -> "保留中 ${tracks.size}曲 · ダウンロード中 ${busy}曲"
                    else -> "保留中 ${ready}曲"
                },
                color = C.Sub, fontSize = 13.sp,
            )
        }
        IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "設定", tint = C.Sub) }
    }
}

@Composable
private fun PasteBar(repo: Repo, bitrate: Int) {
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    var text by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }

    fun submit() {
        if (text.isBlank()) return
        if (repo.addFromText(text) > 0) text = ""
        focus.clearFocus()
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(52.dp)
                .background(C.Surface, RoundedCornerShape(26.dp))
                .border(1.dp, C.Line, RoundedCornerShape(26.dp))
                .padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Link, null, tint = C.Faint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) Text("動画のリンクを貼り付け", color = C.Faint, fontSize = 15.sp)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = C.Text, fontSize = 15.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(C.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (text.isEmpty()) {
                // ワンタップで「貼り付け → 追加」
                FilledIconButton(
                    onClick = {
                        val cm = ctx.getSystemService(ClipboardManager::class.java)
                        val s = cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                        if (s.isBlank()) repo.say("クリップボードが空です") else repo.addFromText(s)
                    },
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = C.Accent, contentColor = C.Bg),
                ) { Icon(Icons.Rounded.ContentPaste, "貼り付け", Modifier.size(20.dp)) }
            } else {
                IconButton(onClick = { text = "" }) { Icon(Icons.Rounded.Close, "消す", tint = C.Sub) }
                FilledIconButton(
                    onClick = { submit() },
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = C.Accent, contentColor = C.Bg),
                ) { Icon(Icons.Rounded.Add, "追加", Modifier.size(22.dp)) }
            }
        }
        Spacer(Modifier.width(8.dp))
        // 端末内の mp3 (編集して上書き) や動画 (mp3 に変換) を選ぶ (複数可)
        val pickMp3 = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            repo.addLocal(uris)
        }
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(C.Surface)
                .border(1.dp, C.Line, RoundedCornerShape(18.dp))
                .clickable { pickMp3.launch(arrayOf("audio/mpeg", "audio/mp3", "video/*")) },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.FolderOpen, "端末内の mp3・動画を追加", tint = C.Text, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.width(8.dp))
        // 新しく追加する曲のビットレート
        Box {
            // 数字と kbps を縦に中央揃えし、矢印はその右に縦中央で置く
            Row(
                Modifier
                    .height(52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(C.Surface)
                    .border(1.dp, C.Line, RoundedCornerShape(18.dp))
                    .clickable { menu = true }
                    .padding(start = 14.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$bitrate", color = C.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 18.sp)
                    Text("kbps", color = C.Faint, fontSize = 10.sp, lineHeight = 12.sp)
                }
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.UnfoldMore, null, tint = C.Faint, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                BITRATES.forEach { b ->
                    DropdownMenuItem(
                        text = { Text("$b kbps", fontWeight = if (b == bitrate) FontWeight.Bold else FontWeight.Normal) },
                        onClick = { repo.settings.edit { it.copy(bitrate = b) }; menu = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun ClipBanner(url: String, onAdd: () -> Unit, onDismiss: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(C.Accent.copy(alpha = 0.12f))
            .clickable(onClick = onAdd)
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.ContentPaste, null, tint = C.Accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("コピーしたリンクを追加", color = C.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(url, color = C.Sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = onAdd) { Text("追加", color = C.Accent, fontWeight = FontWeight.Bold) }
        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "閉じる", tint = C.Sub, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // アプリのロゴ (ランチャーアイコンと同じ絵)
        Box(Modifier.size(96.dp).clip(RoundedCornerShape(28.dp))) {
            Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize())
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize().scale(1.35f))
        }
        Spacer(Modifier.height(20.dp))
        Text("曲を追加しましょう", color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "動画サイトの曲も、端末内の mp3 や動画も、ここにいったん保留されます。\nタグ・アートワーク・長さを整えたら、音量をそろえてまとめて保存できます。",
            color = C.Sub, fontSize = 13.sp, lineHeight = 20.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        // 追加のしかた 3 通り
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HowTo(Icons.Rounded.ContentPaste, "リンクを貼り付け", "YouTube・ニコニコ動画・SoundCloud・X・TikTok")
            HowTo(Icons.Rounded.FolderOpen, "端末内の mp3・動画を選ぶ", "mp3 は編集して上書き、mp4 などの動画は mp3 に変換")
            HowTo(Icons.Rounded.Share, "各アプリの「共有」から", "Otobako を選ぶとそのまま追加")
        }
    }
}

@Composable
private fun HowTo(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Surface).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).background(C.Accent.copy(alpha = 0.14f), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = C.Accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(sub, color = C.Sub, fontSize = 11.sp)
        }
    }
}

@Composable
private fun TrackCard(repo: Repo, t: Track, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val accent = accentColor(t.accent)
    var menu by remember { mutableStateOf(false) }
    var descOpen by rememberSaveable(t.id) { mutableStateOf(false) }
    val dir = repo.dir(t.id)

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(C.Surface)
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.20f), accent.copy(alpha = 0.04f), Color.Transparent)))
            .clickable(onClick = onOpen)
            .padding(12.dp)
            .animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Artwork(t, dir, Modifier.height(72.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                // 「動画のタイトル アカウント名」
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = C.Text, fontWeight = FontWeight.SemiBold)) {
                            append(t.sourceTitle.ifEmpty { t.url })
                        }
                        if (t.uploader.isNotEmpty()) {
                            append("  ")
                            withStyle(SpanStyle(color = C.Sub, fontWeight = FontWeight.Normal)) { append(t.uploader) }
                        }
                    },
                    fontSize = 14.sp, lineHeight = 19.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                StatusLine(repo, t, accent)
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.MoreVert, "メニュー", tint = C.Sub)
                }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    if (t.isReady) DropdownMenuItem(
                        text = { Text("編集") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { menu = false; onOpen() },
                    )
                    if (t.status == Status.FAILED) DropdownMenuItem(
                        text = { Text("再試行") }, leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                        onClick = { menu = false; repo.retry(t.id) },
                    )
                    if (!t.isLocal && !t.isVideoImport) DropdownMenuItem(
                        text = { Text("元のページを開く") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
                        onClick = {
                            menu = false
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(t.webpageUrl.ifEmpty { t.url }))) }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("削除", color = C.Danger) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = C.Danger) },
                        onClick = { menu = false; repo.remove(t.id) },
                    )
                }
            }
        }

        if (t.description.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            val rot by animateFloatAsState(if (descOpen) 180f else 0f, label = "rot")
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { descOpen = !descOpen }
                    .padding(vertical = 4.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("概要欄", color = C.Sub, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Icon(
                    Icons.Rounded.ExpandMore, null, tint = C.Sub,
                    modifier = Modifier.size(18.dp).rotate(rot),
                )
                if (!descOpen) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        t.description.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
                        color = C.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (descOpen) {
                SelectionContainer {
                    Text(
                        t.description.trim(), color = C.Sub, fontSize = 12.sp, lineHeight = 18.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .background(C.Bg.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                    )
                }
            }
        }
    }
}


@Composable
fun Artwork(t: Track, dir: File, modifier: Modifier) {
    val art = if (t.hasArt) File(dir, "art.jpg") else File(dir, "thumb.jpg")
    val img = rememberImage(art.takeIf { t.hasArt || t.hasThumb }, if (t.hasArt) t.artVersion else t.thumbW, 360)
    val ratio = if (t.hasArt) 1f else 16f / 9f
    Box(
        modifier
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(12.dp))
            .background(C.Surface3),
        contentAlignment = Alignment.Center,
    ) {
        if (img != null) Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(Icons.Rounded.MusicNote, null, tint = C.Faint)
        if (t.isReady && t.duration > 0) {
            Text(
                formatTime(t.outLength),
                color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun StatusLine(repo: Repo, t: Track, accent: Color) {
    when (t.status) {
        Status.QUEUED, Status.DOWNLOADING, Status.PROCESSING -> Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.stage.ifEmpty { "待機中" }, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                if (t.status == Status.DOWNLOADING && t.progress > 0f) {
                    Spacer(Modifier.width(6.dp))
                    Text("${(t.progress * 100).toInt()}%", color = C.Sub, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            if (t.status == Status.DOWNLOADING && t.progress > 0f) {
                LinearProgressIndicator(
                    progress = { t.progress }, color = accent, trackColor = C.Surface3,
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    drawStopIndicator = {},
                )
            } else {
                LinearProgressIndicator(
                    color = accent, trackColor = C.Surface3,
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                )
            }
        }
        Status.FAILED -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = C.Danger, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text(t.error ?: "失敗しました", color = C.Danger, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { repo.retry(t.id) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("再試行", color = C.Text, fontSize = 12.sp)
            }
        }
        Status.READY -> Column {
            if (t.edited) {
                Text(
                    "♪ ${t.outTitle}" + (t.tags.artist.takeIf { it.isNotBlank() }?.let { " – $it" } ?: ""),
                    color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Pill(t.site)
                Pill(if (t.bitrate == 0) "元の音質" else "${t.bitrate}k")
                if (t.isTrimmed || t.fadeIn > 0 || t.fadeOut > 0) Pill("カット済", color = C.Text)
                if (!t.hasArt && !t.isLocal) Pill("未トリミング", color = C.Warn, bg = C.Warn.copy(alpha = 0.12f))
                if (!t.edited) Pill("タグ未編集", color = C.Warn, bg = C.Warn.copy(alpha = 0.12f))
            }
            t.exportError?.let {
                Spacer(Modifier.height(4.dp))
                Text("書き出し失敗: $it", color = C.Danger, fontSize = 11.sp, maxLines = 2)
            }
        }
    }
}

@Composable
private fun ExportBar(
    tracks: List<Track>,
    targetDb: Float,
    export: io.github.motimotinyanko.otobako.data.ExportState?,
    onExport: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier,
) {
    val ready = tracks.count { it.isReady }
    Box(
        modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(C.Surface2)
            .border(1.dp, C.Line, RoundedCornerShape(26.dp))
            .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        if (export != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("書き出し中 ${export.index + 1} / ${export.total}", color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("${export.stage} · ${export.name}", color = C.Sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (export.index + export.progress) / export.total },
                        color = C.Accent, trackColor = C.Surface3,
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                        drawStopIndicator = {},
                    )
                }
                TextButton(onClick = onCancel) { Text("中止", color = C.Sub) }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("まとめて書き出し", color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("${ready}曲 · 目標 ${Exporter.dbLabel(targetDb)}", color = C.Sub, fontSize = 12.sp)
                }
                Button(
                    onClick = onExport,
                    enabled = ready > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = C.Accent, contentColor = C.Bg),
                    modifier = Modifier.height(44.dp),
                ) { Text("書き出す", fontWeight = FontWeight.Bold) }
            }
        }
    }
}
