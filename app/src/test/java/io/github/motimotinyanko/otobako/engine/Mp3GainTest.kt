package io.github.motimotinyanko.otobako.engine

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 実機の ffmpeg で作った mp3 (環境変数 GT_DIR の st.mp3 / mono.mp3) に Mp3Gain を掛けて
 * *_p2.mp3 (+3dB) / *_m3.mp3 (-4.5dB) を書き出す。音量の確認は実機の ffmpeg で行う
 */
class Mp3GainTest {
    @Test
    fun applyGain() {
        val dir = System.getenv("GT_DIR")?.let(::File)
        assumeTrue("GT_DIR が無いので省略", dir != null && dir.isDirectory)
        for (name in listOf("st", "mono")) {
            val src = File(dir, "$name.mp3")
            for ((suffix, steps) in listOf("p2" to 2, "m3" to -3)) {
                val out = File(dir, "${name}_$suffix.mp3")
                src.copyTo(out, overwrite = true)
                val frames = Mp3Gain.apply(out, steps)
                println("$name $suffix: $frames frames")
                assertTrue(frames > 100)
                assertTrue(out.length() == src.length())
            }
        }
    }
}
