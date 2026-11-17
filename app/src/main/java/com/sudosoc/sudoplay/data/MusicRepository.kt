package com.sudosoc.sudoplay.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media as M

class MusicRepository(private val ctx: Context) {

    fun loadSongs(): List<Song> {
        val collection = if (Build.VERSION.SDK_INT >= 29)
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            M._ID, M.TITLE, M.ARTIST, M.ALBUM, M.ALBUM_ID, M.DURATION, M.DATE_ADDED, M.TRACK,
        )
        val out = ArrayList<Song>()
        ctx.contentResolver.query(
            collection, projection,
            "${M.IS_MUSIC} != 0 AND ${M.DURATION} >= 20000",
            null, "${M.TITLE} COLLATE NOCASE ASC",
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(M._ID)
            val iTitle = c.getColumnIndexOrThrow(M.TITLE)
            val iArtist = c.getColumnIndexOrThrow(M.ARTIST)
            val iAlbum = c.getColumnIndexOrThrow(M.ALBUM)
            val iAlbumId = c.getColumnIndexOrThrow(M.ALBUM_ID)
            val iDur = c.getColumnIndexOrThrow(M.DURATION)
            val iAdded = c.getColumnIndexOrThrow(M.DATE_ADDED)
            val iTrack = c.getColumnIndexOrThrow(M.TRACK)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                out += Song(
                    id = id,
                    title = c.getString(iTitle)?.takeIf { it.isNotBlank() } ?: "Unknown title",
                    artist = clean(c.getString(iArtist), "Unknown artist"),
                    album = clean(c.getString(iAlbum), "Unknown album"),
                    albumId = c.getLong(iAlbumId),
                    durationMs = c.getLong(iDur),
                    uri = ContentUris.withAppendedId(collection, id),
                    dateAdded = c.getLong(iAdded),
                    track = c.getInt(iTrack),
                )
            }
        }
        return out
    }

    private fun clean(s: String?, fallback: String): String =
        if (s.isNullOrBlank() || s == MediaStore.UNKNOWN_STRING) fallback else s
}
