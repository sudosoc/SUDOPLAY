package com.sudosoc.sudoplay.online

/** A title reduced to its letters and digits, without "(Official Video)", "[Lyrics]", "ft. …" and the like — so the same song matches across sources. */
object TitleKey {
    private val brackets = Regex("""\(.*?\)|\[.*?]|\{.*?}""")
    private val feat = Regex("""\b(feat|ft)\b.*$""")
    private val noise = Regex("""\b(official|video|audio|lyrics?|lyrical|hd|hq|4k|mv|clip|remaster(ed)?|explicit|visualizer)\b""")
    fun of(s: String) = s.lowercase().replace(brackets, " ").replace(feat, " ").replace(noise, " ").filter { it.isLetterOrDigit() }
}
