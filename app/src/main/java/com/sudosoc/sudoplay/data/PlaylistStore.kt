package com.sudosoc.sudoplay.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Playlists persisted as a small JSON file. "Favorites" is just a playlist with a fixed id. */
class PlaylistStore(ctx: Context) {
    private val file = File(ctx.filesDir, "playlists.json")
    val items = mutableStateListOf<Playlist>()
    /** Called with a song id whenever it is added to a playlist (the view model uses it to remember online songs). */
    var onAdd: ((Long) -> Unit)? = null

    init {
        load()
        if (items.none { it.id == FAV_ID }) items.add(0, Playlist(FAV_ID, "Favorites", emptyList()))
    }

    fun get(id: String): Playlist? = items.firstOrNull { it.id == id }

    fun create(name: String): Playlist {
        val p = Playlist(UUID.randomUUID().toString(), name.trim().ifEmpty { "New playlist" }, emptyList())
        items.add(p); save(); return p
    }

    fun delete(id: String) {
        if (id == FAV_ID) return
        items.removeAll { it.id == id }; save()
    }

    fun rename(id: String, name: String) = update(id) { it.copy(name = name.trim().ifEmpty { it.name }) }

    /** Several at once, keeping order and skipping what is already there. */
    fun addSongs(id: String, songIds: List<Long>) = update(id) { p -> p.copy(songIds = p.songIds + songIds.filter { it !in p.songIds }.distinct()) }

    fun addSong(id: String, songId: Long) { onAdd?.invoke(songId); addSongRaw(id, songId) }

    private fun addSongRaw(id: String, songId: Long) = update(id) {
        if (songId in it.songIds) it else it.copy(songIds = it.songIds + songId)
    }

    fun removeSong(id: String, songId: Long) = update(id) { it.copy(songIds = it.songIds - songId) }

    fun isFav(songId: Long) = get(FAV_ID)?.songIds?.contains(songId) == true

    fun toggleFav(songId: Long) {
        if (isFav(songId)) removeSong(FAV_ID, songId) else addSong(FAV_ID, songId)
    }

    private fun update(id: String, f: (Playlist) -> Playlist) {
        val i = items.indexOfFirst { it.id == id }
        if (i >= 0) { items[i] = f(items[i]); save() }
    }

    private fun load() {
        if (!file.exists()) return
        runCatching {
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val ids = o.getJSONArray("songs")
                items.add(Playlist(o.getString("id"), o.getString("name"), List(ids.length()) { ids.getLong(it) }))
            }
        }
    }

    private fun save() {
        val arr = JSONArray()
        items.forEach { p ->
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("songs", JSONArray(p.songIds)))
        }
        runCatching { file.writeText(arr.toString()) }
    }
}
