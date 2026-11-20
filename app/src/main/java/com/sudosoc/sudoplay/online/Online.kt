package com.sudosoc.sudoplay.online

import android.net.Uri
import com.sudosoc.sudoplay.data.NetEntry
import com.sudosoc.sudoplay.data.NetLog
import com.sudosoc.sudoplay.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.TimeUnit

enum class OnlineKind(val label: String) {
    Quran("Quran"), YouTube("YouTube"), SoundCloud("SoundCloud"), Audius("Audius"), Archive("Archive"), Radio("Radio"), Link("Link");
    /** true when [OnlineItem.url] already is the audio file (no extractor needed). */
    val direct get() = this == Quran || this == Archive || this == Radio || this == Link || this == Audius
}

/**
 * One search result / station / link. [url] is the page (YouTube/SoundCloud) or the audio file itself.
 * When [collection] is set the item is a folder (a reciter, an archive.org item…) that opens a list of children.
 */
class OnlineItem(
    val kind: OnlineKind, val title: String, val subtitle: String, val thumb: String?, val durationSec: Long, val url: String,
    val collection: String? = null,
) { val key get() = collection ?: url }

/** Everything that leaves the phone for the Online tab goes through here and is written to the network log. */
object Online {
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"
    internal val http: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).followRedirects(true).build()
    }

    private class OkDownloader : Downloader() {
        override fun execute(request: Request): Response {
            val b = okhttp3.Request.Builder().url(request.url())
            val method = request.httpMethod()
            val data = request.dataToSend()
            b.method(method, if (data != null) data.toRequestBody() else if (method == "POST") ByteArray(0).toRequestBody() else null)
            b.header("User-Agent", UA)
            for ((k, v) in request.headers()) { b.removeHeader(k); v.forEach { b.addHeader(k, it) } }
            val t0 = System.currentTimeMillis()
            http.newCall(b.build()).execute().use { r ->
                if (r.code == 429) throw org.schabi.newpipe.extractor.exceptions.ReCaptchaException("Rate limited", request.url())
                val body = r.body?.string() ?: ""
                log(method, request.url(), r.code, body.length.toLong(), System.currentTimeMillis() - t0)
                return Response(r.code, r.message, r.headers.toMultimap(), body, r.request.url.toString())
            }
        }
    }

    internal fun log(method: String, url: String, code: Int, bytes: Long, ms: Long) {
        val u = Uri.parse(url)
        NetLog.add(NetEntry(System.currentTimeMillis(), method, u.host ?: "?", (u.path ?: "/").take(60), code, bytes, ms))
    }

    lateinit var appCtx: android.content.Context
    fun attach(ctx: android.content.Context) { appCtx = ctx.applicationContext }

    @Volatile private var inited = false
    internal fun init() {
        if (inited) return
        synchronized(this) { if (!inited) { NewPipe.init(OkDownloader(), Localization.DEFAULT); inited = true } }
    }

    fun isLink(q: String) = q.trim().let { (it.startsWith("http://") || it.startsWith("https://")) && !it.contains(' ') }

    fun idFor(url: String): Long = (url.hashCode().toLong() shl 32 or (url.reversed().hashCode().toLong() and 0xFFFFFFFFL)) or Long.MIN_VALUE

    // ---------------- search ----------------
    suspend fun search(kind: OnlineKind, query: String): List<OnlineItem> = withContext(Dispatchers.IO) {
        when (kind) {
            OnlineKind.Radio -> radio(query)
            OnlineKind.YouTube, OnlineKind.SoundCloud -> {
                init()
                val svc = if (kind == OnlineKind.YouTube) ServiceList.YouTube else ServiceList.SoundCloud
                val filters = listOf(if (kind == OnlineKind.YouTube) "music_songs" else "tracks")
                fun run(f: List<String>) = SearchInfo.getInfo(svc, svc.searchQHFactory.fromQuery(query, f, "")).relatedItems.filterIsInstance<StreamInfoItem>()
                val found = runCatching { run(filters) }.getOrDefault(emptyList()).ifEmpty { run(if (kind == OnlineKind.YouTube) listOf("videos") else emptyList()) }
                found.filter { it.duration != 0L || kind == OnlineKind.SoundCloud }.map {
                    OnlineItem(kind, it.name ?: "", it.uploaderName ?: "", it.thumbnails.firstOrNull()?.url, it.duration, it.url)
                }
            }
            OnlineKind.Quran -> Quran.search(appCtx, query)
            OnlineKind.Archive -> archiveSearch(query)
            OnlineKind.Audius -> audiusSearch(query)
            OnlineKind.Link -> emptyList()
        }
    }

    /** Opens a collection item: a reciter (→ recitations), an archive.org item (→ its audio files). */
    suspend fun children(item: OnlineItem): List<OnlineItem> = withContext(Dispatchers.IO) {
        val key = item.collection ?: return@withContext emptyList()
        when {
            key.startsWith("q:") -> Quran.children(appCtx, key)
            key.startsWith("a:") -> archiveFiles(key.removePrefix("a:"), item.title)
            else -> emptyList()
        }
    }

    // ---------------- Audius (open music network: full-length tracks, no key) ----------------
    private fun audiusSearch(q: String): List<OnlineItem> {
        val body = getJson("https://api.audius.co/v1/tracks/search?query=${Uri.encode(q)}&app_name=SUDOPLAY&limit=20")
        val a = org.json.JSONObject(body).optJSONArray("data") ?: return emptyList()
        return (0 until a.length()).map { a.getJSONObject(it) }.filter { it.optBoolean("is_streamable", true) }.map {
            val id = it.optString("id")
            OnlineItem(
                OnlineKind.Audius, it.optString("title"), it.optJSONObject("user")?.optString("name").orEmpty(),
                it.optJSONObject("artwork")?.optString("150x150")?.takeIf { u -> u.startsWith("http") }, it.optLong("duration"),
                "https://api.audius.co/v1/tracks/$id/stream?app_name=SUDOPLAY",
            )
        }
    }


    /** Looks a song up by name across the search sources (YouTube first, then SoundCloud, Audius). Strict match first, then a looser one. */
    suspend fun findFor(title: String, artist: String, durationSec: Long = 0, kinds: List<OnlineKind> = listOf(OnlineKind.YouTube, OnlineKind.SoundCloud, OnlineKind.Audius)): OnlineItem? = withContext(Dispatchers.IO) {
        val q = "$artist $title".replace(Regex("\\s+"), " ").trim()
        if (q.isBlank()) return@withContext null
        val loose = ArrayList<OnlineItem>()
        for (k in kinds) {
            val found = runCatching { search(k, q) }.getOrDefault(emptyList())
            OnlineMatch.best(title, artist, durationSec, found)?.let { return@withContext it }
            val tt = OnlineMatch.tokens(title)
            if (tt.isNotEmpty()) found.filter { it.collection == null && it.url.isNotBlank() }.firstOrNull { c ->
                val all = OnlineMatch.tokens(c.title + " " + c.subtitle); tt.count { it in all }.toDouble() / tt.size >= 0.5
            }?.let { loose.add(it) }
        }
        loose.firstOrNull()
    }

    /** A YouTube / SoundCloud playlist link → (name, tracks). Follows "more pages" up to [max] tracks. */
    suspend fun playlistTracks(link: String, max: Int = 300): Pair<String, List<OnlineItem>> = withContext(Dispatchers.IO) {
        init()
        val svc = NewPipe.getServiceByUrl(link)
        val kind = if (svc.serviceId == ServiceList.SoundCloud.serviceId) OnlineKind.SoundCloud else OnlineKind.YouTube
        val info = org.schabi.newpipe.extractor.playlist.PlaylistInfo.getInfo(svc, link)
        val out = ArrayList<StreamInfoItem>(info.relatedItems.filterIsInstance<StreamInfoItem>())
        var page = info.nextPage
        while (page != null && out.size < max) {
            val more = org.schabi.newpipe.extractor.playlist.PlaylistInfo.getMoreItems(svc, link, page)
            out += more.items.filterIsInstance<StreamInfoItem>()
            page = more.nextPage
        }
        (info.name ?: "Playlist") to out.take(max).map {
            OnlineItem(kind, it.name ?: "", (it.uploaderName ?: "").removeSuffix(" - Topic"), it.thumbnails.firstOrNull()?.url, it.duration, it.url)
        }
    }

    /** Small image download (covers), logged like everything else. */
    fun fetchBytes(url: String): ByteArray? = runCatching {
        val t0 = System.currentTimeMillis()
        http.newCall(okhttp3.Request.Builder().url(url).header("User-Agent", UA).build()).execute().use { r ->
            val b = r.body?.bytes() ?: return@use null
            log("GET", url, r.code, b.size.toLong(), System.currentTimeMillis() - t0)
            if (r.isSuccessful) b else null
        }
    }.getOrNull()

    // ---------------- "same song, another source" ----------------
    suspend fun alternative(item: OnlineItem): OnlineItem? = withContext(Dispatchers.IO) {
        val q = com.sudosoc.sudoplay.online.OnlineMatch.tokens(item.title + " " + item.subtitle).take(8).joinToString(" ")
        if (q.isBlank()) return@withContext null
        val cands = listOf(OnlineKind.SoundCloud, OnlineKind.Audius).flatMap { k -> runCatching { search(k, q) }.getOrDefault(emptyList()) }
        OnlineMatch.best(item.title, item.subtitle, item.durationSec, cands)
    }

    // ---------------- search-as-you-type suggestions (opt-in: sends the partial text to Google's suggest endpoint) ----------------
    suspend fun suggest(q: String): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val body = getJson("https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=${Uri.encode(q)}")
            val arr = org.json.JSONArray(body).getJSONArray(1)
            (0 until arr.length()).map { arr.getString(it) }.take(6)
        }.getOrDefault(emptyList())
    }

    // ---------------- archive.org (free audio library, no key) ----------------
    internal fun getJson(url: String, ua: String = "SUDOPLAY/0.9"): String {
        val t0 = System.currentTimeMillis()
        http.newCall(okhttp3.Request.Builder().url(url).header("User-Agent", ua).build()).execute().use { r ->
            val body = r.body?.string() ?: ""
            log("GET", url, r.code, body.length.toLong(), System.currentTimeMillis() - t0)
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
            return body
        }
    }

    private fun archiveSearch(q: String): List<OnlineItem> {
        val term = Uri.encode("($q) AND mediatype:audio")
        val body = getJson("https://archive.org/advancedsearch.php?q=$term&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=creator&rows=20&sort%5B%5D=downloads+desc&output=json")
        val docs = org.json.JSONObject(body).getJSONObject("response").getJSONArray("docs")
        return (0 until docs.length()).map { docs.getJSONObject(it) }.map {
            val creator = it.opt("creator").let { c -> if (c is JSONArray) c.optString(0) else c?.toString().orEmpty() }
            OnlineItem(OnlineKind.Archive, it.optString("title").ifBlank { it.optString("identifier") }, creator, "https://archive.org/services/img/${it.optString("identifier")}", 0L, "", "a:" + it.optString("identifier"))
        }
    }

    private fun archiveFiles(id: String, parentTitle: String): List<OnlineItem> {
        val files = org.json.JSONObject(getJson("https://archive.org/metadata/${Uri.encode(id)}")).optJSONArray("files") ?: return emptyList()
        val audio = (0 until files.length()).map { files.getJSONObject(it) }.filter {
            val f = it.optString("format")
            f.contains("MP3", true) || f.contains("Ogg Vorbis", true) || f.contains("M4A", true) || f.contains("AAC", true)
        }
        // an item usually stores each track in several derivatives; keep one per track, MP3 first
        val byBase = LinkedHashMap<String, org.json.JSONObject>()
        for (f in audio.sortedBy { if (it.optString("format").contains("MP3", true)) 0 else 1 }) {
            val base = f.optString("name").substringBeforeLast('.')
            byBase.putIfAbsent(base, f)
        }
        return byBase.values.sortedBy { it.optString("name") }.map { f ->
            val len = f.optString("length").let { l -> l.toDoubleOrNull()?.toLong() ?: l.split(":").mapNotNull { p -> p.toLongOrNull() }.fold(0L) { a, p -> a * 60 + p } }
            OnlineItem(
                OnlineKind.Archive, f.optString("title").ifBlank { f.optString("name").substringBeforeLast('.') }, parentTitle, "https://archive.org/services/img/$id",
                len, "https://archive.org/download/${Uri.encode(id)}/" + f.optString("name").split("/").joinToString("/") { Uri.encode(it) },
            )
        }
    }

    // ---------------- internet radio (radio-browser.info, no key) ----------------
    private val radioHosts = listOf("de1.api.radio-browser.info", "nl1.api.radio-browser.info", "at1.api.radio-browser.info")
    private fun radio(query: String): List<OnlineItem> {
        var last: Throwable? = null
        for (h in radioHosts) {
            try {
                val q = if (query.isBlank()) "/json/stations/topclick/40" else
                    "/json/stations/search?name=${Uri.encode(query)}&limit=40&hidebroken=true&order=clickcount&reverse=true"
                val t0 = System.currentTimeMillis()
                val req = okhttp3.Request.Builder().url("https://$h$q").header("User-Agent", "SUDOPLAY/0.8").build()
                http.newCall(req).execute().use { r ->
                    val body = r.body?.string() ?: ""
                    log("GET", req.url.toString(), r.code, body.length.toLong(), System.currentTimeMillis() - t0)
                    if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
                    val a = JSONArray(body)
                    return (0 until a.length()).map { a.getJSONObject(it) }
                        .filter { it.optString("url_resolved").isNotBlank() }
                        .map {
                            OnlineItem(
                                OnlineKind.Radio, it.optString("name").trim(),
                                listOf(it.optString("country"), it.optString("tags").split(",").take(2).joinToString(", ")).filter { s -> s.isNotBlank() }.joinToString(" • "),
                                it.optString("favicon").takeIf { f -> f.startsWith("http") }, 0L, it.optString("url_resolved"),
                            )
                        }
                }
            } catch (e: Throwable) { last = e }
        }
        throw last ?: java.io.IOException("Radio directory unreachable")
    }

    /** Highest audio bitrate (kbps) allowed for streaming right now; 0 = no limit. Set by the app from the quality setting and the network type. */
    @Volatile var capKbps: () -> Int = { 0 }

    /** Best stream under the cap: AAC/m4a first, then the highest bitrate that still fits; if nothing fits, the smallest one. */
    internal fun pickAudio(audio: List<org.schabi.newpipe.extractor.stream.AudioStream>, cap: Int): org.schabi.newpipe.extractor.stream.AudioStream? {
        val fits = if (cap > 0) audio.filter { it.averageBitrate in 1..cap }.ifEmpty { listOfNotNull(audio.filter { it.averageBitrate > 0 }.minByOrNull { it.averageBitrate }) }.ifEmpty { audio } else audio
        return fits.sortedWith(compareByDescending<org.schabi.newpipe.extractor.stream.AudioStream> { it.format?.suffix == "m4a" }.thenByDescending { it.averageBitrate }).firstOrNull()
    }

    // ---------------- turning an item into something the player can play ----------------
    suspend fun resolve(item: OnlineItem): Song = withContext(Dispatchers.IO) {
        val id = idFor(item.url)
        when (item.kind) {
            OnlineKind.Radio, OnlineKind.Link, OnlineKind.Quran, OnlineKind.Archive, OnlineKind.Audius ->
                song(id, item.title, item.subtitle.ifBlank { if (item.kind == OnlineKind.Radio) "Radio" else "Stream" }, item.url, item.thumb, item.durationSec * 1000)
            else -> {
                init()
                val info = StreamInfo.getInfo(item.url)
                val audio = info.audioStreams.filter { it.isUrl && !it.content.isNullOrBlank() }
                // prefer AAC/m4a (plays everywhere), then the highest bitrate
                val best = pickAudio(audio, capKbps())?.content
                    // some YouTube responses only carry a small muxed (video+audio) file; the player ignores its video track
                    ?: info.videoStreams.filter { it.isUrl && !it.isVideoOnly && !it.content.isNullOrBlank() }.minByOrNull { it.height }?.content
                    ?: throw java.io.IOException("No playable stream found (the source may be blocking this network)")
                val thumb = info.thumbnails.maxByOrNull { it.height }?.url ?: info.thumbnails.lastOrNull()?.url ?: item.thumb
                song(id, info.name ?: item.title, (info.uploaderName ?: item.subtitle).removeSuffix(" - Topic"), best, thumb, info.duration * 1000)
            }
        }
    }

    /** A pasted link: YouTube / SoundCloud pages go through the extractor, anything else is played as a direct stream. */
    suspend fun fromLink(link: String): OnlineItem = withContext(Dispatchers.IO) {
        val u = link.trim()
        require(u.startsWith("http://") || u.startsWith("https://")) { "Paste a full link starting with http" }
        Regex("archive\\.org/details/([^/?#]+)").find(u)?.let { m ->
            return@withContext OnlineItem(OnlineKind.Archive, m.groupValues[1], "archive.org", null, 0, "", "a:" + m.groupValues[1])
        }
        init()
        val svc = runCatching { NewPipe.getServiceByUrl(u) }.getOrNull()
        when (svc?.serviceId) {
            // read the real title / artist / cover right away, so a pasted link is named properly everywhere (queue, downloads, playlists)
            ServiceList.YouTube.serviceId, ServiceList.SoundCloud.serviceId -> {
                val kind = if (svc.serviceId == ServiceList.YouTube.serviceId) OnlineKind.YouTube else OnlineKind.SoundCloud
                runCatching { StreamInfo.getInfo(u) }.map {
                    OnlineItem(kind, it.name ?: kind.label, (it.uploaderName ?: "").removeSuffix(" - Topic"), it.thumbnails.maxByOrNull { t -> t.height }?.url ?: it.thumbnails.lastOrNull()?.url, it.duration, u)
                }.getOrElse { OnlineItem(kind, kind.label, "", null, 0, u) }
            }
            else -> OnlineItem(OnlineKind.Link, Uri.parse(u).lastPathSegment?.takeIf { it.isNotBlank() } ?: (Uri.parse(u).host ?: "Stream"), Uri.parse(u).host ?: "", null, 0, u)
        }
    }

    private fun song(id: Long, title: String, artist: String, uri: String, art: String?, durMs: Long) = Song(
        id = id, title = title, artist = artist, album = "Online", albumId = 0, durationMs = durMs, uri = Uri.parse(uri),
        dateAdded = System.currentTimeMillis() / 1000, track = 0, folder = "", path = "", artUrl = art, online = true,
    )
}
