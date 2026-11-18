package com.sudosoc.sudoplay.ui

/** Everything a gesture or a button slot can be told to do. */
object GestureActions {
    val all: List<Pair<String, String>> = listOf(
        "none" to "Nothing",
        "playpause" to "Play / pause",
        "next" to "Next song",
        "prev" to "Previous song",
        "back10" to "Back 10 seconds",
        "fwd10" to "Forward 10 seconds",
        "fav" to "Favorite / unfavorite",
        "shuffle" to "Shuffle on / off",
        "repeat" to "Cycle repeat mode",
        "speed" to "Speed: normal ↔ your last speed",
        "lyrics" to "Show / hide lyrics",
        "queue" to "Show / hide queue",
        "eq" to "Open the sound studio",
        "ab" to "A–B loop point",
        "cover" to "Change cover (on the artwork)",
        "carmode" to "Car mode",
        "clip" to "Make a video clip of this song",
        "bookmark" to "Bookmark this moment",
        "close" to "Close the player",
    )
    fun label(id: String) = all.firstOrNull { it.first == id }?.second ?: "Nothing"
}

/** Ids of the reorderable button groups, in their default order. */
object ButtonOrder {
    val top = listOf("lyrics", "queue")
    val title = listOf("fav", "add")
    val transport = listOf("shuffle", "prev", "play", "next", "repeat")
    val chips = listOf("speed", "eq", "sleep")

    val names = mapOf(
        "lyrics" to "Lyrics", "queue" to "Queue", "fav" to "Favorite", "add" to "Playlist",
        "shuffle" to "Shuffle", "prev" to "Previous", "play" to "Play", "next" to "Next", "repeat" to "Repeat",
        "speed" to "Speed", "eq" to "EQ", "sleep" to "Sleep",
    )

    /** Saved order, repaired so every known id appears exactly once. */
    fun parse(raw: String, all: List<String>): List<String> {
        val got = raw.split(",").filter { it in all }.distinct()
        return got + all.filter { it !in got }
    }
}
