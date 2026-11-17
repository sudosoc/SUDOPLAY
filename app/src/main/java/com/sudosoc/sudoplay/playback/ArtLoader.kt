package com.sudosoc.sudoplay.playback

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size

/** Loads embedded/album artwork for a song's content Uri, with a small in-memory cache. */
object ArtLoader {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val missing = HashSet<String>()

    fun load(ctx: Context, songUri: Uri, size: Int): Bitmap? {
        val key = "${ContentUris.parseId(songUri)}:$size"
        cache.get(key)?.let { return it }
        synchronized(missing) { if (key in missing) return null }

        var bmp: Bitmap? = null
        if (Build.VERSION.SDK_INT >= 29) {
            bmp = runCatching { ctx.contentResolver.loadThumbnail(songUri, Size(size, size), null) }.getOrNull()
        }
        if (bmp == null) {
            bmp = runCatching {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(ctx, songUri)
                    r.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                } finally { r.release() }
            }.getOrNull()
        }
        if (bmp != null) cache.put(key, bmp) else synchronized(missing) { missing.add(key) }
        return bmp
    }
}
