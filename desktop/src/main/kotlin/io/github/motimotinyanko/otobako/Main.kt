package io.github.motimotinyanko.otobako

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.motimotinyanko.otobako.data.Repo
import kotlinx.coroutines.flow.first
import io.github.motimotinyanko.otobako.engine.Engine
import io.github.motimotinyanko.otobako.ui.AppTheme
import io.github.motimotinyanko.otobako.ui.C
import io.github.motimotinyanko.otobako.ui.CropPane
import io.github.motimotinyanko.otobako.ui.Desk
import io.github.motimotinyanko.otobako.ui.EditPane
import io.github.motimotinyanko.otobako.ui.HomePane
import io.github.motimotinyanko.otobako.ui.SettingsPane
import io.github.motimotinyanko.otobako.ui.windowsDarkMode
import java.awt.Dimension
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileLock
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

/** 右側に表示するもの */
private sealed interface Right {
    data object None : Right
    data class Edit(val id: String) : Right
    data class Crop(val id: String) : Right
    data object Settings : Right
}

private val SUPPORTED = listOf("youtube.com", "youtu.be", "nicovideo.jp", "nico.ms", "soundcloud.com", "twitter.com", "x.com")

/** コマンドライン引数 / 2 回目の起動で渡されたもの: ファイル・フォルダは mp3 として、それ以外はリンクとして追加 */
private fun Repo.handleArgs(args: List<String>) {
    val files = args.map(::File).filter { it.exists() }
    if (files.isNotEmpty()) addLocal(files)
    val text = args.filter { !File(it).exists() }.joinToString("\n")
    if (text.isNotBlank()) addFromText(text)
}

/** ドラッグ & ドロップ / Ctrl+V の中身を追加 */
private fun Repo.addTransferable(t: Transferable): Boolean {
    if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
        @Suppress("UNCHECKED_CAST")
        val files = t.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
        addLocal(files)
        return true
    }
    if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
        val s = t.getTransferData(DataFlavor.stringFlavor) as String
        if (s.isNotBlank()) { addFromText(s); return true }
    }
    return false
}

