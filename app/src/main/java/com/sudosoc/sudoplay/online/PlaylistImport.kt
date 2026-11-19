package com.sudosoc.sudoplay.online

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** One song of an imported playlist. [url] empty = "just a name": it is searched for when played or downloaded. */
class ImpTrack(
    val title: String, val artist: String = "", val durationSec: Long = 0, val thumb: String? = null,
    val url: String = "", val kind: OnlineKind? = null,
)
class ImpList(val name: String, val tracks: List<ImpTrack>)

/**
 * Playlist import. Links: YouTube / YouTube Music / SoundCloud (through the extractor), Spotify (the public embed page: the first ~100 songs),
 * Deezer (public API) and Audius. Anything else: paste the songs ("Artist - Title" per line, an Exportify-style CSV, or an .m3u file).
 */
object PlaylistImport {
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"

    // ---------------- text / file ----------------
    fun parseText(name: String, raw: String): ImpList {
        val text = raw.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
        val lines = text.lines().map { it.trim() }
        return when {
            lines.any { it.startsWith("#EXTINF", true) } -> ImpList(name, parseM3u(lines))
            looksLikeCsv(lines) -> ImpList(name, parseCsv(text))
            else -> ImpList(name, lines.mapNotNull(::parseLine))
        }
    }

    private fun kindOfUrl(u: String): OnlineKind = when {
        u.contains("youtube.com", true) || u.contains("youtu.be", true) -> OnlineKind.YouTube
        u.contains("soundcloud.com", true) -> OnlineKind.SoundCloud
        else -> OnlineKind.Link
    }

    /** "Artist - Title", "1. Artist – Title", "Title" or a bare link. */
    fun parseLine(line: String): ImpTrack? {
        var l = line.trim()
        if (l.isEmpty() || l.startsWith("#") || l.startsWith("//")) return null
        if (l.startsWith("http://") || l.startsWith("https://")) {
            val host = runCatching { java.net.URI(l).host }.getOrNull() ?: "Link"
            return ImpTrack(host, "", 0, null, l, kindOfUrl(l))
        }
        l = l.replace(Regex("""^\s*\d{1,3}\s*[.)\-:]\s+"""), "").trim()
        if (l.isEmpty()) return null
        val sep = Regex("""\s[-–—]\s""").find(l)
        return if (sep != null) ImpTrack(l.substring(sep.range.last + 1).trim(), l.substring(0, sep.range.first).trim())
        else ImpTrack(l)
    }

