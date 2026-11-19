package com.sudosoc.sudoplay.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * A song that lives in a playlist without being on the phone: where to find it (a page / stream url, or just "title + artist" to search for),
 * plus enough metadata to draw a row. [id] is what the playlist stores.
 */
class OnlineEntry(
    val id: Long, val kind: String, val title: String, val artist: String,
    val thumb: String?, val durationSec: Long, val url: String,
) {
    val needsSearch get() = url.isBlank()
    val query get() = "$title $artist".trim()
}

/**
 * Two small maps kept in one JSON file:
 *  - [entries]: online songs that are in playlists / favorites (so they can be listed and played later)
 *  - [local]: "this online song was downloaded as that MediaStore id" (so the playlist row flips from ONLINE to ON DEVICE by itself)
 */
class OnlineLibrary(ctx: Context) {
    private val file = File(ctx.filesDir, "onlinelib.json")
    private val entries = HashMap<Long, OnlineEntry>()
    private val local = HashMap<Long, Long>()
    /** Bumped on every change so Compose lists that depend on the maps redraw. */
    var version by mutableIntStateOf(0); private set

    init { load() }

    fun get(id: Long): OnlineEntry? { version; return entries[id] }
    fun localFor(onlineId: Long): Long? { version; return local[onlineId] }
    fun isDownloaded(localId: Long): Boolean { version; return local.containsValue(localId) }
    fun localIds(): Set<Long> { version; return local.values.toSet() }

    fun put(e: OnlineEntry) { entries[e.id] = e; save() }
    fun putAll(list: List<OnlineEntry>) { list.forEach { entries[it.id] = it }; save() }
    fun mapLocal(onlineId: Long, localId: Long) { if (localId > 0) { local[onlineId] = localId; save() } }
    /** The file is gone: every online id that pointed at it goes back to being "online only". */
    fun unmapLocal(localIds: Set<Long>) { if (local.values.removeAll(localIds)) save() }

    fun stub(e: OnlineEntry) = Song(
        id = e.id, title = e.title, artist = e.artist.ifBlank { "Online" }, album = "Online", albumId = 0, durationMs = e.durationSec * 1000,
        uri = Uri.parse(e.url.ifBlank { "https://search.invalid/" }), dateAdded = 0, track = 0, folder = "", path = "", artUrl = e.thumb, online = true,
    )

    private fun load() {
        if (!file.exists()) return
        runCatching {
            val o = JSONObject(file.readText())
            o.optJSONArray("e")?.let { a ->
                for (i in 0 until a.length()) {
                    val x = a.getJSONObject(i)
                    val e = OnlineEntry(x.getLong("id"), x.optString("k", "Link"), x.optString("t"), x.optString("a"),
                        x.optString("th").ifBlank { null }, x.optLong("d"), x.optString("u"))
                    entries[e.id] = e
                }
            }
            o.optJSONArray("l")?.let { a -> for (i in 0 until a.length()) { val p = a.getJSONArray(i); local[p.getLong(0)] = p.getLong(1) } }
        }
    }

    private fun save() {
        version++
        runCatching {
            val e = JSONArray(); entries.values.forEach { e.put(JSONObject().put("id", it.id).put("k", it.kind).put("t", it.title).put("a", it.artist).put("th", it.thumb ?: "").put("d", it.durationSec).put("u", it.url)) }
            val l = JSONArray(); local.forEach { (k, v) -> l.put(JSONArray().put(k).put(v)) }
            file.writeText(JSONObject().put("e", e).put("l", l).toString())
        }
    }
}
