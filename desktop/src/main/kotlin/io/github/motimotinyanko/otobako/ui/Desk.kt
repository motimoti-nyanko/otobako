package io.github.motimotinyanko.otobako.ui

import io.github.motimotinyanko.otobako.data.VIDEO_EXTS
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.net.URI
import javax.swing.JFileChooser
import javax.swing.UIManager

/** Windows の機能 (クリップボード・ファイル選択・ブラウザ・エクスプローラー) */
object Desk {
    /** ダイアログの親ウィンドウ (Main で設定) */
    var window: Frame? = null

    fun clipboardText(): String? = runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String
    }.getOrNull()

    /** mp3 と動画を複数選ぶ (Windows 標準のファイル選択画面) */
    fun pickMp3Files(): List<File> {
        val d = FileDialog(window, "mp3 または動画を選ぶ (複数選択できます)", FileDialog.LOAD)
        d.isMultipleMode = true
        d.file = (listOf("mp3") + VIDEO_EXTS).joinToString(";") { "*.$it" }
        d.setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase().let { it == "mp3" || it in VIDEO_EXTS } }
        d.isVisible = true
        return d.files.orEmpty().toList()
    }

    /** フォルダを選ぶ */
    fun pickFolder(title: String, current: String?): File? {
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        val c = JFileChooser(current?.let(::File)).apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
        }
        return if (c.showDialog(window, "このフォルダを選ぶ") == JFileChooser.APPROVE_OPTION) c.selectedFile else null
    }

    fun browse(url: String) {
        runCatching { Desktop.getDesktop().browse(URI(url)) }
    }

    /** エクスプローラーでファイルを選んだ状態で開く */
    fun reveal(file: File) {
        runCatching { ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start() }
    }

    fun openFolder(dir: File) {
        runCatching { Desktop.getDesktop().open(dir) }
    }
}