    private fun parseM3u(lines: List<String>): List<ImpTrack> {
        val out = ArrayList<ImpTrack>()
        var pending: ImpTrack? = null
        for (l in lines) {
            if (l.startsWith("#EXTINF", true)) {
                val head = l.substringAfter(':').substringBefore(',')
                val secs = head.trim().substringBefore(' ').toLongOrNull()?.coerceAtLeast(0) ?: 0
                val t = parseLine(l.substringAfter(',', ""))
                pending = t?.let { ImpTrack(it.title, it.artist, secs) }
            } else if (l.isNotEmpty() && !l.startsWith("#")) {
                val isUrl = l.startsWith("http://") || l.startsWith("https://")
                val p = pending
                if (p != null) out += if (isUrl) ImpTrack(p.title, p.artist, p.durationSec, null, l, kindOfUrl(l)) else p
                else if (isUrl) parseLine(l)?.let { out += it }
                else out += ImpTrack(l.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.'))
                pending = null
            }
        }
        return out
    }

    private fun looksLikeCsv(lines: List<String>): Boolean {
        val h = lines.firstOrNull { it.isNotEmpty() }?.lowercase() ?: return false
        return h.contains(',') && (h.contains("track name") || h.contains("title") || h.contains("song") || h.contains("artist"))
    }

    internal fun csvRow(line: String): List<String> {
        val out = ArrayList<String>(); val sb = StringBuilder(); var q = false; var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && q && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> q = !q
                c == ',' && !q -> { out += sb.toString(); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out.map { it.trim() }
    }

    private fun parseCsv(text: String): List<ImpTrack> {
        val rows = text.lines().filter { it.isNotBlank() }
        val head = csvRow(rows.first()).map { it.lowercase() }
        fun col(vararg n: String) = head.indexOfFirst { h -> n.any { h == it } }.takeIf { it >= 0 }
        val ti = col("track name", "title", "name", "song", "track", "song name") ?: return emptyList()
        val ai = col("artist name(s)", "artist name", "artist", "artists")
        val di = col("duration (ms)", "duration_ms", "duration")
        return rows.drop(1).mapNotNull { r ->
            val c = csvRow(r)
            val t = c.getOrNull(ti)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val dur = c.getOrNull(di ?: -1)?.toLongOrNull()?.let { if (it > 10_000) it / 1000 else it } ?: 0
            ImpTrack(t, c.getOrNull(ai ?: -1)?.replace(";", ", ").orEmpty(), dur)
        }
    }

    /** Does this link point at a whole playlist / album (rather than one song)? */
    fun isPlaylistLink(u: String): Boolean {
        val l = u.trim().lowercase()
        if (!(l.startsWith("http://") || l.startsWith("https://"))) return false
        return when {
            l.contains("youtube.com") || l.contains("youtu.be") -> Regex("""[?&]list=""").containsMatchIn(l) && !Regex("""[?&]v=""").containsMatchIn(l) || l.contains("/playlist?")
            l.contains("soundcloud.com") -> l.contains("/sets/")
            l.contains("spotify.com") -> Regex("""/(playlist|album)/""").containsMatchIn(l)
            l.contains("deezer.") -> Regex("""/(playlist|album)/""").containsMatchIn(l)
            l.contains("audius.co") -> l.contains("/playlist/") || l.contains("/album/")
            else -> false
        }
    }

    // ---------------- links ----------------
    suspend fun fromLink(link: String): ImpList = withContext(Dispatchers.IO) {
        val u = link.trim()
        require(u.startsWith("http://") || u.startsWith("https://")) { "Paste a full link starting with http" }
        Regex("""open\.spotify\.com/(?:intl-[a-z]+/)?(playlist|album)/([A-Za-z0-9]+)""").find(u)?.let { return@withContext spotify(it.groupValues[1], it.groupValues[2]) }
        if (u.contains("deezer.", true)) return@withContext deezer(u)
        if (u.contains("audius.co", true)) return@withContext audius(u)
        if (u.contains("spotify.", true)) throw java.io.IOException("Open the playlist in Spotify and share its open.spotify.com link")
        if (u.contains("youtube.com", true) || u.contains("youtu.be", true)) {
            val id = YtPlaylist.idOf(u) ?: throw java.io.IOException("That YouTube link has no playlist in it (it needs list=…)")
            // YouTube's page is read directly; the extractor library is the fallback
            val direct = runCatching { YtPlaylist.fetch(id) }
            direct.getOrNull()?.takeIf { it.second.isNotEmpty() }?.let { return@withContext ImpList(it.first, it.second) }
            val viaLib = runCatching { Online.playlistTracks("https://www.youtube.com/playlist?list=$id") }.getOrNull()
            if (viaLib != null && viaLib.second.isNotEmpty())
                return@withContext ImpList(viaLib.first, viaLib.second.map { ImpTrack(it.title, it.subtitle, it.durationSec, it.thumb, it.url, it.kind) })
            throw java.io.IOException(direct.exceptionOrNull()?.message ?: "No songs found in that playlist (it may be private or a YouTube mix)")
        }
        if (u.contains("soundcloud.com", true)) {
            val (name, items) = Online.playlistTracks(u)
            if (items.isEmpty()) throw java.io.IOException("No songs found in that playlist (is it private?)")
            return@withContext ImpList(name, items.map { ImpTrack(it.title, it.subtitle, it.durationSec, it.thumb, it.url, it.kind) })
        }
        // a plain .m3u / text link
        val body = Online.getJson(u, UA)
        parseText(Uri.parse(u).lastPathSegment?.substringBeforeLast('.')?.ifBlank { null } ?: "Imported", body).also {
            if (it.tracks.isEmpty()) throw java.io.IOException("That link is not a playlist I know. Paste the song names instead.")
        }
    }

    private fun spotify(type: String, id: String): ImpList {
        val html = Online.getJson("https://open.spotify.com/embed/$type/$id", UA)
        return parseSpotifyEmbed(html)
    }

    internal fun parseSpotifyEmbed(html: String): ImpList {
        val json = Regex("""<script id="__NEXT_DATA__"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1)
            ?: throw java.io.IOException("Spotify did not return the playlist")
        val e = JSONObject(json).optJSONObject("props")?.optJSONObject("pageProps")?.optJSONObject("state")?.optJSONObject("data")?.optJSONObject("entity")
            ?: throw java.io.IOException("Spotify did not return the playlist")
        val list = e.optJSONArray("trackList") ?: throw java.io.IOException("This Spotify playlist is empty or private")
        val tracks = (0 until list.length()).mapNotNull { list.optJSONObject(it) }.mapNotNull { t ->
            val title = t.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ImpTrack(title, t.optString("subtitle").replace(' ', ' ').replace(Regex("""\s*,\s*"""), ", ").trim(), t.optLong("duration") / 1000)
        }
        return ImpList(e.optString("title").ifBlank { e.optString("name") }.ifBlank { "Spotify playlist" }, tracks)
    }

    private fun deezer(link: String): ImpList {
        // short links (link.deezer.com …) redirect to the real page; okhttp follows them
        val resolved = if (Regex("""deezer\.com/(?:[a-z]{2}/)?(playlist|album)/\d+""").containsMatchIn(link)) link else {
            runCatching { Online.http.newCall(okhttp3.Request.Builder().url(link).header("User-Agent", UA).build()).execute().use { it.request.url.toString() } }.getOrDefault(link)
        }
        val m = Regex("""deezer\.com/(?:[a-z]{2}/)?(playlist|album)/(\d+)""").find(resolved) ?: throw java.io.IOException("That Deezer link is not a playlist or album")
        val type = m.groupValues[1]; val id = m.groupValues[2]
        val head = JSONObject(Online.getJson("https://api.deezer.com/$type/$id"))
        if (head.has("error")) throw java.io.IOException("Deezer: " + head.optJSONObject("error")?.optString("message"))
        val albumCover = head.optString("cover_medium").takeIf { it.startsWith("http") }
        val out = ArrayList<ImpTrack>()
        var index = 0
        while (out.size < 500) {
            val page = JSONObject(Online.getJson("https://api.deezer.com/$type/$id/tracks?limit=100&index=$index"))
            val a = page.optJSONArray("data") ?: break
            for (i in 0 until a.length()) {
                val t = a.getJSONObject(i)
                out += ImpTrack(t.optString("title"), t.optJSONObject("artist")?.optString("name").orEmpty(), t.optLong("duration"),
                    t.optJSONObject("album")?.optString("cover_medium")?.takeIf { it.startsWith("http") } ?: albumCover)
            }
            if (!page.has("next") || a.length() == 0) break
            index += 100
        }
        return ImpList(head.optString("title").ifBlank { "Deezer playlist" }, out)
    }

    private fun audius(link: String): ImpList {
        val body = Online.getJson("https://api.audius.co/v1/resolve?url=${Uri.encode(link)}&app_name=SUDOPLAY")
        val root = JSONObject(body)
        val d = root.optJSONArray("data")?.optJSONObject(0) ?: root.optJSONObject("data") ?: throw java.io.IOException("Audius did not find that link")
        val tr = d.optJSONArray("tracks")
        val name = d.optString("playlist_name").ifBlank { d.optString("title") }.ifBlank { "Audius" }
        fun one(t: JSONObject) = ImpTrack(
            t.optString("title"), t.optJSONObject("user")?.optString("name").orEmpty(), t.optLong("duration"),
            t.optJSONObject("artwork")?.optString("150x150")?.takeIf { it.startsWith("http") },
            "https://api.audius.co/v1/tracks/${t.optString("id")}/stream?app_name=SUDOPLAY", OnlineKind.Audius,
        )
        val tracks = if (tr != null) (0 until tr.length()).mapNotNull { tr.optJSONObject(it) }.map(::one) else listOf(one(d))
        return ImpList(name, tracks)
    }
}