fun main(args: Array<String>) {
    if (!SingleInstance.acquire(args.toList())) return
    C.isDark = windowsDarkMode()
    val repo = Repo()
    repo.handleArgs(args.toList())

    application {
        val state = rememberWindowState(width = 1320.dp, height = 880.dp, position = WindowPosition(Alignment.Center))
        var confirmExit by remember { mutableStateOf(false) }
        var clip by remember { mutableStateOf<String?>(null) }
        var dismissedClip by remember { mutableStateOf<String?>(null) }
        var right by remember { mutableStateOf<Right>(Right.None) }
        // 動作確認用: OTOBAKO_DEBUG_OPEN=edit|crop|settings で起動直後にその画面を開く (通常は未設定)
        LaunchedEffect(Unit) {
            val mode = System.getenv("OTOBAKO_DEBUG_OPEN") ?: return@LaunchedEffect
            if (mode == "settings") { right = Right.Settings; return@LaunchedEffect }
            val t = repo.tracks.first { list -> list.any { it.isReady } }.first { it.isReady }
            right = if (mode == "crop") Right.Crop(t.id) else Right.Edit(t.id)
        }

        fun quit() { repo.shutdown(); exitApplication() }

        Window(
            onCloseRequest = { if (repo.isBusyNow) confirmExit = true else quit() },
            title = "Otobako",
            icon = painterResource("logo.png"),
            state = state,
            onKeyEvent = { e ->
                if (e.type != KeyEventType.KeyDown) return@Window false
                when {
                    // 入力欄が受け取らなかった Ctrl+V = クリップボードのリンク / エクスプローラーでコピーした mp3 を追加
                    e.isCtrlPressed && e.key == Key.V -> {
                        runCatching { java.awt.Toolkit.getDefaultToolkit().systemClipboard.getContents(null)?.let(repo::addTransferable) }
                        true
                    }
                    e.key == Key.Escape -> {
                        right = when (val r = right) {
                            is Right.Crop -> Right.Edit(r.id)
                            Right.Settings -> Right.None
                            else -> r
                        }
                        true
                    }
                    else -> false
                }
            },
        ) {
            DisposableEffect(Unit) {
                Desk.window = window
                window.minimumSize = Dimension(980, 640)
                SingleInstance.onArgs = { a ->
                    repo.handleArgs(a)
                    if (window.extendedState == java.awt.Frame.ICONIFIED) window.extendedState = java.awt.Frame.NORMAL
                    window.toFront()
                }
                // フォーカスが戻った時、コピーしてあるリンクを「追加しますか？」と出す
                val l = object : WindowAdapter() {
                    override fun windowGainedFocus(e: WindowEvent?) {
                        val text = Desk.clipboardText() ?: return
                        val url = Regex("""https?://\S+""").find(text)?.value ?: return
                        clip = if (url != dismissedClip && !repo.hasUrl(url) && SUPPORTED.any { url.contains(it, true) }) url else null
                    }
                }
                window.addWindowFocusListener(l)
                onDispose { window.removeWindowFocusListener(l) }
            }

            AppTheme {
                App(
                    repo, right, { right = it }, clip,
                    onAddClip = { repo.addFromText(it); dismissedClip = it; clip = null },
                    onDismissClip = { dismissedClip = clip; clip = null },
                )
                if (confirmExit) {
                    AlertDialog(
                        onDismissRequest = { confirmExit = false },
                        title = { Text("処理中です") },
                        text = { Text("ダウンロードか書き出しの途中です。終了すると中断されます (次回起動時にダウンロードは再開します)。") },
                        confirmButton = { TextButton(onClick = { quit() }) { Text("終了する", color = C.Danger) } },
                        dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("続ける") } },
                        containerColor = C.Surface2,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun App(
    repo: Repo,
    right: Right,
    setRight: (Right) -> Unit,
    clip: String?,
    onAddClip: (String) -> Unit,
    onDismissClip: () -> Unit,
) {
    val snack = remember { SnackbarHostState() }
    var dropping by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { repo.messages.collect { snack.showSnackbar(it) } }

    val dropTarget = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { dropping = true }
            override fun onExited(event: DragAndDropEvent) { dropping = false }
            override fun onEnded(event: DragAndDropEvent) { dropping = false }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                dropping = false
                return runCatching { repo.addTransferable(event.awtTransferable) }.getOrDefault(false)
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(C.Bg)
            .dragAndDropTarget(shouldStartDragAndDrop = { true }, target = dropTarget),
    ) {
        Row(Modifier.fillMaxSize()) {
            val selected = when (right) {
                is Right.Edit -> right.id
                is Right.Crop -> right.id
                else -> null
            }
            HomePane(
                repo, clip, onAddClip, onDismissClip,
                selectedId = selected,
                onOpen = { setRight(Right.Edit(it)) },
                onSettings = { setRight(if (right == Right.Settings) Right.None else Right.Settings) },
                modifier = Modifier.width(460.dp),
            )
            Box(Modifier.width(1.dp).fillMaxHeight().background(C.Line))
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (right) {
                    Right.None -> Placeholder()
                    is Right.Edit -> EditPane(
                        repo, right.id,
                        onBack = { setRight(Right.None) },
                        onSwitch = { setRight(Right.Edit(it)) },
                        onCrop = { setRight(Right.Crop(right.id)) },
                    )
                    is Right.Crop -> CropPane(repo, right.id, onClose = { setRight(Right.Edit(right.id)) })
                    Right.Settings -> SettingsPane(repo, onBack = { setRight(Right.None) })
                }
            }
        }
        if (dropping) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(C.Bg.copy(alpha = 0.75f))
                    .padding(24.dp)
                    .border(3.dp, C.Accent, RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("ここにドロップして追加\nmp3 ファイル・フォルダ・動画のリンク", color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, lineHeight = 30.sp)
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 96.dp).width(560.dp))
    }
}

@Composable
private fun Placeholder() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(88.dp).clip(RoundedCornerShape(26.dp)).alpha(0.5f)) {
            Image(painterResource("logo.png"), null, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(18.dp))
        Text("左の一覧から曲を選ぶと、ここで編集できます", color = C.Sub, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        Text("Ctrl+V でリンクを追加 · mp3 やフォルダはウィンドウへドラッグ & ドロップ", color = C.Faint, fontSize = 12.sp)
    }
}

/**
 * 二重起動の防止。2 つ目の Otobako を起動したら、引数 (リンクや mp3) を 1 つ目に渡して終わる
 * (エクスプローラーで mp3 を Otobako.exe にドラッグした時など)
 */
object SingleInstance {
    var onArgs: ((List<String>) -> Unit)? = null
    private var lock: FileLock? = null
    private var server: ServerSocket? = null

    fun acquire(args: List<String>): Boolean {
        val lockFile = File(Engine.home, "instance.lock")
        val portFile = File(Engine.home, "instance.port")
        val l = runCatching { RandomAccessFile(lockFile, "rw").channel.tryLock() }.getOrNull()
        if (l == null) {
            // もう起動している → 引数を渡して終了
            runCatching {
                Socket(InetAddress.getLoopbackAddress(), portFile.readText().trim().toInt()).use { s ->
                    s.getOutputStream().write(args.joinToString("\n").toByteArray(Charsets.UTF_8))
                }
            }
            return false
        }
        lock = l
        val srv = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
        server = srv
        portFile.writeText(srv.localPort.toString())
        thread(isDaemon = true, name = "otobako-instance") {
            while (true) {
                val text = runCatching { srv.accept().use { it.getInputStream().readBytes().toString(Charsets.UTF_8) } }.getOrNull() ?: continue
                val list = text.lines().filter { it.isNotBlank() }
                SwingUtilities.invokeLater { onArgs?.invoke(list) }
            }
        }
        return true
    }
}
