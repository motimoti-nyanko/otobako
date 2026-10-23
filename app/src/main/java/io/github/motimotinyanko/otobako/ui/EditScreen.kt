package io.github.motimotinyanko.otobako.ui

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.github.motimotinyanko.otobako.data.Repo
import io.github.motimotinyanko.otobako.data.Tags
import io.github.motimotinyanko.otobako.data.TitleGuess
import io.github.motimotinyanko.otobako.data.Track
import io.github.motimotinyanko.otobako.data.formatTime
import io.github.motimotinyanko.otobako.engine.Waveform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun EditScreen(repo: Repo, id: String, onBack: () -> Unit, onSwitch: (String) -> Unit, onCrop: () -> Unit) {
    val tracks by repo.tracks.collectAsState()
    val t = tracks.find { it.id == id }
    if (t == null || !t.isReady) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val editable = tracks.filter { it.isReady }
    val idx = editable.indexOfFirst { it.id == id }
    val accent = accentColor(t.accent)
    val dir = repo.dir(id)

    Column(
        Modifier
            .fillMaxSize()
            .background(C.Bg)
            .background(Brush.verticalGradient(0f to accent.copy(alpha = 0.30f), 0.45f to C.Bg))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "戻る", tint = C.Text) }
            Text("編集", color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            // 次々に編集できるよう、前後の曲へ移動
            IconButton(onClick = { editable.getOrNull(idx - 1)?.let { onSwitch(it.id) } }, enabled = idx > 0) {
                Icon(Icons.Rounded.ChevronLeft, "前の曲", tint = if (idx > 0) C.Text else C.Faint)
            }
            Text("${idx + 1} / ${editable.size}", color = C.Sub, fontSize = 13.sp)
            IconButton(onClick = { editable.getOrNull(idx + 1)?.let { onSwitch(it.id) } }, enabled = idx < editable.size - 1) {
                Icon(Icons.Rounded.ChevronRight, "次の曲", tint = if (idx < editable.size - 1) C.Text else C.Faint)
            }
        }

        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            ArtHero(t, dir, accent, onCrop)
            SourceCard(t)
            TagEditor(repo, t, accent, multi = tracks.size > 1)
            Section("長さ") { TrimEditor(repo, t, accent) }
            Section("音質") {
                BitrateSelector(t.bitrate, keepOption = t.isLocal) { b -> repo.update(id) { it.copy(bitrate = b) } }
                Spacer(Modifier.height(8.dp))
                val src = buildString {
                    if (t.sourceCodec.isNotEmpty()) append(t.sourceCodec.substringBefore('.'))
                    if (t.sourceAbr > 0) append(" 約${t.sourceAbr.roundToInt()}kbps")
                }
                val note = when {
                    t.isLossless -> "再エンコードせず無劣化で保存します。音量は mp3gain と同じく 1.5dB 刻みで変わります。"
                    t.isLocal && t.bitrate == 0 -> "フェードを付けたため、元に近いビットレートで再エンコードします。"
                    t.isLocal -> "指定したビットレートで再エンコードします (音質がわずかに落ちます)。"
                    else -> "元より高くしても音質は上がりませんが、再変換による劣化は少なくなります。"
                }
                Text(
                    (if (src.isNotEmpty()) "元の音声: $src。" else "") + note,
                    color = C.Faint, fontSize = 11.sp, lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun ArtHero(t: Track, dir: File, accent: Color, onCrop: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val file = File(dir, if (t.hasArt) "art.jpg" else "thumb.jpg")
        val img = rememberImage(file.takeIf { t.hasArt || t.hasThumb }, if (t.hasArt) t.artVersion else t.thumbW, 1080)
        Box(
            Modifier
                .height(220.dp)
                .aspectRatio(if (t.hasArt || !t.hasThumb) 1f else t.thumbW.toFloat() / t.thumbH)
                .clip(RoundedCornerShape(18.dp))
                .background(C.Surface3)
                .clickable(enabled = t.hasThumb, onClick = onCrop),
            contentAlignment = Alignment.Center,
        ) {
            if (img != null) Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (!t.hasArt && t.hasThumb) {
                // 正方形になる範囲の目安
                Canvas(Modifier.fillMaxSize()) {
                    val s = min(size.width, size.height)
                    val l = (size.width - s) / 2
                    drawRect(Color.Black.copy(alpha = 0.5f), Offset.Zero, Size(l, size.height))
                    drawRect(Color.Black.copy(alpha = 0.5f), Offset(l + s, 0f), Size(l, size.height))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (t.hasThumb) {
            Button(
                onClick = onCrop,
                colors = ButtonDefaults.buttonColors(containerColor = if (t.hasArt) C.Surface3 else accent, contentColor = if (t.hasArt) C.Text else C.Bg),
                contentPadding = PaddingValues(horizontal = 18.dp),
            ) {
                Icon(Icons.Rounded.Crop, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (t.hasArt) "切り抜きを調整" else "正方形に切り抜く", fontWeight = FontWeight.SemiBold)
            }
            Text(
                if (t.hasArt) "アートワーク ${t.crop?.size ?: 0}×${t.crop?.size ?: 0}" else (if (t.isLocal) "埋め込み画像" else "サムネイル") + " ${t.thumbW}×${t.thumbH}",
                color = C.Faint, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            Text(if (t.isLocal) "埋め込み画像はありません" else "サムネイルを取得できませんでした", color = C.Faint, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SourceCard(t: Track) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text(t.sourceTitle, color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            if (t.isLocal) "端末内のファイル · ${t.localName.orEmpty()} · ${formatTime(t.duration)}"
            else "${t.uploader} · ${t.site} · ${formatTime(t.duration)}",
            color = C.Sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TagEditor(repo: Repo, t: Track, accent: Color, multi: Boolean) {
    var tags by remember(t.id) { mutableStateOf(t.tags) }
    fun set(n: Tags) {
        tags = n
        repo.update(t.id) { it.copy(tags = n, edited = true) }
    }
    val guess = remember(t.id) { TitleGuess.guess(t.sourceTitle, t.uploader) }
    // 推測を適用する直前の曲名・アーティスト (「元に戻す」用)
    var beforeGuess by remember(t.id) { mutableStateOf<Tags?>(null) }
    fun applyGuess(g: TitleGuess.Guess) {
        beforeGuess = tags
        set(tags.copy(title = g.title, artist = g.artist))
    }
    val guessApplied = guess != null && guess.title == tags.title && guess.artist == tags.artist

    Section("タグ") {
        val prev = beforeGuess
        if (guessApplied && prev != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(C.Surface2)
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.AutoFixHigh, null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("推測を適用しました", color = C.Sub, fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    set(tags.copy(title = prev.title, artist = prev.artist))
                    beforeGuess = null
                }) {
                    Icon(Icons.AutoMirrored.Rounded.Undo, null, tint = accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("元に戻す", color = accent, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(10.dp))
        } else if (guess != null && !guessApplied) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.12f))
                    .clickable { applyGuess(guess) }
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.AutoFixHigh, null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("タイトルから推測", color = C.Sub, fontSize = 11.sp)
                    Text("曲名「${guess.title}」", color = C.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("アーティスト「${guess.artist}」", color = C.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = { applyGuess(guess) }) {
                    Text("適用", color = accent, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        TagField("曲名", tags.title, accent) { set(tags.copy(title = it)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { set(tags.copy(title = tags.artist, artist = tags.title)) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Icon(Icons.Rounded.SwapVert, null, tint = C.Sub, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("曲名とアーティストを入れ替え", color = C.Sub, fontSize = 12.sp)
            }
        }
        TagField("アーティスト", tags.artist, accent) { set(tags.copy(artist = it)) }
        Spacer(Modifier.height(10.dp))
        val all: ((Tags) -> String, (Tags, String) -> Tags) -> (() -> Unit)? = { get, put ->
            if (multi) ({ repo.applyToAll(get, put, t.id) }) else null
        }
        TagField("アルバム", tags.album, accent, all({ it.album }, { x, v -> x.copy(album = v) })) { set(tags.copy(album = it)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(
                onClick = { set(tags.copy(album = tags.title)) },
                enabled = tags.title.isNotBlank() && tags.album != tags.title,
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Icon(Icons.Rounded.ContentCopy, null, tint = C.Sub, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("曲名をアルバムにコピー", color = C.Sub, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        TagField("アルバムアーティスト", tags.albumArtist, accent, all({ it.albumArtist }, { x, v -> x.copy(albumArtist = v) })) { set(tags.copy(albumArtist = it)) }
        Spacer(Modifier.height(10.dp))
        TagField("作曲者", tags.composer, accent, all({ it.composer }, { x, v -> x.copy(composer = v) })) { set(tags.copy(composer = it)) }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TagField("年", tags.year, accent, all({ it.year }, { x, v -> x.copy(year = v) }), KeyboardType.Number, Modifier.weight(1f)) { set(tags.copy(year = it)) }
            TagField("トラック番号", tags.track, accent, null, KeyboardType.Number, Modifier.weight(1f)) { set(tags.copy(track = it)) }
        }
        Spacer(Modifier.height(10.dp))
        TagField("ジャンル", tags.genre, accent, all({ it.genre }, { x, v -> x.copy(genre = v) })) { set(tags.copy(genre = it)) }
        Spacer(Modifier.height(10.dp))
        TagField("コメント", tags.comment, accent, singleLine = false) { set(tags.copy(comment = it)) }
        if (multi) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.DoneAll, null, tint = C.Faint, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("を押すと、その値を保留中の全曲にコピーします", color = C.Faint, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun TagField(
    label: String,
    value: String,
    accent: Color,
    applyAll: (() -> Unit)? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier.fillMaxWidth(),
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 4,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        trailingIcon = applyAll?.let { f -> { IconButton(onClick = f) { Icon(Icons.Rounded.DoneAll, "全曲に適用", tint = C.Faint) } } },
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent, unfocusedBorderColor = C.Line,
            focusedLabelColor = accent, unfocusedLabelColor = C.Sub,
            cursorColor = accent, focusedTextColor = C.Text, unfocusedTextColor = C.Text,
            focusedContainerColor = C.Bg.copy(alpha = 0.4f), unfocusedContainerColor = C.Bg.copy(alpha = 0.4f),
        ),
        modifier = modifier,
    )
}

// ---------------- 長さの編集 ----------------

private fun fmt1(v: Double) = String.format(Locale.US, "%.1f", v)

@Composable
private fun TrimEditor(repo: Repo, t: Track, accent: Color) {
    val ctx = LocalContext.current
    val dir = repo.dir(t.id)
    val peaks by produceState<FloatArray?>(null, t.id) { value = withContext(Dispatchers.IO) { Waveform.read(File(dir, "wave.bin")) } }

    var dur by remember(t.id) { mutableDoubleStateOf(t.duration) }
    var start by remember(t.id) { mutableDoubleStateOf(t.trimStart) }
    var end by remember(t.id) { mutableDoubleStateOf(t.endOrDuration) }
    var fin by remember(t.id) { mutableDoubleStateOf(t.fadeIn) }
    var fout by remember(t.id) { mutableDoubleStateOf(t.fadeOut) }
    var pos by remember(t.id) { mutableDoubleStateOf(t.trimStart) }
    var playing by remember(t.id) { mutableStateOf(false) }

    val player = remember(t.id) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(dir, t.audioFile!!))))
            prepare()
        }
    }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && dur <= 0 && player.duration > 0) {
                    dur = player.duration / 1000.0
                    if (end <= 0) end = dur
                }
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }

    fun commit() {
        repo.update(t.id) {
            it.copy(
                trimStart = if (start < 0.05) 0.0 else start,
                trimEnd = if (end >= dur - 0.05) 0.0 else end,
                fadeIn = fin, fadeOut = fout,
            )
        }
    }
    fun setStart(v: Double) { start = v.coerceIn(0.0, max(0.0, end - 1.0)) }
    fun setEnd(v: Double) { end = v.coerceIn(min(dur, start + 1.0), dur) }
    fun seek(v: Double) { pos = v.coerceIn(0.0, dur); player.seekTo((pos * 1000).toLong()) }
    fun playFrom(v: Double) { seek(v); player.play() }

    // 再生位置の追跡 + フェードのプレビュー + 終了位置で止める
    LaunchedEffect(playing) {
        while (playing) {
            pos = player.currentPosition / 1000.0
            var v = 1.0
            if (fin > 0 && pos - start < fin) v = min(v, max(0.0, (pos - start) / fin))
            if (fout > 0 && end - pos < fout) v = min(v, max(0.0, (end - pos) / fout))
            player.volume = v.toFloat()
            if (pos >= end) { player.pause(); seek(start) }
            delay(40)
        }
        player.volume = 1f
    }

    if (dur <= 0) {
        Text("曲の長さを読み込み中…", color = C.Sub, fontSize = 13.sp)
        return
    }

    WaveformBar(
        peaks, dur, start, end, fin, fout, pos, accent,
        onStart = ::setStart, onEnd = ::setEnd, onSeek = ::seek, onRelease = ::commit,
    )
    Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        Text("0:00", color = C.Faint, fontSize = 10.sp)
        Spacer(Modifier.weight(1f))
        Text(formatTime(pos, true), color = C.Text, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Text(formatTime(dur), color = C.Faint, fontSize = 10.sp)
    }
    Spacer(Modifier.height(10.dp))

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalIconButton(
            onClick = { if (playing) player.pause() else { if (pos < start || pos >= end - 0.05) seek(start); player.play() } },
            modifier = Modifier.size(52.dp),
        ) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "再生", Modifier.size(28.dp)) }
        Column(Modifier.weight(1f)) {
            Text("書き出す長さ ${formatTime(end - start, true)}", color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Row {
                TextButton(onClick = { playFrom(start) }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("頭から", color = accent, fontSize = 12.sp) }
                TextButton(onClick = { playFrom(max(start, end - 6.0)) }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("終わり6秒前から", color = accent, fontSize = 12.sp) }
            }
        }
        IconButton(onClick = { start = 0.0; end = dur; fin = 0.0; fout = 0.0; commit() }) {
            Icon(Icons.Rounded.RestartAlt, "リセット", tint = C.Sub)
        }
    }
    Spacer(Modifier.height(6.dp))
    PointRow("開始", start, accent, onNudge = { setStart(start + it); commit() }, onHere = { setStart(pos); commit() })
    Spacer(Modifier.height(6.dp))
    PointRow("終了", end, accent, onNudge = { setEnd(end + it); commit() }, onHere = { setEnd(pos); commit() })
    Spacer(Modifier.height(14.dp))
    FadeSlider("フェードイン", fin, accent, { fin = it }, ::commit)
    FadeSlider("フェードアウト", fout, accent, { fout = it }, ::commit)
}

@Composable
private fun PointRow(label: String, value: Double, accent: Color, onNudge: (Double) -> Unit, onHere: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Surface2).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = C.Sub, fontSize = 12.sp, modifier = Modifier.width(32.dp))
        Text(formatTime(value, true), color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(72.dp))
        Nudge("-1", { onNudge(-1.0) })
        Nudge("-0.1", { onNudge(-0.1) })
        Nudge("+0.1", { onNudge(0.1) })
        Nudge("+1", { onNudge(1.0) })
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onHere, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("今の位置", color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun Nudge(text: String, onClick: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 2.dp).clip(RoundedCornerShape(8.dp)).background(C.Surface3).clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 6.dp),
    ) { Text(text, color = C.Text, fontSize = 11.sp) }
}

@Composable
private fun FadeSlider(label: String, value: Double, accent: Color, onChange: (Double) -> Unit, onDone: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = C.Text, fontSize = 13.sp, modifier = Modifier.width(96.dp))
        Slider(
            value = value.toFloat(), onValueChange = { onChange((it * 2).roundToInt() / 2.0) }, onValueChangeFinished = onDone,
            valueRange = 0f..10f, steps = 19, modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = C.Surface3, activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
        )
        Text(if (value > 0) "${fmt1(value)}秒" else "なし", color = C.Sub, fontSize = 12.sp, modifier = Modifier.width(44.dp).padding(start = 6.dp))
    }
}

/** 波形 + 開始/終了の取っ手 + 再生位置。取っ手の近くを触るとその取っ手、それ以外は再生位置の移動 */
@Composable
private fun WaveformBar(
    peaks: FloatArray?,
    dur: Double,
    start: Double,
    end: Double,
    fin: Double,
    fout: Double,
    pos: Double,
    accent: Color,
    onStart: (Double) -> Unit,
    onEnd: (Double) -> Unit,
    onSeek: (Double) -> Unit,
    onRelease: () -> Unit,
) {
    val s by rememberUpdatedState(start)
    val e by rememberUpdatedState(end)
    val fStart by rememberUpdatedState(onStart)
    val fEnd by rememberUpdatedState(onEnd)
    val fSeek by rememberUpdatedState(onSeek)
    val fRelease by rememberUpdatedState(onRelease)

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(C.Bg.copy(alpha = 0.6f))
            .pointerInput(dur) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val w = size.width.toFloat()
                    fun at(x: Float) = (x / w * dur).coerceIn(0.0, dur)
                    val xs = (s / dur * w).toFloat()
                    val xe = (e / dur * w).toFloat()
                    val slop = 28.dp.toPx()
                    val ds = abs(down.position.x - xs)
                    val de = abs(down.position.x - xe)
                    val target = when {
                        ds < slop && ds <= de -> 1
                        de < slop -> 2
                        else -> 0
                    }
                    if (target == 0) fSeek(at(down.position.x))
                    down.consume()
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        val t = at(ch.position.x)
                        when (target) { 1 -> fStart(t); 2 -> fEnd(t); else -> fSeek(t) }
                        ch.consume()
                    }
                    if (target != 0) fRelease()
                }
            },
    ) {
        val w = size.width
        val h = size.height
        val xs = (start / dur * w).toFloat()
        val xe = (end / dur * w).toFloat()
        val barW = 2.dp.toPx()
        val gap = 1.dp.toPx()
        val cols = (w / (barW + gap)).toInt().coerceAtLeast(1)
        val mid = h / 2
        for (c in 0 until cols) {
            val x = c * (barW + gap)
            val v = if (peaks == null || peaks.isEmpty()) 0.08f else {
                val a = (c.toLong() * peaks.size / cols).toInt()
                val b = max(a + 1, ((c + 1).toLong() * peaks.size / cols).toInt()).coerceAtMost(peaks.size)
                var m = 0f
                for (i in a until b) m = max(m, peaks[i])
                m
            }
            val bh = max(2.dp.toPx(), v * h * 0.86f)
            val inside = x + barW / 2 in xs..xe
            drawRoundRect(
                color = if (inside) accent else C.Faint.copy(alpha = 0.5f),
                topLeft = Offset(x, mid - bh / 2), size = Size(barW, bh), cornerRadius = CornerRadius(barW / 2),
            )
        }
        // カットされる範囲を暗く
        drawRect(C.Bg.copy(alpha = 0.6f), Offset.Zero, Size(xs, h))
        drawRect(C.Bg.copy(alpha = 0.6f), Offset(xe, 0f), Size(w - xe, h))
        // フェードの形
        if (fin > 0 || fout > 0) {
            val xi = (xs + fin / dur * w).toFloat().coerceAtMost(xe)
            val xo = (xe - fout / dur * w).toFloat().coerceAtLeast(xs)
            val p = Path().apply {
                moveTo(xs, if (fin > 0) h - 2 else 2f)
                lineTo(xi, 2f); lineTo(xo, 2f)
                lineTo(xe, if (fout > 0) h - 2 else 2f)
            }
            drawPath(p, C.Text.copy(alpha = 0.55f), style = Stroke(1.5.dp.toPx()))
        }
        // 取っ手
        listOf(xs, xe).forEach { x ->
            drawRect(C.Text, Offset(x - 1.dp.toPx(), 0f), Size(2.dp.toPx(), h))
            drawCircle(C.Text, 6.dp.toPx(), Offset(x, 7.dp.toPx()))
            drawCircle(C.Text, 6.dp.toPx(), Offset(x, h - 7.dp.toPx()))
        }
        // 再生位置
        val xp = (pos / dur * w).toFloat()
        drawRect(C.Warn, Offset(xp - 0.75.dp.toPx(), 0f), Size(1.5.dp.toPx(), h))
    }
}

