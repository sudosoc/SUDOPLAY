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

enum class OnlineKind { YouTube, SoundCloud, Radio, Link }

/** One search result / station / link. [url] is the page (YouTube/SoundCloud) or the stream itself (radio, link). */
class OnlineItem(val kind: OnlineKind, val title: String, val subtitle: String, val thumb: String?, val durationSec: Long, val url: String)

/** Everything that leaves the phone for the Online tab goes through here and is written to the network log. */
object Online {
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"
    private val http: OkHttpClient by lazy {
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

    private fun log(method: String, url: String, code: Int, bytes: Long, ms: Long) {
        val u = Uri.parse(url)
        NetLog.add(NetEntry(System.currentTimeMillis(), method, u.host ?: "?", (u.path ?: "/").take(60), code, bytes, ms))
    }

    @Volatile private var inited = false
    private fun init() {
        if (inited) return
        synchronized(this) { if (!inited) { NewPipe.init(OkDownloader(), Localization.DEFAULT); inited = true } }
    }

    private fun idFor(url: String): Long = (url.hashCode().toLong() shl 32 or (url.reversed().hashCode().toLong() and 0xFFFFFFFFL)) or Long.MIN_VALUE

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
            OnlineKind.Link -> emptyList()
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

    // ---------------- turning an item into something the player can play ----------------
    suspend fun resolve(item: OnlineItem): Song = withContext(Dispatchers.IO) {
        val id = idFor(item.url)
        when (item.kind) {
            OnlineKind.Radio, OnlineKind.Link -> song(id, item.title, item.subtitle.ifBlank { if (item.kind == OnlineKind.Radio) "Radio" else "Stream" }, item.url, item.thumb, 0L)
            else -> {
                init()
                val info = StreamInfo.getInfo(item.url)
                val audio = info.audioStreams.filter { it.isUrl && !it.content.isNullOrBlank() }
                // prefer AAC/m4a (plays everywhere), then the highest bitrate
                val best = audio.sortedWith(compareByDescending<org.schabi.newpipe.extractor.stream.AudioStream> { it.format?.suffix == "m4a" }.thenByDescending { it.averageBitrate }).firstOrNull()?.content
                    // some YouTube responses only carry a small muxed (video+audio) file; the player ignores its video track
                    ?: info.videoStreams.filter { it.isUrl && !it.isVideoOnly && !it.content.isNullOrBlank() }.minByOrNull { it.height }?.content
                    ?: throw java.io.IOException("No playable stream found (the source may be blocking this network)")
                song(id, info.name ?: item.title, info.uploaderName ?: item.subtitle, best, info.thumbnails.firstOrNull()?.url ?: item.thumb, info.duration * 1000)
            }
        }
    }

    /** A pasted link: YouTube / SoundCloud pages go through the extractor, anything else is played as a direct stream. */
    suspend fun fromLink(link: String): OnlineItem = withContext(Dispatchers.IO) {
        val u = link.trim()
        require(u.startsWith("http://") || u.startsWith("https://")) { "Paste a full link starting with http" }
        init()
        val svc = runCatching { NewPipe.getServiceByUrl(u) }.getOrNull()
        when (svc?.serviceId) {
            ServiceList.YouTube.serviceId -> OnlineItem(OnlineKind.YouTube, "YouTube", "", null, 0, u)
            ServiceList.SoundCloud.serviceId -> OnlineItem(OnlineKind.SoundCloud, "SoundCloud", "", null, 0, u)
            else -> OnlineItem(OnlineKind.Link, Uri.parse(u).lastPathSegment?.takeIf { it.isNotBlank() } ?: (Uri.parse(u).host ?: "Stream"), Uri.parse(u).host ?: "", null, 0, u)
        }
    }

    private fun song(id: Long, title: String, artist: String, uri: String, art: String?, durMs: Long) = Song(
        id = id, title = title, artist = artist, album = "Online", albumId = 0, durationMs = durMs, uri = Uri.parse(uri),
        dateAdded = System.currentTimeMillis() / 1000, track = 0, folder = "", path = "", artUrl = art, online = true,
    )
}
