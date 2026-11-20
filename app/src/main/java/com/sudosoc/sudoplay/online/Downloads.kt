package com.sudosoc.sudoplay.online

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.sudosoc.sudoplay.data.NetEntry
import com.sudosoc.sudoplay.data.NetLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/** One thing the user can pick when downloading: a file or stream with its own format / bitrate. */
class DlOption(val label: String, val url: String, val ext: String, val hls: Boolean)

class DlJob(val id: Long, val title: String, val artist: String, val option: DlOption, val playlistId: String?) {
    var progress by mutableFloatStateOf(0f)
    var state by mutableStateOf("Queued")      // Queued / Downloading / Done / Failed / Cancelled
    var error by mutableStateOf<String?>(null)
    var songId: Long = -1
    /** Where the song came from: cover to save with it, length, and every online id that should flip to "on device" once it is saved. */
    var thumb: String? = null
    var durSec: Long = 0
    var origIds: List<Long> = emptyList()
    internal var job: Job? = null
    @Volatile var cancelled = false
    /** The page the song came from (kept so a download can be re-resolved and restored after a restart). */
    var srcUrl: String = ""
    var srcKind: String = "Link"
    val active get() = state == "Queued" || state == "Downloading" || state.startsWith("Waiting")
}

/**
 * Downloads audio into Music/SUDOPLAY (so it shows up in the library like any other file) and can drop it straight into a playlist.
 * Two at a time; a foreground service keeps the process alive while anything is running.
 */
