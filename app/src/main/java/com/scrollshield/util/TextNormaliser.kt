package com.scrollshield.util

@Suppress("RegExpRedundantEscape")
object TextNormaliser {

    // Emoji ranges: combining enclosing keycap, variation selectors, misc symbols,
    // dingbats, supplementary surrogate pairs for emoji blocks
    private val EMOJI_RE = Regex(
        "\u20E3|\uFE0F|[\uFE00-\uFE0E]|[\u2600-\u26FF]|[\u2700-\u27BF]" +
        "|\uD83C[\uDF00-\uDFFF]|\uD83D[\uDC00-\uDEFF]|\uD83E[\uDD00-\uDDFF]|\u200D"
    )
    private val WHITESPACE_RE = Regex("""\s+""")
    private val URL_RE        = Regex("""https?://\S+|ftp://\S+""")
    private val MENTION_RE    = Regex("""@\w+""")

    fun normalise(text: String): String {
        var s = text
        s = s.lowercase()                          // Rule 1: lowercase
        s = EMOJI_RE.replace(s, "")               // Rule 2: strip emoji
        s = WHITESPACE_RE.replace(s, " ")         // Rule 3: collapse whitespace
        s = URL_RE.replace(s, "")                 // Rule 4: remove URLs
        s = MENTION_RE.replace(s, "")             // Rule 5: remove @mentions
        return WHITESPACE_RE.replace(s, " ").trim() // Final cleanup: collapse again + trim
    }
}
