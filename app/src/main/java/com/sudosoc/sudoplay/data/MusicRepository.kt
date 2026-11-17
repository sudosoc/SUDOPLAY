package com.sudosoc.sudoplay.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media as M

class MusicRepository(private val ctx: Context) {

    fun loadSongs(minDurationSec: Int = 20): List<Song> {
        val collection = if (Build.VERSION.SDK_INT >= 29)
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        @Suppress("DEPRECATION")
        val projection = buildList {
            addAll(listOf(M._ID, M.TITLE, M.ARTIST, M.ALBUM, M.ALBUM_ID, M.DURATION, M.DATE_ADDED, M.TRACK, M.DATA))
            if (Build.VERSION.SDK_INT >= 29) add(M.RELATIVE_PATH)
        }.toTypedArray()
        val out = ArrayList<Song>()
        ctx.contentResolver.query(
            collection, projection,
            "${M.IS_MUSIC} != 0 AND ${M.DURATION} >= ${minDurationSec * 1000L}",
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
            @Suppress("DEPRECATION")
            val iData = c.getColumnIndexOrThrow(M.DATA)
            val iRel = if (Build.VERSION.SDK_INT >= 29) c.getColumnIndexOrThrow(M.RELATIVE_PATH) else -1
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val path = c.getString(iData).orEmpty()
                val folder = (if (iRel >= 0) c.getString(iRel) else java.io.File(path).parent).orEmpty().trimEnd('/')
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
                    folder = folder.ifEmpty { "Unknown folder" },
                    path = path,
                )
            }
        }
        return out
    }

    private fun clean(s: String?, fallback: String): String =
        if (s.isNullOrBlank() || s == MediaStore.UNKNOWN_STRING) fallback else s
}
