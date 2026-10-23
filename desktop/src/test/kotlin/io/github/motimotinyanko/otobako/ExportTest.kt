package io.github.motimotinyanko.otobako

import io.github.motimotinyanko.otobako.data.AppSettings
import io.github.motimotinyanko.otobako.data.CropRect
import io.github.motimotinyanko.otobako.data.Tags
import io.github.motimotinyanko.otobako.data.Track
import io.github.motimotinyanko.otobako.engine.Engine
import io.github.motimotinyanko.otobako.engine.Exporter
import io.github.motimotinyanko.otobako.engine.LocalMp3
import io.github.motimotinyanko.otobako.engine.Thumbs
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 同梱の ffmpeg で実際に書き出して、ffprobe で結果を確かめる (Windows 上で実行) */
class ExportTest {
    private val work = File(System.getProperty("java.io.tmpdir"), "otobako-test").apply { deleteRecursively(); mkdirs() }

    private fun ff(vararg a: String) = runBlocking { Engine.ffmpeg(listOf("-hide_banner", "-loglevel", "error", "-y") + a) }.also { assertTrue(it.ok, it.tail()) }

    private fun probe(f: File): JSONObject = runBlocking {
        JSONObject(Engine.ffprobe(listOf("-v", "quiet", "-print_format", "json", "-show_format", "-show_streams", f.path)).log.joinToString("\n"))
    }

    @Test
    fun downloadedTrack() = runBlocking {
        val dir = File(work, "dl").apply { mkdirs() }
        ff("-f", "lavfi", "-i", "sine=frequency=440:duration=20,volume=-20dB", "-ac", "2", "-c:a", "aac", File(dir, "audio.m4a").path)
        ff("-f", "lavfi", "-i", "testsrc=size=1280x720", "-frames:v", "1", File(dir, "thumb.jpg").path)
        Thumbs.crop(File(dir, "thumb.jpg"), CropRect(280, 0, 720), File(dir, "art.jpg"))
        val out = File(work, "out").apply { mkdirs() }
        val t = Track(
            id = "t1", url = "x", addedAt = 0, audioFile = "audio.m4a", duration = 20.0, thumbW = 1280, thumbH = 720,
            artVersion = 1, crop = CropRect(280, 0, 720), bitrate = 192, edited = true,
            tags = Tags(title = "テスト曲", artist = "テスト歌手", album = "アルバム", year = "2026", track = "3"),
            trimStart = 2.0, trimEnd = 12.0, fadeIn = 1.0, fadeOut = 2.0,
        )
        val msg = Exporter.export(t, dir, AppSettings(outputDir = out.path, targetDb = 89f)) { _, _ -> }
        println("downloaded: $msg")
        val f = File(out, "テスト曲 - テスト歌手.mp3")
        assertTrue(f.exists(), "出力ファイルが無い: ${out.list()?.toList()}")
        val p = probe(f)
        val fmt = p.getJSONObject("format")
        val tags = fmt.getJSONObject("tags")
        println("tags=$tags duration=${fmt.getString("duration")}")
        assertEquals("テスト曲", tags.getString("title"))
        assertEquals("テスト歌手", tags.getString("artist"))
        assertEquals(10.0, fmt.getString("duration").toDouble(), 0.15)
        val streams = p.getJSONArray("streams")
        val video = (0 until streams.length()).map { streams.getJSONObject(it) }.firstOrNull { it.getString("codec_type") == "video" }
        assertTrue(video != null && video.getInt("width") == 720 && video.getInt("height") == 720, "アートワーク 720x720 が無い")
        // 89dB 目標で -20dB の正弦波は大きく持ち上がるはず
        val vol = runBlocking { Engine.ffmpeg(listOf("-hide_banner", "-i", f.path, "-af", "volumedetect", "-f", "null", "-")) }
        println(vol.log.filter { it.contains("mean_volume") || it.contains("max_volume") })
    }

    @Test
    fun localMp3Lossless() = runBlocking {
        val lib = File(work, "library").apply { mkdirs() }
        val out = File(work, "out2").apply { mkdirs() }
        val src = File(lib, "元の曲.mp3")
        ff("-f", "lavfi", "-i", "sine=frequency=330:duration=8,volume=-12dB", "-ac", "2", "-ar", "44100", "-c:a", "libmp3lame", "-b:a", "192k", "-metadata", "title=古いタイトル", src.path)
        val dir = File(work, "local").apply { mkdirs() }
        val audio = File(dir, "audio.mp3")
        LocalMp3.copyIn(src, audio)
        val info = LocalMp3.probe(audio)
        println("probe: $info")
        assertEquals("古いタイトル", info.tags.title)
        val t = Track(
            id = "t2", url = src.path, addedAt = 0, audioFile = "audio.mp3", duration = info.duration, sourceAbr = info.bitrate,
            localUri = src.path, localName = src.name, bitrate = 0, edited = true, tags = info.tags.copy(title = "新しいタイトル", artist = "歌手"),
        )
        val before = src.length()
        val msg = Exporter.export(t, dir, AppSettings(outputDir = out.path, targetDb = 89f)) { _, _ -> }
        println("local: $msg")
        assertEquals("新しいタイトル", probe(src).getJSONObject("format").getJSONObject("tags").getString("title"))
        // PC の mp3 は元のファイルを上書きするだけで、保存先フォルダには何も増えない
        assertTrue(out.list().isNullOrEmpty(), "保存先にファイルが追加された: ${out.list()?.toList()}")
        assertEquals(listOf("元の曲.mp3"), lib.list()?.toList(), "元のフォルダに余計なファイルがある")
        val a = probe(src).getJSONArray("streams").getJSONObject(0)
        assertEquals("mp3", a.getString("codec_name"))
        assertEquals("192000", a.getString("bit_rate"))
        println("size before=$before after=${src.length()}")
        // 保存先フォルダが未設定でも上書きできる
        val t3 = t.copy(id = "t3", tags = t.tags.copy(title = "未設定でも上書き"))
        val msg3 = Exporter.export(t3, dir, AppSettings(outputDir = null)) { _, _ -> }
        println("no-output: $msg3")
        assertEquals("未設定でも上書き", probe(src).getJSONObject("format").getJSONObject("tags").getString("title"))
    }
}
