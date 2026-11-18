package com.sudosoc.sudoplay.data

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val uri: Uri,
    val dateAdded: Long,
    val track: Int,
    val folder: String = "",
    val path: String = "",
    /** Online items only: where the cover comes from, and a flag so local-only features can skip them. */
    val artUrl: String? = null,
    val online: Boolean = false,
)

data class Playlist(val id: String, val name: String, val songIds: List<Long>)

const val FAV_ID = "fav"

/** Auto-generated lists driven by play statistics / metadata. */
enum class Smart(val title: String) {
    Recent("Recently played"),
    Most("Most played"),
    Added("Recently added"),
    Unplayed("Never played"),
    Mix("Mix for you"),
}
