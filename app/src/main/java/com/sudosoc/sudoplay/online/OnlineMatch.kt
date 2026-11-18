package com.sudosoc.sudoplay.online

/** Finds the same song on another source: used when YouTube refuses a stream, so "play it anyway" still works. */
object OnlineMatch {
    private val junk = Regex("""\(.*?\)|\[.*?]|\b(official|video|audio|lyrics?|lyric|hd|hq|4k|music|clip|mv|visualizer|remastered|feat|ft)\b""", RegexOption.IGNORE_CASE)

    fun tokens(s: String): Set<String> =
        junk.replace(s, " ").lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 1 }.toSet()

    class Scored(val item: OnlineItem, val score: Double)

    /** Best candidate for (title, artist, duration), or null when nothing is close enough. */
    fun best(title: String, artist: String, durationSec: Long, candidates: List<OnlineItem>): OnlineItem? {
        val tTitle = tokens(title)
        if (tTitle.isEmpty()) return null
        val tAll = tTitle + tokens(artist)
        return candidates.asSequence().filter { it.collection == null && it.url.isNotBlank() }.mapNotNull { c ->
            val cTitle = tokens(c.title); val cAll = cTitle + tokens(c.subtitle)
            val titleCov = tTitle.count { it in cAll }.toDouble() / tTitle.size
            val allCov = tAll.count { it in cAll }.toDouble() / tAll.size
            if (titleCov < 0.75 || allCov < 0.6) return@mapNotNull null
            if (durationSec > 0 && c.durationSec > 0) {
                val diff = kotlin.math.abs(durationSec - c.durationSec)
                if (diff > maxOf(15.0, durationSec * 0.12)) return@mapNotNull null
            }
            // prefer a close length and a candidate that is not much longer than the target (mixes, 1-hour loops)
            val extra = (cTitle.size - tTitle.size).coerceAtLeast(0) * 0.03
            Scored(c, titleCov + allCov - extra)
        }.maxByOrNull { it.score }?.item
    }
}
