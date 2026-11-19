package com.sudosoc.sudoplay.online

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** A song suggested by Discovery: only a name (the player finds a source when you play it). */
class DTrack(val title: String, val artist: String, val durationSec: Long, val thumb: String?)
class DPlaylist(val id: Long, val title: String, val thumb: String?, val count: Int)

/**
 * "What should I listen to?" — public charts and playlists from Deezer's open API (no key, nothing about you is sent) and Audius trending.
 * Results are cached for ten minutes. Everything goes through the network log.
 */
object Discovery {
    val genres = listOf(
        0 to "Top", 132 to "Pop", 116 to "Rap / Hip-hop", 152 to "Rock", 113 to "Dance", 165 to "R&B", 106 to "Electro",
        129 to "Jazz", 98 to "Classical", 464 to "Metal", 169 to "Soul & Funk", 2 to "African", 16 to "Asian", 197 to "Latin", 81 to "Indian", 466 to "Folk",
    )
    /** Searches that find popular regional / mood playlists. */
    val playlistTopics = listOf("Top Egypt", "Top Arabic", "Arabic Pop", "Khaleeji", "Mahraganat", "Lofi", "Workout", "Chill", "Focus", "Road trip", "Throwback", "Party")

    private val cache = HashMap<String, Pair<Long, Any>>()
    private const val TTL = 10 * 60 * 1000L
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> cached(key: String, load: suspend () -> T): T {
        cache[key]?.let { (at, v) -> if (System.currentTimeMillis() - at < TTL) return v as T }
        return load().also { cache[key] = System.currentTimeMillis() to it }
    }

    fun parseTracks(json: String): List<DTrack> {
        val a = JSONObject(json).optJSONArray("data") ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.mapNotNull { t ->
            val title = t.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            DTrack(title, t.optJSONObject("artist")?.optString("name").orEmpty(), t.optLong("duration"),
                t.optJSONObject("album")?.optString("cover_medium")?.takeIf { it.startsWith("http") })
        }
    }

    fun parsePlaylists(json: String): List<DPlaylist> {
        val a = JSONObject(json).optJSONArray("data") ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.mapNotNull { p ->
            val id = p.optLong("id", 0); if (id == 0L) return@mapNotNull null
            DPlaylist(id, p.optString("title"), p.optString("picture_medium").takeIf { it.startsWith("http") }, p.optInt("nb_tracks"))
        }
    }

    suspend fun chart(genre: Int, limit: Int = 30): List<DTrack> = cached("chart$genre") {
        withContext(Dispatchers.IO) { parseTracks(Online.getJson("https://api.deezer.com/chart/$genre/tracks?limit=$limit")) }
    }

    suspend fun chartPlaylists(limit: Int = 20): List<DPlaylist> = cached("chartpl") {
        withContext(Dispatchers.IO) { parsePlaylists(Online.getJson("https://api.deezer.com/chart/0/playlists?limit=$limit")) }
    }

    suspend fun playlists(topic: String, limit: Int = 20): List<DPlaylist> = cached("pl:$topic") {
        withContext(Dispatchers.IO) { parsePlaylists(Online.getJson("https://api.deezer.com/search/playlist?q=${Uri.encode(topic)}&limit=$limit")) }
    }

    /** Audius trending: full-length tracks that play directly (no search needed). */
    suspend fun audiusTrending(limit: Int = 20): List<OnlineItem> = cached("audius") {
        withContext(Dispatchers.IO) {
            val a = JSONObject(Online.getJson("https://api.audius.co/v1/tracks/trending?app_name=SUDOPLAY&limit=$limit")).optJSONArray("data") ?: return@withContext emptyList()
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.filter { it.optBoolean("is_streamable", true) }.map {
                OnlineItem(
                    OnlineKind.Audius, it.optString("title"), it.optJSONObject("user")?.optString("name").orEmpty(),
                    it.optJSONObject("artwork")?.optString("150x150")?.takeIf { u -> u.startsWith("http") }, it.optLong("duration"),
                    "https://api.audius.co/v1/tracks/${it.optString("id")}/stream?app_name=SUDOPLAY",
                )
            }
        }
    }

    /** "Because you listen to …": for each of [artists], the top songs of related artists. Only called when the user turned it on. */
    suspend fun becauseOf(artists: List<String>): List<Pair<String, List<DTrack>>> = coroutineScope {
        artists.take(3).map { name ->
            async {
                runCatching {
                    cached("rel:$name") {
                        withContext(Dispatchers.IO) {
                            val found = JSONObject(Online.getJson("https://api.deezer.com/search/artist?q=${Uri.encode(name)}&limit=1")).optJSONArray("data")?.optJSONObject(0)
                            val id = found?.optLong("id") ?: return@withContext emptyList<DTrack>()
                            val related = JSONObject(Online.getJson("https://api.deezer.com/artist/$id/related?limit=4")).optJSONArray("data")
                            val ids = (0 until (related?.length() ?: 0)).mapNotNull { related?.optJSONObject(it)?.optLong("id") }
                            ids.take(3).flatMap { rid -> parseTracks(Online.getJson("https://api.deezer.com/artist/$rid/top?limit=4")) }
                        }
                    }
                }.getOrDefault(emptyList()).let { name to it }
            }
        }.awaitAll().filter { it.second.isNotEmpty() }
    }
}
