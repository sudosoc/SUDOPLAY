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
    internal var job: Job? = null
    val active get() = state == "Queued" || state == "Downloading"
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

    fun enqueue(title: String, artist: String, option: DlOption, playlistId: String?) {
        val job = DlJob(next++, title, artist, option, playlistId)
        jobs.add(0, job)
        ContextCompat.startForegroundService(ctx, Intent(ctx, DownloadService::class.java))
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
                    job.state = "Failed"; job.error = (e.message ?: e.javaClass.simpleName).take(140)
                }
            }
        }
    }

    fun cancel(j: DlJob) { j.job?.cancel(); if (j.active) j.state = "Cancelled" }
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

    private fun get(url: String) = http.newCall(okhttp3.Request.Builder().url(url).header("User-Agent", UA).build()).execute()
    private fun log(url: String, code: Int, bytes: Long, ms: Long) {
        val u = Uri.parse(url)
        NetLog.add(NetEntry(System.currentTimeMillis(), "GET", u.host ?: "?", (u.path ?: "/").take(60), code, bytes, ms))
    }

    private fun saveProgressive(j: DlJob): Uri {
        val out = open(j)
        try {
            val t0 = System.currentTimeMillis()
            get(j.option.url).use { r ->
                if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code} — the source refused the download")
                val body = r.body ?: throw java.io.IOException("Empty response")
                val total = body.contentLength()
                var done = 0L
                out.stream.use { os ->
                    body.byteStream().use { ins ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = ins.read(buf); if (n < 0) break
                            os.write(buf, 0, n); done += n
                            if (total > 0) j.progress = (done.toFloat() / total).coerceAtMost(0.99f)
                            if (Thread.currentThread().isInterrupted || j.state == "Cancelled") throw kotlinx.coroutines.CancellationException()
                        }
                    }
                }
                log(j.option.url, r.code, done, System.currentTimeMillis() - t0)
            }
            out.finish(); return out.uri
        } catch (t: Throwable) { runCatching { out.stream.close() }; out.abort(); throw t }
    }

    /** HLS (what SoundCloud serves): fetch the playlist, then every fragment in order — init segment + fragments make a normal .m4a. */
    private fun saveHls(j: DlJob): Uri {
        var base = j.option.url
        var text = get(base).use { it.body?.string() ?: "" }
        if (text.contains("#EXT-X-STREAM-INF")) {                     // master playlist → first variant
            val v = text.lines().first { it.isNotBlank() && !it.startsWith("#") }
            base = absUrl(base, v)
            text = get(base).use { it.body?.string() ?: "" }
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
                    get(u).use { r ->
                        if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code} on fragment ${i + 1}")
                        val b = r.body?.bytes() ?: ByteArray(0); os.write(b); bytes += b.size
                    }
                    j.progress = ((i + 1f) / parts.size).coerceAtMost(0.99f)
                    if (Thread.currentThread().isInterrupted || j.state == "Cancelled") throw kotlinx.coroutines.CancellationException()
                }
            }
            log(j.option.url, 200, bytes, System.currentTimeMillis() - t0)
            out.finish(); return out.uri
        } catch (t: Throwable) { runCatching { out.stream.close() }; out.abort(); throw t }
    }
}
