package com.sudosoc.sudoplay.data

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.sudosoc.sudoplay.playback.ArtLoader
import java.io.File

/**
 * User-chosen covers. Stored as JPEG in filesDir/covers: `s<songId>.jpg` for a single song,
 * `a<albumId>.jpg` when applied to a whole album.
 */
object CoverStore {
    var version by mutableIntStateOf(0); private set
    private var dir: File? = null
    private val albumOf = HashMap<Long, Long>()

    private fun dir(ctx: Context): File = dir ?: File(ctx.applicationContext.filesDir, "covers").also { it.mkdirs(); dir = it }

    fun registerSongs(songs: List<Song>) {
        synchronized(albumOf) { albumOf.clear(); songs.forEach { albumOf[it.id] = it.albumId } }
    }

    fun fileFor(ctx: Context, songId: Long): File? {
        val d = dir(ctx)
        val s = File(d, "s$songId.jpg")
        if (s.exists()) return s
        val album = synchronized(albumOf) { albumOf[songId] } ?: return null
        val a = File(d, "a$album.jpg")
        return if (a.exists()) a else null
    }

    fun hasCustom(ctx: Context, song: Song) = fileFor(ctx, song.id) != null

    fun save(ctx: Context, song: Song, bmp: Bitmap, wholeAlbum: Boolean) {
        val f = File(dir(ctx), if (wholeAlbum) "a${song.albumId}.jpg" else "s${song.id}.jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        changed()
    }

    fun reset(ctx: Context, song: Song) {
        val d = dir(ctx)
        File(d, "s${song.id}.jpg").delete()
        File(d, "a${song.albumId}.jpg").delete()
        changed()
    }

    fun clearAll(ctx: Context) {
        dir(ctx).listFiles()?.forEach { it.delete() }
        changed()
    }

    fun count(ctx: Context) = dir(ctx).listFiles()?.size ?: 0

    private fun changed() { ArtLoader.clearCache(); version++ }
}