object DownloadCenter {
    val jobs = mutableStateListOf<DlJob>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(2)
    private var next = 1L
    private lateinit var ctx: Context
    private var onSaved: (DlJob) -> Unit = {}
    private val http by lazy { OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).followRedirects(true).build() }
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"

    fun attach(c: Context, saved: (DlJob) -> Unit) { ctx = c.applicationContext; onSaved = saved }

    // ---------- unfinished downloads survive the app being closed ----------
    private fun persist() {
        runCatching {
            val a = org.json.JSONArray()
            jobs.filter { it.active && it.srcUrl.isNotBlank() }.forEach { j ->
                a.put(org.json.JSONObject().put("t", j.title).put("a", j.artist).put("u", j.srcUrl).put("k", j.srcKind).put("th", j.thumb ?: "")
                    .put("d", j.durSec).put("p", j.playlistId ?: "").put("o", org.json.JSONArray(j.origIds)))
            }
            ctx.getSharedPreferences("sudoplay", Context.MODE_PRIVATE).edit().putString("dl_queue", a.toString()).apply()
        }
    }
    /** Called once at start: picks up what was still downloading when the app was closed. */
    fun restore() {
        val raw = ctx.getSharedPreferences("sudoplay", Context.MODE_PRIVATE).getString("dl_queue", null) ?: return
        ctx.getSharedPreferences("sudoplay", Context.MODE_PRIVATE).edit().remove("dl_queue").apply()
        scope.launch {
            kotlinx.coroutines.delay(4000)   // let the library load first, so files that are already on the phone are recognised
            runCatching {
                val a = org.json.JSONArray(raw)
                for (i in 0 until a.length()) {
                    val x = a.getJSONObject(i)
                    val kind = runCatching { OnlineKind.valueOf(x.optString("k")) }.getOrDefault(OnlineKind.Link)
                    val item = OnlineItem(kind, x.optString("t"), x.optString("a"), x.optString("th").ifBlank { null }, x.optLong("d"), x.optString("u"))
                    val ids = x.optJSONArray("o"); val extra = ids?.let { if (it.length() > 0) it.optLong(0) else null }
                    withContext(Dispatchers.Main) { downloadNow(listOf(item), x.optString("p").ifBlank { null }, listOf(extra)) }
                }
            }
        }
    }

    // ---------- never twice: a second tap while the first is still looking for a source (or already downloading) does nothing ----------
    /** Keys of songs whose download is being prepared right now (before it becomes a job). Compose state, so the player chip can show it at once. */
    val pending = androidx.compose.runtime.mutableStateListOf<String>()
    /** Set by the app: the id of the copy of this song that is already on the phone (the same file, or a song with the same title and artist), or null. */
    var alreadySaved: (OnlineItem, Long?) -> Long? = { _, _ -> null }
    /** Set by the app: called for a song that was already there, so it can be linked and put in the chosen playlist instead of downloading it again. */
    var onAlreadyHave: (OnlineItem, Long?, Long, String?) -> Unit = { _, _, _, _ -> }
    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
    fun keysOf(item: OnlineItem, extraId: Long? = null): Set<String> = buildSet {
        if (item.url.isNotBlank()) add("id:" + Online.idFor(item.url))
        extraId?.let { add("id:$it") }
        add("n:" + norm(item.title + item.subtitle.substringBefore(" • ")))
    }
    private fun jobKeys(j: DlJob): Set<String> = j.origIds.map { "id:$it" }.toSet() + ("n:" + norm(j.title + j.artist))
    fun isBusyWith(keys: Set<String>) = keys.any { it in pending } || jobs.any { it.active && jobKeys(it).any { k -> k in keys } }
    fun pendingFor(songId: Long) = ("id:$songId") in pending
    val activeCount get() = jobs.count { it.active }
    fun avgProgress(): Float = jobs.filter { it.active }.let { l -> if (l.isEmpty()) 0f else l.map { it.progress }.average().toFloat() }

    // ---------- what can be downloaded, and in which qualities ----------
    suspend fun options(item: OnlineItem): List<DlOption> = withContext(Dispatchers.IO) {
        when (item.kind) {
            OnlineKind.Radio -> emptyList()                       // a live stream has no end
            OnlineKind.Quran, OnlineKind.Archive, OnlineKind.Link, OnlineKind.Audius -> {
                val ext = item.url.substringBefore('?').substringAfterLast('.', "mp3").lowercase().let { if (it.length in 2..4) it else "mp3" }
                listOf(DlOption("Original file (${ext.uppercase()})", item.url, ext, item.url.contains(".m3u8")))
            }
            else -> {
                val info = StreamInfo.getInfo(item.url)
                info.audioStreams.filter { it.isUrl && !it.content.isNullOrBlank() }
                    .sortedWith(compareByDescending<org.schabi.newpipe.extractor.stream.AudioStream> { it.format?.suffix == "m4a" || it.format?.suffix == "mp3" }.thenByDescending { it.averageBitrate })
                    .map {
                        val ext = it.format?.suffix ?: "m4a"
                        val hls = it.deliveryMethod.name == "HLS"
                        val br = if (it.averageBitrate > 0) "${it.averageBitrate} kbps" else "unknown bitrate"
                        DlOption("${ext.uppercase()} • $br" + if (ext == "webm" || ext == "opus") " (may not show in the library)" else "", it.content, ext, hls)
                    }
                    .ifEmpty { throw java.io.IOException("No downloadable audio found (the source may be blocking this network)") }
            }
        }
    }

    /** The stream the app itself would play for this item, as a download option. */
    private suspend fun asPlayed(item: OnlineItem): DlOption {
        val url = Online.resolve(item).uri.toString()
        val ext = url.substringBefore('?').substringAfterLast('.', "").lowercase().let { if (it.length in 2..4 && it.all(Char::isLetterOrDigit)) it else "m4a" }
        return DlOption("As played in the app", url, ext, url.contains(".m3u8"))
    }

    /**
     * What a one-tap download uses: the best quality the source lists; if it lists none (or refuses), the stream the app plays;
     * if that fails too, the same song from another source (SoundCloud / Audius). Null only when nothing worked.
     */
    suspend fun best(item: OnlineItem): DlOption? {
        if (item.kind == OnlineKind.Radio) return null
        runCatching { options(item).firstOrNull() }.getOrNull()?.let { return it }
        runCatching { asPlayed(item) }.getOrNull()?.let { return it }
        val alt = runCatching { Online.alternative(item) }.getOrNull() ?: return null
        return runCatching { options(alt).firstOrNull() }.getOrNull() ?: runCatching { asPlayed(alt) }.getOrNull()
    }

    /** One tap: no questions asked. Starts every item in the background and says what happened. */
    fun downloadNow(items: List<OnlineItem>, playlistId: String? = null, extraIds: List<Long?> = emptyList()) {
        if (items.isEmpty()) return
        if (blockedByWifiOnly()) return
        // drop what is already downloading / saved, so repeated taps cannot start the same file again
        val fresh = ArrayList<Triple<OnlineItem, Long?, Set<String>>>()
        var busy = 0; var saved = 0; var addedToList = 0
        items.forEachIndexed { idx, it0 ->
            val extra = extraIds.getOrNull(idx)
            val keys = keysOf(it0, extra)
            val have = alreadySaved(it0, extra)
            when {
                have != null -> { saved++; onAlreadyHave(it0, extra, have, playlistId); if (playlistId != null) addedToList++ }
                isBusyWith(keys) -> busy++
                else -> { pending.addAll(keys); fresh.add(Triple(it0, extra, keys)) }
            }
        }
        if (fresh.isEmpty()) {
            toast(when { busy > 0 -> "Already downloading — see Downloads"; addedToList > 0 -> "Already on your phone — added to the playlist"; else -> "Already on your phone" })
            return
        }
        toast(if (fresh.size == 1) "Looking for the best source…" else "Starting ${fresh.size} downloads…" + if (busy + saved > 0) " (${busy + saved} skipped: already there)" else "")
        scope.launch {
            var ok = 0
            val failed = ArrayList<String>()
            for ((it0, extra, keys) in fresh) {
                try {
                    val o = best(it0)
                    if (o == null) { failed.add(it0.title); continue }
                    withContext(Dispatchers.Main) { enqueue(it0.title, it0.subtitle.substringBefore(" • "), o, playlistId, it0, listOfNotNull(extra)) }
                    ok++
                } finally { withContext(Dispatchers.Main) { keys.forEach { pending.remove(it) } } }
            }
            toast(when {
                failed.isEmpty() -> if (ok == 1) "Downloading — see Downloads" else "Downloading $ok files — see Downloads"
                ok == 0 -> "Couldn't find a downloadable source for " + failed.first().take(40) + if (failed.size > 1) " (+${failed.size - 1} more)" else ""
                else -> "Downloading $ok, ${failed.size} had no source"
            })
        }
    }

    private fun toast(msg: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post { android.widget.Toast.makeText(ctx, com.sudosoc.sudoplay.ui.Tr.t(msg), android.widget.Toast.LENGTH_LONG).show() }
    }

    /** "Download only on Wi-Fi" (Settings → Online). True = refused, and the user was told. */
    fun blockedByWifiOnly(): Boolean {
        val wifiOnly = ctx.getSharedPreferences("sudoplay", Context.MODE_PRIVATE).getBoolean("dl_wifi_only", false)
        if (!wifiOnly) return false
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        if (!cm.isActiveNetworkMetered) return false
        toast("Downloads are set to Wi-Fi only — connect to Wi-Fi, or turn that off in Settings → Online")
        return true
    }

    fun enqueue(title: String, artist: String, option: DlOption, playlistId: String?, item: OnlineItem? = null, extraIds: List<Long> = emptyList()) {
        // backup guard for the "choose quality" path: the same song is already running
        if (jobs.any { it.active && norm(it.title + it.artist) == norm(title + artist) }) { toast("Already downloading “${title.take(40)}”"); return }
        val job = DlJob(next++, title, artist, option, playlistId)
        if (item != null) {
            job.srcUrl = item.url; job.srcKind = item.kind.name
            job.thumb = item.thumb; job.durSec = item.durationSec
            job.origIds = (listOf(Online.idFor(item.url)).filter { item.url.isNotBlank() } + extraIds).distinct()
        } else job.origIds = extraIds
        jobs.add(0, job)
        persist()
        // the notification is a bonus: if the system refuses to start the service, the download itself still runs
        runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, DownloadService::class.java)) }
        job.job = scope.launch {
            gate.withPermit {
                try {
                    job.state = "Downloading"
                    val uri = if (option.hls) saveHls(job) else saveProgressive(job)
                    job.songId = ContentUris.parseId(uri).takeIf { Build.VERSION.SDK_INT >= 29 } ?: -1
                    job.progress = 1f; job.state = "Done"
                    withContext(Dispatchers.Main) { onSaved(job) }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    job.state = "Cancelled"
                } catch (e: Throwable) {
                    job.state = "Failed"; job.error = com.sudosoc.sudoplay.online.Friendly.of(e)
                } finally { persist() }
            }
        }
    }

    fun cancel(j: DlJob) { j.cancelled = true; j.job?.cancel(); if (j.active) j.state = "Cancelled"; persist() }
    fun clearFinished() { jobs.removeAll { !it.active } }

    // ---------- output file (MediaStore, so the library sees it) ----------
    private class Out(val uri: Uri, val stream: OutputStream, val finish: () -> Unit, val abort: () -> Unit)

    private fun mimeFor(ext: String) = when (ext) { "m4a", "mp4" -> "audio/mp4"; "mp3" -> "audio/mpeg"; "webm" -> "audio/webm"; "opus", "ogg" -> "audio/ogg"; "aac" -> "audio/aac"; else -> "audio/mpeg" }
    private fun safe(s: String) = s.replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), " ").replace(Regex("\\s+"), " ").trim().take(90).ifBlank { "audio" }

    private fun open(j: DlJob): Out {
        val ext = j.option.ext
        val name = safe(if (j.artist.isNotBlank()) "${j.title} - ${j.artist}" else j.title) + "." + ext
        if (Build.VERSION.SDK_INT >= 29) {
            val cv = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                put(MediaStore.Audio.Media.MIME_TYPE, mimeFor(ext))
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/SUDOPLAY")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), cv) ?: throw java.io.IOException("Could not create the file")
            val os = ctx.contentResolver.openOutputStream(uri) ?: throw java.io.IOException("Could not open the file")
            return Out(uri, os, {
                ctx.contentResolver.update(uri, ContentValues().apply {
                    put(MediaStore.Audio.Media.IS_PENDING, 0)
                }, null, null)
                // after the scan the title/artist come from the file name; set the nice ones
                runCatching {
                    ctx.contentResolver.update(uri, ContentValues().apply {
                        put(MediaStore.Audio.Media.TITLE, j.title)
                        if (j.artist.isNotBlank()) put(MediaStore.Audio.Media.ARTIST, j.artist)
                        put(MediaStore.Audio.Media.ALBUM, "SUDOPLAY")
                    }, null, null)
                }
            }, { runCatching { ctx.contentResolver.delete(uri, null, null) } })
        }
        val dir = java.io.File(ctx.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "SUDOPLAY").apply { mkdirs() }
        val f = java.io.File(dir, name)
        return Out(Uri.fromFile(f), f.outputStream(), {}, { f.delete() })
    }

    internal fun absUrl(base: String, ref: String): String =
        if (ref.startsWith("http://") || ref.startsWith("https://")) ref
        else runCatching { java.net.URI(base).resolve(ref).toString() }.getOrElse { base.substringBeforeLast('/') + "/" + ref }

    /** Same client, but a dead connection (Wi-Fi ⇄ mobile data) is noticed within 20 s instead of hanging. */
    private val dl by lazy { Online.http.newBuilder().readTimeout(20, TimeUnit.SECONDS).retryOnConnectionFailure(true).build() }
    private fun req(url: String, from: Long = 0) = okhttp3.Request.Builder().url(url).header("User-Agent", UA).apply { if (from > 0) header("Range", "bytes=$from-") }.build()
    private fun get(url: String) = dl.newCall(req(url)).execute()
    private fun log(url: String, code: Int, bytes: Long, ms: Long) {
        val u = Uri.parse(url)
        NetLog.add(NetEntry(System.currentTimeMillis(), "GET", u.host ?: "?", (u.path ?: "/").take(60), code, bytes, ms))
    }

    private class Stale(val code: Int) : java.io.IOException("HTTP $code")

    /** null = the connection is fine; otherwise why we are waiting. */
    internal fun waitingFor(wifiOnly: Boolean, internet: Boolean, unmetered: Boolean): String? = when {
        !internet -> "Waiting for network"
        wifiOnly && !unmetered -> "Waiting for Wi-Fi"
        else -> null
    }
    private fun netReady(): String? {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val c = cm.getNetworkCapabilities(cm.activeNetwork)
        val wifiOnly = ctx.getSharedPreferences("sudoplay", Context.MODE_PRIVATE).getBoolean("dl_wifi_only", false)
        return waitingFor(wifiOnly, c?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true, c?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true)
    }
    private fun ensure(j: DlJob) { if (j.cancelled || Thread.currentThread().isInterrupted) throw kotlinx.coroutines.CancellationException() }
    /** Pauses (without failing) until the connection is usable again — e.g. while the phone switches from mobile data to Wi-Fi. */
    private fun awaitNet(j: DlJob) {
        var why = netReady() ?: return
        while (true) {
            ensure(j); j.state = why
            Thread.sleep(1000)
            why = netReady() ?: break
        }
        j.state = "Downloading"
    }

    /** One small file (playlist / fragment) with retries and the same network waiting. */
    private fun bytesRetry(j: DlJob, url: String): ByteArray {
        var fails = 0
        while (true) {
            awaitNet(j); ensure(j)
            try {
                get(url).use { r ->
                    if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
                    return r.body?.bytes() ?: ByteArray(0)
                }
            } catch (e: java.io.IOException) {
                if (netReady() != null) continue
                if (++fails > 5) throw e
                Thread.sleep(800L * fails)
            }
        }
    }

    /** Downloads straight to the file. If the connection drops it carries on from the byte it reached; if the link expires it starts again with a fresh one. */
    private fun saveProgressive(j: DlJob): Uri {
        var out = open(j)
        var done = 0L; var total = -1L; var fails = 0; var restarts = 0
        var url = j.option.url
        try {
            while (true) {
                awaitNet(j); ensure(j)
                val before = done
                try {
                    val t0 = System.currentTimeMillis()
                    dl.newCall(req(url, done)).execute().use { r ->
                        if (r.code == 416 && done > 0) return@use                      // nothing left to fetch: it was already complete
                        if (r.code == 403 || r.code == 410 || r.code == 404) throw Stale(r.code)
                        if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code} — the source refused the download")
                        val body = r.body ?: throw java.io.IOException("Empty response")
                        if (total < 0) total = if (r.code == 206) (r.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull() ?: -1L) else body.contentLength()
                        var skip = if (done > 0 && r.code == 200) done else 0L           // the server ignored "Range": skip what we already have
                        val buf = ByteArray(64 * 1024)
                        body.byteStream().use { ins ->
                            while (true) {
                                val n = ins.read(buf); if (n < 0) break
                                var off = 0
                                if (skip > 0) { val k = minOf(skip, n.toLong()).toInt(); skip -= k; off = k }
                                if (n > off) { out.stream.write(buf, off, n - off); done += (n - off) }
                                if (total > 0) j.progress = (done.toFloat() / total).coerceAtMost(0.99f)
                                ensure(j)
                            }
                        }
                        if (total > 0 && done < total) throw java.io.IOException("Connection closed early")
                        log(url, r.code, done, System.currentTimeMillis() - t0)
                    }
                    break
                } catch (e: kotlinx.coroutines.CancellationException) { throw e
                } catch (e: Stale) {
                    // the link stopped working (often right after the network changed): fetch a fresh one and begin again
                    if (j.srcUrl.isBlank() || ++restarts > 3) throw e
                    val fresh = kotlinx.coroutines.runBlocking { best(OnlineItem(runCatching { OnlineKind.valueOf(j.srcKind) }.getOrDefault(OnlineKind.Link), j.title, j.artist, j.thumb, j.durSec, j.srcUrl)) }
                        ?: throw e
                    if (fresh.hls || fresh.ext != j.option.ext) throw e
                    runCatching { out.stream.close() }; out.abort(); out = open(j); done = 0; total = -1; url = fresh.url
                } catch (e: java.io.IOException) {
                    ensure(j)
                    if (netReady() != null) continue                                   // no connection right now → wait, don't count it as a failure
                    fails = if (done > before) 1 else fails + 1
                    if (fails > 6) throw e
                    Thread.sleep(700L * fails)
                }
            }
            runCatching { out.stream.close() }
            out.finish(); return out.uri
        } catch (t: Throwable) { runCatching { out.stream.close() }; out.abort(); throw t }
    }

    /** HLS (what SoundCloud serves): fetch the playlist, then every fragment in order — init segment + fragments make a normal .m4a. */
    private fun saveHls(j: DlJob): Uri {
        var base = j.option.url
        var text = String(bytesRetry(j, base))
        if (text.contains("#EXT-X-STREAM-INF")) {                     // master playlist → first variant
            val v = text.lines().first { it.isNotBlank() && !it.startsWith("#") }
            base = absUrl(base, v)
            text = String(bytesRetry(j, base))
        }
        if (text.contains("#EXT-X-KEY") && !text.contains("METHOD=NONE")) throw java.io.IOException("This stream is encrypted and cannot be saved")
        val parts = ArrayList<String>()
        Regex("#EXT-X-MAP:URI=\"([^\"]+)\"").find(text)?.let { parts.add(absUrl(base, it.groupValues[1])) }
        text.lines().filter { it.isNotBlank() && !it.startsWith("#") }.forEach { parts.add(absUrl(base, it.trim())) }
        if (parts.isEmpty()) throw java.io.IOException("Empty playlist")
        val out = open(j)
        try {
            val t0 = System.currentTimeMillis(); var bytes = 0L
            out.stream.use { os ->
                parts.forEachIndexed { i, u ->
                    val b = bytesRetry(j, u); os.write(b); bytes += b.size
                    j.progress = ((i + 1f) / parts.size).coerceAtMost(0.99f)
                    ensure(j)
                }
            }
            log(j.option.url, 200, bytes, System.currentTimeMillis() - t0)
            out.finish(); return out.uri
        } catch (t: Throwable) { runCatching { out.stream.close() }; out.abort(); throw t }
    }
}
