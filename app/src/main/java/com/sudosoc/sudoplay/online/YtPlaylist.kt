package com.sudosoc.sudoplay.online

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads a public YouTube playlist straight from its web page. The extractor library returns the playlist name but no songs since YouTube
 * changed the page layout, so this follows the page's own data (and its "load more" continuation) instead. Nothing is sent but the playlist id.
 */
object YtPlaylist {
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"
    class Page(val name: String?, val tracks: List<ImpTrack>, val next: String?, val key: String?, val version: String?)

    fun idOf(link: String): String? = Regex("""[?&]list=([A-Za-z0-9_-]+)""").find(link)?.groupValues?.get(1)

    fun fetch(listId: String, max: Int = 300): Pair<String, List<ImpTrack>> {
        val html = get("https://www.youtube.com/playlist?list=$listId", null)
        val first = parseHtml(html)
        val out = ArrayList(first.tracks)
        var token = first.next
        var guard = 0
        while (token != null && out.size < max && guard++ < 6) {
            val body = JSONObject().put("context", JSONObject().put("client", JSONObject().put("clientName", "WEB").put("clientVersion", first.version ?: "2.20260101.00.00").put("hl", "en")))
                .put("continuation", token).toString()
            val more = parseJson(JSONObject(get("https://www.youtube.com/youtubei/v1/browse?key=${first.key ?: ""}", body)), false)
            if (more.tracks.isEmpty()) break
            out += more.tracks
            token = more.next
        }
        return (first.name ?: "YouTube playlist") to out.distinctBy { it.url }.take(max)
    }

    private fun get(url: String, post: String?): String {
        val b = okhttp3.Request.Builder().url(url).header("User-Agent", UA).header("Accept-Language", "en-US,en;q=0.9").header("Cookie", "CONSENT=YES+1; SOCS=CAI")
        if (post != null) b.post(post.toRequestBody("application/json".toMediaType()))
        val t0 = System.currentTimeMillis()
        Online.http.newCall(b.build()).execute().use { r ->
            val s = r.body?.string() ?: ""
            runCatching { Online.log(if (post != null) "POST" else "GET", url, r.code, s.length.toLong(), System.currentTimeMillis() - t0) }
            if (!r.isSuccessful) throw java.io.IOException("YouTube answered HTTP ${r.code}")
            return s
        }
    }

    internal fun parseHtml(html: String): Page {
        val marker = "var ytInitialData = "
        val i = html.indexOf(marker)
        if (i < 0) throw java.io.IOException("YouTube did not return the playlist (is it private?)")
        val start = i + marker.length
        val end = html.indexOf(";</script>", start)
        if (end < 0) throw java.io.IOException("YouTube page was not readable")
        val key = Regex(""""INNERTUBE_API_KEY":"([^"]+)"""").find(html)?.groupValues?.get(1)
        val ver = Regex(""""INNERTUBE_CONTEXT_CLIENT_VERSION":"([^"]+)"""").find(html)?.groupValues?.get(1) ?: Regex(""""clientVersion":"([^"]+)"""").find(html)?.groupValues?.get(1)
        val p = parseJson(JSONObject(html.substring(start, end)), true)
        return Page(p.name, p.tracks, p.next, key, ver)
    }

    /** [mainOnly]: first page → only the main column (not the sidebar). */
    internal fun parseJson(root: JSONObject, mainOnly: Boolean): Page {
        val scope: Any = if (mainOnly) (root.optJSONObject("contents")?.optJSONObject("twoColumnBrowseResultsRenderer")?.optJSONArray("tabs") ?: root) else root
        val tracks = ArrayList<ImpTrack>()
        var next: String? = null
        fun walk(o: Any?) {
            when (o) {
                is JSONObject -> {
                    o.optJSONObject("lockupViewModel")?.let { l -> lockup(l)?.let { tracks += it } }
                    o.optJSONObject("playlistVideoRenderer")?.let { v -> video(v)?.let { tracks += it } }
                    o.optJSONObject("continuationItemRenderer")?.let { c ->
                        c.optJSONObject("continuationEndpoint")?.optJSONObject("continuationCommand")?.optString("token")?.takeIf { it.isNotBlank() }?.let { next = it }
                    }
                    for (k in o.keys()) if (k != "lockupViewModel" && k != "playlistVideoRenderer") walk(o.opt(k))
                }
                is JSONArray -> for (i in 0 until o.length()) walk(o.opt(i))
            }
        }
        walk(scope)
        val name = root.optJSONObject("metadata")?.optJSONObject("playlistMetadataRenderer")?.optString("title")?.takeIf { it.isNotBlank() }
            ?: root.optJSONObject("microformat")?.optJSONObject("microformatDataRenderer")?.optString("title")?.takeIf { it.isNotBlank() }
        return Page(name, tracks, next, null, null)
    }

    private fun clean(a: String) = a.removeSuffix(" - Topic").trim()

    private fun lockup(l: JSONObject): ImpTrack? {
        if (l.optString("contentType") != "LOCKUP_CONTENT_TYPE_VIDEO") return null
        val id = l.optString("contentId").takeIf { it.length in 8..16 } ?: return null
        val meta = l.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
        val title = meta?.optJSONObject("title")?.optString("content")?.takeIf { it.isNotBlank() } ?: return null
        // channel names: first metadata row, first part
        val rows = meta.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")?.optJSONArray("metadataRows")
        var artist = ""
        if (rows != null) for (r in 0 until rows.length()) {
            val parts = rows.optJSONObject(r)?.optJSONArray("metadataParts") ?: continue
            val t = parts.optJSONObject(0)?.optJSONObject("text")?.optString("content").orEmpty()
            if (t.isNotBlank() && !Regex("""^\d[\d.,KMB ]*\s*(views?|watching)""", RegexOption.IGNORE_CASE).containsMatchIn(t)) { artist = clean(t); break }
        }
        var dur = 0L
        val overlays = l.optJSONObject("contentImage")?.optJSONObject("thumbnailViewModel")?.optJSONArray("overlays")
        if (overlays != null) for (i in 0 until overlays.length()) {
            val badges = overlays.optJSONObject(i)?.optJSONObject("thumbnailBottomOverlayViewModel")?.optJSONArray("badges") ?: continue
            for (b in 0 until badges.length()) {
                val txt = badges.optJSONObject(b)?.optJSONObject("thumbnailBadgeViewModel")?.optString("text").orEmpty()
                parseClock(txt)?.let { dur = it }
            }
        }
        return ImpTrack(title, artist, dur, "https://i.ytimg.com/vi/$id/hqdefault.jpg", "https://www.youtube.com/watch?v=$id", OnlineKind.YouTube)
    }

    private fun video(v: JSONObject): ImpTrack? {
        val id = v.optString("videoId").takeIf { it.isNotBlank() } ?: return null
        val title = v.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: return null
        val artist = clean(v.optJSONObject("shortBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty())
        return ImpTrack(title, artist, v.optString("lengthSeconds").toLongOrNull() ?: 0, "https://i.ytimg.com/vi/$id/hqdefault.jpg", "https://www.youtube.com/watch?v=$id", OnlineKind.YouTube)
    }

    fun parseClock(s: String): Long? {
        val p = s.trim().split(":")
        if (p.size !in 2..3 || p.any { it.toLongOrNull() == null }) return null
        return p.fold(0L) { a, x -> a * 60 + x.toLong() }
    }
}
