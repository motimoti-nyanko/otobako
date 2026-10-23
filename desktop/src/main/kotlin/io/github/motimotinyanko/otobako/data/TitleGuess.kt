package io.github.motimotinyanko.otobako.data

/** 動画タイトルから「曲名 / アーティスト」の候補を推測する (自動では適用しない) */
object TitleGuess {
    data class Guess(val title: String, val artist: String)

    private val brackets = Regex("""【[^】]*】|\[[^\]]*]|〔[^〕]*〕""")
    private val noise = Regex(
        """[(（]\s*(official|music\s*video|mv|pv|lyrics?|lyric\s*video|audio|video|full|ver\.?|version|歌ってみた|オリジナル|公式)[^)）]*[)）]""",
        RegexOption.IGNORE_CASE,
    )
    private val tailNoise = Regex(
        """\s*(official\s*(music\s*)?video|music\s*video|lyric\s*video|\bMV\b|\bPV\b|\bOfficial\b)\s*$""",
        RegexOption.IGNORE_CASE,
    )

    fun clean(s: String): String {
        var t = s.replace(brackets, " ").replace(noise, " ")
        repeat(2) { t = t.replace(tailNoise, "") }
        return t.replace(Regex("""\s+"""), " ").trim().trim('-', '／', '/', '|', ' ')
    }

    fun cleanUploader(s: String) = s.removeSuffix(" - Topic").removeSuffix("- Topic").trim()

    fun guess(sourceTitle: String, uploader: String): Guess? {
        val t = clean(sourceTitle)
        if (t.isEmpty()) return null
        val up = cleanUploader(uploader)

        // アーティスト「曲名」 / アーティスト『曲名』
        Regex("""^(.*?)\s*[「『](.+?)[」』]""").find(t)?.let { m ->
            val a = m.groupValues[1].trim().trim('-', '/', '／', ' ')
            return Guess(m.groupValues[2].trim(), a.ifEmpty { up })
        }
        // アーティスト - 曲名
        Regex("""^(.+?)\s+[-–—－]\s+(.+)$""").find(t)?.let { m ->
            return Guess(m.groupValues[2].trim(), m.groupValues[1].trim())
        }
        // 曲名 / アーティスト
        Regex("""^(.+?)\s*[/／]\s*(.+)$""").find(t)?.let { m ->
            return Guess(m.groupValues[1].trim(), m.groupValues[2].trim())
        }
        return if (t != sourceTitle.trim()) Guess(t, up) else null
    }
}
