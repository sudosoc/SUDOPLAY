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
)

data class Playlist(val id: String, val name: String, val songIds: List<Long>)

const val FAV_ID = "fav"
