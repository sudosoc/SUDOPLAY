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
import com.sudosoc.sudoplay.data.CoverStore

/** Loads artwork for a song's content Uri: user cover first, then album art / embedded picture. */
object ArtLoader {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val missing = HashSet<String>()

    fun clearCache() {
        cache.evictAll()
        synchronized(missing) { missing.clear() }
    }

    fun load(ctx: Context, songUri: Uri, size: Int): Bitmap? {
        val id = ContentUris.parseId(songUri)
        val key = "$id:$size"
        cache.get(key)?.let { return it }
        synchronized(missing) { if (key in missing) return null }

        var bmp: Bitmap? = null
        CoverStore.fileFor(ctx, id)?.let { f -> bmp = decodeFile(f.path, size) }
        if (bmp == null && Build.VERSION.SDK_INT >= 29) {
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
        val out = bmp
        if (out != null) cache.put(key, out) else synchronized(missing) { missing.add(key) }
        return out
    }

    private fun decodeFile(path: String, target: Int): Bitmap? = runCatching {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        var sample = 1
        while (o.outWidth / (sample * 2) >= target && o.outHeight / (sample * 2) >= target) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}
