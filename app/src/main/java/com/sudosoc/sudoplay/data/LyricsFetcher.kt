package com.sudosoc.sudoplay.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/**
 * Optional lyrics download from LRCLIB (lrclib.net — free, no account, no key). Only the artist, title, album and length
 * of the song are sent. Found lyrics are cached on the phone, misses are remembered for two weeks. Everything goes through [NetLog].
 */
object LyricsFetcher {
    private const val HOST = "lrclib.net"
    private const val UA = "SUDOPLAY/0.6 (https://github.com/sudosoc/SUDOPLAY)"
    private const val RETRY_MS = 14L * 24 * 3600 * 1000

    private fun dir(ctx: Context) = File(ctx.filesDir, "lyrics").apply { mkdirs() }
    private fun lrc(ctx: Context, id: Long) = File(dir(ctx), "$id.lrc")
    private fun miss(ctx: Context, id: Long) = File(dir(ctx), "$id.none")

    /** The saved lyrics text (LRC), for writing into the audio file. */
    fun raw(ctx: Context, id: Long): String? = runCatching { lrc(ctx, id).takeIf { it.exists() }?.readText() }.getOrNull()

    fun cached(ctx: Context, song: Song): Lyrics? = runCatching {
        val f = lrc(ctx, song.id)
        if (f.exists()) LyricsLoader.parse(f.readText()) else null
    }.getOrNull()

    /** Looks the song up online. [force] ignores the remembered "not found". Call from a background thread. */
    fun fetch(ctx: Context, song: Song, force: Boolean = false): Lyrics? {
        cached(ctx, song)?.let { return it }
        val m = miss(ctx, song.id)
        if (!force && m.exists() && System.currentTimeMillis() - m.lastModified() < RETRY_MS) return null
        val title = song.title.trim()
        val artist = song.artist.trim().takeUnless { it.isEmpty() || it.equals("<unknown>", true) } ?: return null
        val secs = (song.durationMs / 1000).toInt()

        fun clean(x: String) = x.replace(Regex("""[\(\[].*?[\)\]]"""), "").replace(Regex("""\s+"""), " ").trim()
        val attempts = listOf(
            "/api/get?track_name=${enc(title)}&artist_name=${enc(artist)}${if (song.album.isBlank()) "" else "&album_name=" + enc(song.album)}&duration=$secs",
            "/api/get?track_name=${enc(clean(title))}&artist_name=${enc(clean(artist))}&duration=$secs",
        )
        var found: String? = null
        var failed = false
        for (p in attempts) {
            val r = request(p)
            if (r == null) { failed = true; break }          // network trouble: do not remember a miss
            if (r.first == 200) { found = pick(JSONObject(r.second)); if (found != null) break }
        }
        if (found == null && !failed) {
            val r = request("/api/search?track_name=${enc(clean(title))}&artist_name=${enc(clean(artist))}")
            if (r == null) failed = true
            else if (r.first == 200) {
                val arr = runCatching { JSONArray(r.second) }.getOrNull()
                var best: JSONObject? = null
                if (arr != null) for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    if (abs(o.optDouble("duration", -99.0) - secs) <= 4.0 && pick(o) != null) { best = o; break }
                }
                found = best?.let { pick(it) }
            }
        }
        if (found != null) {
            runCatching { lrc(ctx, song.id).writeText(found); m.delete() }
            return LyricsLoader.parse(found)
        }
        if (!failed) runCatching { m.writeText("x") }
        return null
    }

    private fun pick(o: JSONObject): String? {
        if (o.optBoolean("instrumental")) return null
        val synced = o.optString("syncedLyrics", "").takeIf { it.isNotBlank() && it != "null" }
        val plain = o.optString("plainLyrics", "").takeIf { it.isNotBlank() && it != "null" }
        return synced ?: plain
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** Returns (status, body) or null if the network failed. */
    private fun request(path: String): Pair<Int, String>? {
        val t0 = System.currentTimeMillis()
        var status = -1
        var bytes = 0L
        try {
            val c = URL("https://$HOST$path").openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 8000
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept", "application/json")
            status = c.responseCode
            val body = (if (status in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
            bytes = body.size.toLong()
            return status to body.decodeToString()
        } catch (_: Exception) {
            return null
        } finally {
            NetLog.add(NetEntry(System.currentTimeMillis(), "GET", HOST, path.substringBefore("?"), status, bytes, System.currentTimeMillis() - t0))
        }
    }
}
