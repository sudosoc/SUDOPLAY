package com.sudosoc.sudoplay

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.os.bundleOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.sudosoc.sudoplay.data.AppSettings
import com.sudosoc.sudoplay.data.CoverStore
import com.sudosoc.sudoplay.data.EqPresets
import com.sudosoc.sudoplay.data.FxScene
import com.sudosoc.sudoplay.data.FxScenes
import com.sudosoc.sudoplay.data.FxState
import com.sudosoc.sudoplay.data.Haptics
import com.sudosoc.sudoplay.data.Lyrics
import com.sudosoc.sudoplay.data.LyricsFetcher
import com.sudosoc.sudoplay.data.LyricsLoader
import com.sudosoc.sudoplay.data.MusicRepository
import com.sudosoc.sudoplay.data.PlaylistStore
import com.sudosoc.sudoplay.data.Smart
import com.sudosoc.sudoplay.data.Song
import com.sudosoc.sudoplay.playback.Analyzer
import com.sudosoc.sudoplay.playback.FxBus
import com.sudosoc.sudoplay.playback.PlaybackService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context = app.applicationContext
    private val repo = MusicRepository(ctx)
    private val prefs = ctx.getSharedPreferences("sudoplay", Context.MODE_PRIVATE)
    val store = PlaylistStore(ctx)
    /** Online songs that live in playlists, and which of them are now also on the phone. */
    val olib = com.sudosoc.sudoplay.data.OnlineLibrary(ctx)

    // ---- settings / theme ----
    val settings = AppSettings(prefs)
    val themeId: String get() = settings.theme.value
    val haptics = Haptics(ctx) { settings.hapticLevel.value }

    // ---- library ----
    /** Everything MediaStore returned (before hidden folders are applied). */
    var rawSongs by mutableStateOf<List<Song>>(emptyList()); private set
    var songs by mutableStateOf<List<Song>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    private var byId: Map<Long, Song> = emptyMap()
    private var refreshJob: Job? = null
    private var restored = false
    private var abSong = -1L
    private var mixSeed = System.currentTimeMillis()
    fun newMix() { mixSeed = System.nanoTime(); mixTick++ }
    var mixTick by mutableIntStateOf(0); private set

    // ---- playback state ----
    var current by mutableStateOf<Song?>(null); private set
    var isPlaying by mutableStateOf(false); private set
    var position by mutableLongStateOf(0L); private set
    var duration by mutableLongStateOf(0L); private set
    var shuffle by mutableStateOf(false); private set
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF); private set
    var speed by mutableFloatStateOf(1f); private set
    var queue by mutableStateOf<List<Song>>(emptyList()); private set
    var queueIndex by mutableIntStateOf(0); private set
    var sleepEndsAt by mutableLongStateOf(0L); private set
    var sleepEndOfSong by mutableStateOf(false); private set
    private var sleepSongId = -1L

    // ---- analysis (loudness gain + real waveform) ----
    var wave by mutableStateOf<FloatArray?>(null); private set
    private var waveFor = -1L
    private val analysisLock = Mutex()
    private val analysing = HashSet<Long>()
    private fun analyzeSong(s: Song, forCurrent: Boolean) {
        if (s.online) return
        if (!analysing.add(s.id)) return
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { analysisLock.withLock { Analyzer.analyze(ctx, s.id, s.uri, s.durationMs) } }
            analysing.remove(s.id)
            if (r == null) return@launch
            FxBus.gains[s.id.toString()] = r.gainDb
            if (forCurrent && current?.id == s.id) wave = r.peaks.takeIf { it.isNotEmpty() }
        }
    }

    // ---- lyrics ----
    var lyrics by mutableStateOf<Lyrics?>(null); private set
    private var lyricsFor = -1L
    var lyricsBusy by mutableStateOf(false); private set

    /** The user asked for it (button in the lyrics view): look the current song up online right now. */
    fun findLyricsOnline() {
        val cur = current ?: return
        if (lyricsBusy) return
        lyricsBusy = true
        viewModelScope.launch {
            val l = withContext(Dispatchers.IO) { runCatching { LyricsFetcher.fetch(ctx, cur, force = true) }.getOrNull() }
            lyricsBusy = false
            if (l != null && lyricsFor == cur.id) lyrics = l
        }
    }

    // ---- play stats (drive the smart lists) ----
    var plays by mutableStateOf(parsePlays(prefs.getString("plays", "") ?: "")); private set
    var recent by mutableStateOf(
        (prefs.getString("recent", "") ?: "").split(",").mapNotNull { it.toLongOrNull() }
    ); private set
    private var lastCounted = -1L

    // ---- sound engine ----
    var fx by mutableStateOf(FxState.load(prefs)); private set
    var userScenes by mutableStateOf(FxScenes.loadUser(prefs)); private set
    var eqBands by mutableIntStateOf(0); private set
    var eqMin by mutableIntStateOf(-1500); private set
    var eqMax by mutableIntStateOf(1500); private set
    var eqFreqs by mutableStateOf<List<Int>>(emptyList()); private set

    // ---- car mode (full-screen landscape UI for the head unit) ----
    var carMode by mutableStateOf(false)
    var carChecked = false

    // ---- video clip maker ----
    var clipSong by mutableStateOf<Song?>(null)

    // ---- open-player request (notification tap) ----
    var wantPlayer by mutableStateOf(false); private set
    fun requestPlayer() { wantPlayer = true; viewModelScope.launch { delay(4000); wantPlayer = false } }
    fun playerOpened() { wantPlayer = false }

    private val readyActions = mutableListOf<() -> Unit>()
    /** Runs now if the library + player are ready, otherwise right after they are. */
    fun whenReady(a: () -> Unit) { if (controller != null && songs.isNotEmpty()) a() else readyActions += a }
    private fun flushReady() { if (controller != null && songs.isNotEmpty() && readyActions.isNotEmpty()) { val l = readyActions.toList(); readyActions.clear(); l.forEach { it() } } }
    fun appContext() = ctx
    // ---- online (YouTube / SoundCloud / radio / links) ----
    init {
        // streaming quality: "Auto" streams the best on Wi-Fi and a lighter file on mobile data; "Data saver" always a small one
        com.sudosoc.sudoplay.online.Online.capKbps = {
            when (settings.streamQuality.value) {
                1 -> 0
                2 -> 70
                else -> if ((ctx.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).isActiveNetworkMetered) 128 else 0
            }
        }
    }

    init {
        com.sudosoc.sudoplay.online.DownloadCenter.alreadySaved = { item, extra -> localMatch(item, extra)?.id }
        com.sudosoc.sudoplay.online.DownloadCenter.onAlreadyHave = { _, _, localId, playlistId -> playlistId?.let { store.addSong(it, localId) } }
        com.sudosoc.sudoplay.online.Online.attach(ctx)
        store.onAdd = { sid -> rememberOnline(sid) }
        com.sudosoc.sudoplay.online.DownloadCenter.attach(ctx) { job ->
            job.playlistId?.let { if (job.songId > 0) store.addSong(it, job.songId) }
            if (job.songId > 0) { job.origIds.forEach { olib.mapLocal(it, job.songId) }; saveExtras(job) }
            refreshLibrary()
        }
        com.sudosoc.sudoplay.online.DownloadCenter.restore()   // unfinished downloads from last time carry on
    }

    // ---- "is this song already on the phone?" — same file, or the same title + artist (+ length) in the library ----
    private var matchSrc: List<Song>? = null
    private val matchIdx = HashMap<String, MutableList<Song>>()
    private fun normTitle(s: String) = com.sudosoc.sudoplay.online.TitleKey.of(s)
    fun localMatch(item: com.sudosoc.sudoplay.online.OnlineItem, extra: Long? = null): Song? {
        listOfNotNull(extra, item.url.takeIf { it.isNotBlank() }?.let { com.sudosoc.sudoplay.online.Online.idFor(it) }).forEach { id ->
            olib.localFor(id)?.let { l -> byId[l] }?.let { return it }
        }
        if (rawSongs !== matchSrc) {
            matchIdx.clear(); rawSongs.forEach { matchIdx.getOrPut(normTitle(it.title)) { ArrayList() }.add(it) }; matchSrc = rawSongs
        }
        val artist = lyricArtist(item.subtitle.substringBefore(" • "))
        val key = normTitle(lyricTitle(item.title, artist))
        if (key.length < 3) return null
        val at = com.sudosoc.sudoplay.online.OnlineMatch.tokens(artist)
        val dur = item.durationSec
        val hit = matchIdx[key]?.firstOrNull { c ->
            val ca = com.sudosoc.sudoplay.online.OnlineMatch.tokens(c.artist)
            val artistOk = at.isEmpty() || c.artist.isBlank() || c.artist.equals("<unknown>", true) || ca.any { it in at }
            val durOk = dur <= 0 || c.durationMs <= 0 || kotlin.math.abs(c.durationMs / 1000 - dur) <= 6
            artistOk && durOk
        } ?: return null
        // remember the link, so rows show "on this phone" and it keeps working even if the library is rescanned
        if (item.url.isNotBlank()) olib.mapLocal(com.sudosoc.sudoplay.online.Online.idFor(item.url), hit.id)
        if (extra != null && extra < 0) olib.mapLocal(extra, hit.id)
        return hit
    }

    /** Add to a playlist; an online song that is already on the phone is added as the phone's file. Returns (playlist name, was already in it). */
    fun addToPlaylist(pid: String, song: Song): Pair<String, Boolean> {
        val p = store.get(pid) ?: return "" to false
        val id = if (song.online) localMatch(itemOf(song), song.id)?.id ?: song.id else song.id
        val already = id in p.songIds || song.id in p.songIds
        if (!already) store.addSong(pid, id)
        return p.name to already
    }

    // ---- online songs inside playlists ----
    /** A playlist / favorites id → something we can show: the phone's file, the downloaded copy of an online song, or the online song itself. */
    fun entrySong(id: Long): Song? {
        byId[id]?.let { return it }
        olib.localFor(id)?.let { l -> byId[l]?.let { return it } }
        return olib.get(id)?.let { olib.stub(it) }
    }
    fun playlistSongs(id: String): List<Song> = (store.get(id)?.songIds ?: emptyList()).mapNotNull(::entrySong).distinctBy { it.id }
    /** Where a resolved online song came from when it was played from a "name only" entry. */
    private val stubOf = HashMap<Long, Long>()
    private fun entryItem(e: com.sudosoc.sudoplay.data.OnlineEntry) = com.sudosoc.sudoplay.online.OnlineItem(
        runCatching { com.sudosoc.sudoplay.online.OnlineKind.valueOf(e.kind) }.getOrDefault(com.sudosoc.sudoplay.online.OnlineKind.Link),
        e.title, e.artist, e.thumb, e.durationSec, e.url)

    /** Called when an online song is added to a playlist / favorites: keeps what is needed to list and replay it. */
    private fun rememberOnline(sid: Long) {
        if (sid >= 0 || olib.get(sid) != null) return
        val s = onlineSongs[sid] ?: queue.firstOrNull { it.id == sid } ?: current?.takeIf { it.id == sid } ?: return
        val origin = onlineOrigin[sid]
        olib.put(com.sudosoc.sudoplay.data.OnlineEntry(
            sid, origin?.kind?.name ?: "Link", s.title, s.artist, s.artUrl, s.durationMs / 1000,
            origin?.url ?: s.uri.toString(),
        ))
    }

    private fun onlineSources(): List<com.sudosoc.sudoplay.online.OnlineKind> {
        val on = (prefs.getString("online_src", null) ?: "").split(",").toSet()
        val l = listOf(com.sudosoc.sudoplay.online.OnlineKind.YouTube, com.sudosoc.sudoplay.online.OnlineKind.SoundCloud, com.sudosoc.sudoplay.online.OnlineKind.Audius)
        return if (on.size <= 1) l else l.filter { it.name in on }.ifEmpty { l }
    }

    /** The playable online item for a stored entry (searching by name when the entry has no link yet, and remembering the find). */
    private suspend fun itemFor(e: com.sudosoc.sudoplay.data.OnlineEntry): com.sudosoc.sudoplay.online.OnlineItem? {
        if (!e.needsSearch) return entryItem(e)
        val found = com.sudosoc.sudoplay.online.Online.findFor(e.title, e.artist, e.durationSec, onlineSources()) ?: return null
        olib.put(com.sudosoc.sudoplay.data.OnlineEntry(e.id, found.kind.name, e.title, e.artist, e.thumb ?: found.thumb, if (e.durationSec > 0) e.durationSec else found.durationSec, found.url))
        return found
    }

    /** Resolve one online item to a playable Song, trying another source when YouTube refuses. */
    private suspend fun resolveOnline(item: com.sudosoc.sudoplay.online.OnlineItem, allowAlt: Boolean = true): Song {
        val on = com.sudosoc.sudoplay.online.Online
        val s = try { on.resolve(item) } catch (e: Throwable) {
            val alt = if (item.kind == com.sudosoc.sudoplay.online.OnlineKind.YouTube && allowAlt) on.alternative(item) else null
            if (alt == null) throw e
            onlineNote = "YouTube would not give the audio, so this is the ${alt.kind.label} version"
            onlineOrigin[on.idFor(alt.url)] = alt
            on.resolve(alt)
        }
        onlineSongs[s.id] = s
        onlineOrigin.putIfAbsent(s.id, item)
        return s
    }

    /** A playable copy of any song: local ones as they are, online "stubs" resolved fresh (stream links expire). */
    private suspend fun materialize(song: Song): Song? {
        if (!song.online) return song
        val e = olib.get(song.id) ?: return song
        olib.localFor(song.id)?.let { l -> byId[l]?.let { return it } }
        val item = itemFor(e) ?: return null
        return runCatching { resolveOnline(item) }.getOrNull()?.also { if (it.id != song.id) stubOf[it.id] = song.id }
    }

    private var mixJob: Job? = null
    /** Play a list that may contain online songs: the first one starts as soon as it resolves, the rest are queued behind it in order. */
    /** True when the user turned Offline mode on, or the phone simply has no internet: online-only songs cannot play. */
    val offline: Boolean get() = settings.offlineMode.value || !com.sudosoc.sudoplay.data.NetState.online

    fun playList(list: List<Song>, index: Int, shuffleOn: Boolean = false) {
        if (list.isEmpty()) return
        if (offline && list.any { it.online }) {
            val playable = list.filter { !it.online }
            if (playable.isEmpty()) { android.widget.Toast.makeText(ctx, com.sudosoc.sudoplay.ui.Tr.t("Nothing here is available offline"), android.widget.Toast.LENGTH_SHORT).show(); return }
            val cur = list.getOrNull(index)
            playList(playable, playable.indexOfFirst { it.id == cur?.id }.coerceAtLeast(0), shuffleOn)
            return
        }
        if (list.none { it.online }) { play(list, index, if (shuffleOn) true else null); return }
        val at = index.coerceIn(0, list.lastIndex)
        val order = if (shuffleOn) list.shuffled() else list.drop(at) + list.take(at)
        mixJob?.cancel()
        mixJob = viewModelScope.launch {
            var started = false
            var failed = 0
            for (s in order) {
                val m = withContext(Dispatchers.IO) { materialize(s) }
                if (m == null) { failed++; continue }
                val c = controller ?: continue
                if (!started) { playInternal(listOf(m), 0, null); started = true }
                else c.addMediaItem(toItem(m))
            }
            if (!started) onlineError = "None of these songs could be played right now (the sources may be blocking this network)"
            else if (failed > 0) onlineNote = "$failed song${if (failed == 1) "" else "s"} could not be found and were skipped"
        }
    }

    /** Download an online song (or every online song of a list): finds the source by name when needed. */
    fun downloadSongs(list: List<Song>) {
        val todo = list.filter { it.online && olib.localFor(it.id) == null }
        if (todo.isEmpty()) { android.widget.Toast.makeText(ctx, com.sudosoc.sudoplay.ui.Tr.t("Already on your phone"), android.widget.Toast.LENGTH_SHORT).show(); return }
        if (com.sudosoc.sudoplay.online.DownloadCenter.blockedByWifiOnly()) return
        android.widget.Toast.makeText(ctx, if (todo.size == 1) "Looking for the best source…" else "Preparing ${todo.size} songs…", android.widget.Toast.LENGTH_SHORT).show()
        viewModelScope.launch {
            val items = ArrayList<com.sudosoc.sudoplay.online.OnlineItem>(); val ids = ArrayList<Long?>()
            for (s in todo) {
                val e = olib.get(s.id)
                val item = withContext(Dispatchers.IO) {
                    runCatching { if (e != null) itemFor(e) else itemOf(s) }.getOrNull()
                }
                if (item != null) { items += item; ids += s.id }
            }
            if (items.isEmpty()) android.widget.Toast.makeText(ctx, com.sudosoc.sudoplay.ui.Tr.t("Couldn't find a source for these songs"), android.widget.Toast.LENGTH_LONG).show()
            else com.sudosoc.sudoplay.online.DownloadCenter.downloadNow(items, null, ids)
        }
    }

    /** The song that is playing right now, as something to download (covers songs played from playlist entries too). */
    fun downloadCurrent() {
        val s = current ?: return
        if (!s.online) return
        if (com.sudosoc.sudoplay.online.DownloadCenter.blockedByWifiOnly()) return
        com.sudosoc.sudoplay.online.DownloadCenter.downloadNow(listOf(itemOf(s)), null, listOf(s.id, stubOf[s.id]))
    }
    /**
     * What to download for a song that is playing: the real title, artist, cover and length the player already knows
     * (the item it was found as can be a bare pasted link with no details), with the page link from where it came.
     */
    private fun itemOf(s: Song): com.sudosoc.sudoplay.online.OnlineItem {
        val o = onlineOrigin[s.id]
        val artist = s.artist.takeIf { it.isNotBlank() && it != "Stream" && it != "Radio" && it != "Online" } ?: o?.subtitle.orEmpty()
        return com.sudosoc.sudoplay.online.OnlineItem(
            o?.kind ?: com.sudosoc.sudoplay.online.OnlineKind.Link, s.title.ifBlank { o?.title.orEmpty() }, artist,
            s.artUrl ?: o?.thumb, if (s.durationMs > 0) s.durationMs / 1000 else o?.durationSec ?: 0, o?.url ?: s.uri.toString(),
        )
    }
    /** Local copy of the playing online song, if it has been downloaded. */
    fun localCopyOf(s: Song?): Song? {
        s ?: return null
        if (!s.online) return null
        val ids = listOfNotNull(s.id, stubOf[s.id], onlineOrigin[s.id]?.let { com.sudosoc.sudoplay.online.Online.idFor(it.url) })
        return ids.firstNotNullOfOrNull { olib.localFor(it) }?.let { byId[it] }
    }

    // ---- cover + lyrics saved next to a download ----
    private fun saveExtras(job: com.sudosoc.sudoplay.online.DlJob) {
        if (!settings.dlExtras.value) return
        val id = job.songId
        viewModelScope.launch(Dispatchers.IO) {
            var coverJpeg: ByteArray? = null
            runCatching {
                job.thumb?.let { u ->
                    com.sudosoc.sudoplay.online.Online.fetchBytes(u)?.let { b ->
                        android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size)?.let { bmp ->
                            val side = minOf(bmp.width, bmp.height)
                            val sq = if (bmp.width == bmp.height) bmp else android.graphics.Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
                            CoverStore.saveForId(ctx, id, sq)
                            coverJpeg = java.io.ByteArrayOutputStream().also { o -> sq.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, o) }.toByteArray()
                        }
                    }
                }
            }
            runCatching {
                var artist = lyricArtist(job.artist)
                var title = job.title
                if (artist.isBlank()) Regex("""\s[-–—]\s""").find(title)?.let { m -> artist = title.substring(0, m.range.first).trim(); title = title.substring(m.range.last + 1) }
                val tmp = Song(id, lyricTitle(title, artist), artist, "", 0, job.durSec * 1000, android.net.Uri.EMPTY, 0, 0)
                LyricsFetcher.fetch(ctx, tmp)
            }
            // finally put the name, cover and lyrics inside the file itself, so they travel with it
            runCatching {
                if (id > 0) {
                    val uri = android.content.ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    com.sudosoc.sudoplay.data.TagWriter.write(ctx, uri, null, com.sudosoc.sudoplay.data.Tags(job.title, job.artist, cover = coverJpeg, lyrics = LyricsFetcher.raw(ctx, id)))
                }
            }
        }
    }

    /** Downloaded songs: everything in Music/SUDOPLAY plus whatever a playlist entry was mapped to. */
    fun downloadedSongs(): List<Song> {
        val mapped = olib.localIds()
        return rawSongs.filter { it.folder.contains("SUDOPLAY", true) || it.id in mapped }.sortedByDescending { it.dateAdded }
    }

    /**
     * Fixes a downloaded file that has a wrong or empty name: writes the new title/artist into the file's library entry,
     * then looks up its cover (Deezer) and lyrics (lrclib) by that name.
     */
    fun fixDownload(song: Song, title: String, artist: String, done: (String) -> Unit) {
        viewModelScope.launch {
            val msg = withContext(Dispatchers.IO) {
                val ok = runCatching {
                    ctx.contentResolver.update(song.uri, android.content.ContentValues().apply {
                        put(MediaStore.Audio.Media.TITLE, title)
                        put(MediaStore.Audio.Media.ARTIST, artist)
                    }, null, null) > 0
                }.getOrDefault(false)
                if (!ok) return@withContext "Android did not allow renaming this file"
                var cover = false; var lyr = false
                runCatching {
                    val q = android.net.Uri.encode("$artist $title".trim())
                    val j = org.json.JSONObject(com.sudosoc.sudoplay.online.Online.getJson("https://api.deezer.com/search?q=$q&limit=1"))
                    val url = j.optJSONArray("data")?.optJSONObject(0)?.optJSONObject("album")?.let { it.optString("cover_big").ifBlank { it.optString("cover_medium") } }
                    if (!url.isNullOrBlank()) com.sudosoc.sudoplay.online.Online.fetchBytes(url)?.let { b ->
                        android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size)?.let { CoverStore.saveForId(ctx, song.id, it); cover = true }
                    }
                }
                runCatching {
                    val tmp = Song(song.id, lyricTitle(title, artist), artist, "", 0, song.durationMs, song.uri, 0, 0)
                    lyr = LyricsFetcher.fetch(ctx, tmp, force = true) != null
                }
                "Saved" + (if (cover) " • cover found" else "") + (if (lyr) " • lyrics found" else "")
            }
            refreshLibrary()
            done(msg)
        }
    }

    /** Deletes the files (only ones this app made can be removed without a system prompt); reports how many went. */
    fun deleteDownloads(list: List<Song>, done: (Int) -> Unit) {
        viewModelScope.launch {
            val gone = HashSet<Long>()
            withContext(Dispatchers.IO) {
                for (s in list) {
                    val n = runCatching { ctx.contentResolver.delete(s.uri, null, null) }.getOrDefault(0)
                    if (n > 0) gone += s.id
                }
            }
            if (gone.isNotEmpty()) {
                olib.unmapLocal(gone)
                store.items.toList().forEach { p -> gone.forEach { g -> if (g in p.songIds) store.removeSong(p.id, g) } }
                val c = controller
                if (c != null) for (i in c.mediaItemCount - 1 downTo 0) if (c.getMediaItemAt(i).mediaId.toLongOrNull() in gone) c.removeMediaItem(i)
                refreshLibrary()
            }
            done(gone.size)
        }
    }

    // ---- playlist import ----
    var importBusy by mutableStateOf<String?>(null); private set
    /** [input] is a link, or pasted / loaded text. Creates the playlist and calls [done] with a message (error or result). */
    fun importPlaylist(input: String, nameHint: String, done: (String) -> Unit) {
        if (importBusy != null) return
        importBusy = "Reading the playlist…"
        viewModelScope.launch {
            try {
                val text = input.trim()
                val one = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
                val imp = withContext(Dispatchers.IO) {
                    if (one.size == 1 && com.sudosoc.sudoplay.online.Online.isLink(one[0])) com.sudosoc.sudoplay.online.PlaylistImport.fromLink(one[0])
                    else com.sudosoc.sudoplay.online.PlaylistImport.parseText(nameHint.ifBlank { "Imported playlist" }, text)
                }
                if (imp.tracks.isEmpty()) { done("No songs found in that"); return@launch }
                importBusy = "Adding ${imp.tracks.size} songs…"
                val entries = imp.tracks.map { t ->
                    val key = if (t.url.isNotBlank()) t.url else "search:" + (t.artist + "|" + t.title).lowercase().replace(Regex("\\s+"), " ").trim()
                    com.sudosoc.sudoplay.data.OnlineEntry(com.sudosoc.sudoplay.online.Online.idFor(key), t.kind?.name ?: "Link", t.title, t.artist, t.thumb, t.durationSec, t.url)
                }
                olib.putAll(entries)
                val p = store.create(nameHint.ifBlank { imp.name })
                store.addSongs(p.id, entries.map { it.id })
                done("Imported ${entries.size} songs into “${p.name}”")
                android.widget.Toast.makeText(ctx, com.sudosoc.sudoplay.ui.Tr.t("Imported ${entries.size} songs — see Playlists"), android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Throwable) {
                val msg = com.sudosoc.sudoplay.online.Friendly.of(e)
                done(msg)
                android.widget.Toast.makeText(ctx, com.sudosoc.sudoplay.ui.Tr.t("Import failed: $msg"), android.widget.Toast.LENGTH_LONG).show()
            } finally { importBusy = null }
        }
    }

    // ---- playlist preview (Discover): open a public playlist without adding anything ----
    class PlaylistPreview(title: String, val thumb: String?, val url: String) {
        var title by mutableStateOf(title)
        var loading by mutableStateOf(true)
        var error by mutableStateOf<String?>(null)
        var tracks by mutableStateOf<List<com.sudosoc.sudoplay.online.ImpTrack>>(emptyList())
        var added by mutableStateOf(false)
    }
    var preview by mutableStateOf<PlaylistPreview?>(null); private set
    fun openPreview(title: String, thumb: String?, url: String) {
        val st = PlaylistPreview(title, thumb, url); preview = st
        viewModelScope.launch {
            try {
                val imp = withContext(Dispatchers.IO) { com.sudosoc.sudoplay.online.PlaylistImport.fromLink(url) }
                st.tracks = imp.tracks; if (st.title.isBlank()) st.title = imp.name
            }
            catch (e: Throwable) { st.error = com.sudosoc.sudoplay.online.Friendly.of(e) }
            finally { st.loading = false }
        }
    }
    fun closePreview() { preview = null }
    private fun entryOf(t: com.sudosoc.sudoplay.online.ImpTrack): com.sudosoc.sudoplay.data.OnlineEntry {
        val key = if (t.url.isNotBlank()) t.url else "search:" + (t.artist + "|" + t.title).lowercase().replace(Regex("\\s+"), " ").trim()
        return com.sudosoc.sudoplay.data.OnlineEntry(com.sudosoc.sudoplay.online.Online.idFor(key), t.kind?.name ?: "Link", t.title, t.artist, t.thumb, t.durationSec, t.url)
    }
    fun previewId(t: com.sudosoc.sudoplay.online.ImpTrack): Long = entryOf(t).id

    /** An online search result as a song that can be ticked, queued, favourited or put in a playlist. Folders / blank links give null. */
    fun songForOnline(item: com.sudosoc.sudoplay.online.OnlineItem): Song? {
        if (item.url.isBlank() || item.collection != null) return null
        val e = com.sudosoc.sudoplay.data.OnlineEntry(com.sudosoc.sudoplay.online.Online.idFor(item.url), item.kind.name, item.title, item.subtitle, item.thumb, item.durationSec, item.url)
        if (olib.get(e.id) == null) olib.put(e)
        return entrySong(e.id)
    }

    // ---- the queue ----
    fun moveInQueue(from: Int, to: Int) { controller?.let { if (from in 0 until it.mediaItemCount && to in 0 until it.mediaItemCount && from != to) it.moveMediaItem(from, to) } }
    /** Make queue item [i] the next one to play. */
    fun queueItemNext(i: Int) {
        val c = controller ?: return
        val cur = c.currentMediaItemIndex
        if (i == cur) return
        moveInQueue(i, if (i > cur) cur + 1 else cur)
    }
    fun clearQueueKeepCurrent() {
        val c = controller ?: return
        for (i in c.mediaItemCount - 1 downTo 0) if (i != c.currentMediaItemIndex) c.removeMediaItem(i)
    }
    /** Returns the new playlist's name. */
    fun saveQueueAsPlaylist(): String {
        val pl = store.create(current?.title?.let { "Queue • $it" } ?: "Queue")
        queue.forEach { addToPlaylist(pl.id, it) }
        return pl.name
    }

    /** The previewed songs as playable entries (only now are they remembered by the app; no playlist is created). */
    fun previewSongs(p: PlaylistPreview): List<Song> {
        val es = p.tracks.map(::entryOf); olib.putAll(es)
        return es.mapNotNull { entrySong(it.id) }.distinctBy { it.id }
    }
    /** "Add to my playlists": only when the user asks for it. */
    fun previewAdd(p: PlaylistPreview) {
        if (p.added) return
        val es = p.tracks.map(::entryOf); olib.putAll(es)
        val pl = store.create(p.title.ifBlank { "Playlist" })
        store.addSongs(pl.id, es.map { it.id })
        p.added = true
        android.widget.Toast.makeText(ctx, com.sudosoc.sudoplay.ui.Tr.t("Added “${pl.name}” to your playlists"), android.widget.Toast.LENGTH_SHORT).show()
    }

    /** Turns suggested songs (Discovery charts) into a playlist of "name only" entries. */
    fun saveTracksAsPlaylist(name: String, tracks: List<com.sudosoc.sudoplay.online.DTrack>): String {
        val entries = tracks.map { t ->
            val key = "search:" + (t.artist + "|" + t.title).lowercase().replace(Regex("\\s+"), " ").trim()
            com.sudosoc.sudoplay.data.OnlineEntry(com.sudosoc.sudoplay.online.Online.idFor(key), "Link", t.title, t.artist, t.thumb, t.durationSec, "")
        }
        olib.putAll(entries)
        val p = store.create(name)
        store.addSongs(p.id, entries.map { it.id })
        return p.name
    }

    private fun lyricArtist(a: String) = a.replace(Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE), "").replace(Regex("""VEVO$""", RegexOption.IGNORE_CASE), "").trim()
    /** "Artist - Song (Official Video)" → "Song" when the left part is the artist. */
    private fun lyricTitle(title: String, artist: String): String {
        val m = Regex("""\s[-–—]\s""").find(title) ?: return title
        val l = title.substring(0, m.range.first); val r = title.substring(m.range.last + 1)
        val at = com.sudosoc.sudoplay.online.OnlineMatch.tokens(artist)
        return when {
            com.sudosoc.sudoplay.online.OnlineMatch.tokens(l).any { it in at } -> r
            com.sudosoc.sudoplay.online.OnlineMatch.tokens(r).any { it in at } -> l
            else -> title
        }
    }
    val onlineSongs = HashMap<Long, Song>()
    var onlineBusy by mutableStateOf<String?>(null); private set
    var onlineError by mutableStateOf<String?>(null)
    var onlineNote by mutableStateOf<String?>(null)
    /** What each online song was found as (so a failed YouTube stream can be retried elsewhere, and so it can be downloaded). */
    val onlineOrigin = HashMap<Long, com.sudosoc.sudoplay.online.OnlineItem>()
    /** Download dialog request, shown on top of everything: (items, group name for a new playlist). */
    var openStudio by mutableStateOf(false)
    var dlRequest by mutableStateOf<Pair<List<com.sudosoc.sudoplay.online.OnlineItem>, String>?>(null)
    /** "Find this song online": the Online tab picks this up, searches it, and clears it. */
    var onlineQuery by mutableStateOf<String?>(null)
    var wantTab by mutableStateOf<Int?>(null)
    fun findOnline(song: Song) { onlineQuery = (song.title + " " + song.artist.takeIf { !it.equals("<unknown>", true) }.orEmpty()).trim(); wantTab = 5 }
    private val favPrefs get() = (prefs.getString("fav_sheikhs", "") ?: "").split("\n").filter { it.contains('\t') }
    var favSheikhs by mutableStateOf(favPrefs.map { it.substringBefore('\t') to it.substringAfter('\t') })
    fun toggleSheikh(name: String, key: String) {
        favSheikhs = if (favSheikhs.any { it.second == key }) favSheikhs.filter { it.second != key } else favSheikhs + (name to key)
        prefs.edit().putString("fav_sheikhs", favSheikhs.joinToString("\n") { it.first + "\t" + it.second }).apply()
    }
    /** mode 0 = play now, 1 = play next, 2 = add to queue */
    fun playOnline(item: com.sudosoc.sudoplay.online.OnlineItem, mode: Int = 0, fallbackTried: Boolean = false) {
        if (onlineBusy != null) return
        // already downloaded? play the file on the phone
        localMatch(item)?.let { loc ->
            when (mode) { 0 -> play(listOf(loc), 0); 1 -> playNext(loc); else -> addToQueue(loc) }
            return
        }
        if (offline) { onlineError = "You're offline — only songs saved on this phone can play"; return }
        onlineBusy = item.url; onlineError = null; onlineNote = null
        viewModelScope.launch {
            try {
                val s = resolveOnline(item, !fallbackTried)
                val c = controller
                if (mode == 0 || c == null || c.mediaItemCount == 0) play(listOf(s), 0)
                else if (mode == 1) c.addMediaItem(c.currentMediaItemIndex + 1, toItem(s))
                else c.addMediaItem(toItem(s))
            } catch (e: Throwable) {
                onlineError = com.sudosoc.sudoplay.online.Friendly.of(e)
            } finally { onlineBusy = null }
        }
    }
    /** Play something by name only (Discovery charts, imported "Artist - Title" lines). */
    fun playByName(title: String, artist: String, durationSec: Long = 0, thumb: String? = null) {
        if (onlineBusy != null) return
        onlineBusy = "name:$title"; onlineError = null; onlineNote = null
        viewModelScope.launch {
            try {
                val item = com.sudosoc.sudoplay.online.Online.findFor(title, artist, durationSec, onlineSources())
                    ?: throw java.io.IOException("Could not find “$title” on your search sources")
                onlineBusy = null
                playOnline(if (thumb != null && item.thumb == null) com.sudosoc.sudoplay.online.OnlineItem(item.kind, item.title, item.subtitle, thumb, item.durationSec, item.url) else item)
            } catch (e: Throwable) {
                onlineError = com.sudosoc.sudoplay.online.Friendly.of(e)
            } finally { if (onlineBusy?.startsWith("name:") == true) onlineBusy = null }
        }
    }
    /** Plays a list of direct-file items (a reciter's surahs, an archive.org item…) as a queue, starting at [start]. */
    fun playOnlineAll(items: List<com.sudosoc.sudoplay.online.OnlineItem>, start: com.sudosoc.sudoplay.online.OnlineItem) {
        val playable = items.filter { it.kind.direct && it.collection == null && it.url.isNotBlank() }
        if (playable.isEmpty()) return
        viewModelScope.launch {
            try {
                val songs = playable.map { com.sudosoc.sudoplay.online.Online.resolve(it) }
                songs.forEach { onlineSongs[it.id] = it }
                play(songs, playable.indexOf(start).coerceAtLeast(0))
            } catch (e: Throwable) { onlineError = (e.message ?: "Could not play").take(160) }
        }
    }
    fun playOnlineLink(link: String) {
        viewModelScope.launch {
            try { playOnline(com.sudosoc.sudoplay.online.Online.fromLink(link)) } catch (e: Throwable) { onlineError = (e.message ?: "Bad link").take(160) }
        }
    }

    var infoSong by mutableStateOf<Song?>(null)
    fun shuffleAll() { val l = songs; if (l.isNotEmpty()) play(l, l.indices.random(), true) }
    fun resumeNow() { controller?.let { if (it.mediaItemCount > 0) it.play() else shuffleAll() } }

    // ---- lecture mode (long audio remembers position + speed) ----
    private fun lectureSong(s: Song?) = s != null && settings.lectureMode.value && s.durationMs >= settings.lectureMinutes.value * 60_000L
    private fun saveLecture() {
        val c = controller ?: return
        val s = current ?: return
        if (!lectureSong(s) || !c.isPlaying) return
        val pos = c.currentPosition
        val e = prefs.edit()
        if (pos < 5_000 || pos > s.durationMs - 8_000) e.remove("lec_${s.id}") else e.putLong("lec_${s.id}", pos)
        e.putFloat("lecs_${s.id}", c.playbackParameters.speed).apply()
    }

    // ---- bookmarks (a note + a timestamp, on any song) ----
    class Bookmark(val ms: Long, val note: String)
    var bookmarks by mutableStateOf(loadBookmarks()); private set
    private fun loadBookmarks(): Map<Long, List<Bookmark>> = runCatching {
        val o = org.json.JSONObject(prefs.getString("bookmarks", "{}") ?: "{}")
        o.keys().asSequence().associate { k ->
            val a = o.getJSONArray(k)
            k.toLong() to List(a.length()) { i -> a.getJSONArray(i).let { Bookmark(it.getLong(0), it.getString(1)) } }
        }
    }.getOrDefault(emptyMap())
    private fun saveBookmarks() {
        val o = org.json.JSONObject()
        bookmarks.forEach { (id, list) -> o.put(id.toString(), org.json.JSONArray(list.map { org.json.JSONArray().put(it.ms).put(it.note) })) }
        prefs.edit().putString("bookmarks", o.toString()).apply()
    }
    fun addBookmark() {
        val s = current ?: return
        val ms = position
        val l = lyrics
        val line = if (l != null && l.synced) l.lines.lastOrNull { it.timeMs <= ms }?.text?.trim()?.take(48)?.takeIf { it.isNotEmpty() } else null
        val note = line ?: "Bookmark"
        bookmarks = bookmarks + (s.id to ((bookmarks[s.id] ?: emptyList()) + Bookmark(ms, note)).sortedBy { it.ms })
        saveBookmarks(); haptics.click()
    }
    fun removeBookmark(songId: Long, ms: Long) {
        bookmarks = bookmarks + (songId to (bookmarks[songId] ?: emptyList()).filter { it.ms != ms })
        saveBookmarks()
    }

    // ---- listening clock (plays per hour of the day) ----
    var hourCounts by mutableStateOf(
        (prefs.getString("hours", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }.let { if (it.size == 24) it else List(24) { 0 } }
    ); private set

    // ---- smart output: remember a sound profile per headphones / speaker ----
    private val audioMgr = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    var outputName by mutableStateOf("Phone speaker"); private set
    private var outputKey = "speaker"
    private fun currentOutput(): Pair<String, String> {
        val devs = audioMgr.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val order = listOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, 26 /* BLE headset */, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE,
        )
        for (t in order) devs.firstOrNull { it.type == t }?.let { d ->
            val name = d.productName?.toString()?.takeIf { it.isNotBlank() } ?: when (t) { AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"; AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB audio"; else -> "Headphones" }
            return "${t}_$name" to name
        }
        return "speaker" to "Phone speaker"
    }
    private fun routeChanged(added: Boolean) {
        val (key, name) = currentOutput()
        if (key == outputKey) return
        outputKey = key; outputName = name
        if (settings.deviceEq.value) prefs.getString("dfx_$key", null)?.let { fx = FxState.fromJson(it); sendFx(); prefs.edit().putString("fx", fx.toJson()).apply() }
        val c = controller
        if (added && key != "speaker" && settings.resumeOnConnect.value && c != null && !c.isPlaying && c.mediaItemCount > 0) c.play()
    }
    private val deviceCb = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = routeChanged(true)
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = routeChanged(false)
    }

    // ---- A-B loop ----
    var abA by mutableStateOf<Long?>(null); private set
    var abB by mutableStateOf<Long?>(null); private set
    fun abCycle() {
        when {
            abA == null -> abA = position
            abB == null -> if (position > (abA ?: 0L) + 500) abB = position else abA = null
            else -> { abA = null; abB = null }
        }
        haptics.click()
    }

    // ---- shake to skip ----
    private val sensors = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var shakeOn = false
    private var lastShake = 0L
    private val shakeListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]) / 9.81f
            val now = System.currentTimeMillis()
            if (g > 2.7f && now - lastShake > 1000) { lastShake = now; haptics.heavy(); next() }
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }
    private fun updateShake() {
        val want = settings.shake.value && isPlaying
        if (want == shakeOn) return
        shakeOn = want
        if (want) sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensors.registerListener(shakeListener, it, SensorManager.SENSOR_DELAY_UI) }
        else sensors.unregisterListener(shakeListener)
    }

    // ---- UI helpers ----
    var playlistTarget by mutableStateOf<Song?>(null)

    private var controller: MediaController? = null

    private val mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            refreshJob?.cancel()
            refreshJob = viewModelScope.launch { delay(1500); refreshLibrary() }
        }
    }

    /** A song that cannot be played does not stop the music: jump to the next one in the queue and say so. */
    private fun skipBroken(): Boolean {
        val c = controller ?: return false
        if (!c.hasNextMediaItem()) return false
        c.seekToNextMediaItem(); c.prepare(); c.play()
        onlineNote = "Skipped — couldn't play that song"
        return true
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync()
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            val cur = current
            if (cur?.online == true) {
                val http403 = (error.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode
                val origin = onlineOrigin[cur.id]
                if (origin != null && origin.kind == com.sudosoc.sudoplay.online.OnlineKind.YouTube && onlineBusy == null) {
                    // the stream URL resolved but the server refused it: try the same song elsewhere
                    viewModelScope.launch {
                        val alt = com.sudosoc.sudoplay.online.Online.alternative(origin)
                        if (alt != null) { onlineNote = "YouTube refused the stream — playing the ${alt.kind.label} version"; playOnline(alt, 0, true) }
                        else if (!skipBroken()) onlineError = "YouTube refused this stream (HTTP ${http403 ?: "error"}) and no other source has it."
                    }
                    return
                }
                if (skipBroken()) return
                onlineError = when {
                    http403 == 403 || http403 == 410 -> "The source refused this stream (HTTP $http403). YouTube does this often — try SoundCloud or radio, or search again later."
                    else -> "Playback failed: ${error.errorCodeName}"
                }
            }
        }
    }

    init {
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            runCatching { future.get() }.getOrNull()?.let {
                controller = it; flushReady()
                // online items can be muxed video files; we only ever want their sound
                it.trackSelectionParameters = it.trackSelectionParameters.buildUpon().setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true).build()
                it.addListener(listener)
                if (it.mediaItemCount > 0) restored = true
                sync()
                loadEqInfo()
                tryRestore()
                applyMisc()
                changePitch(settings.pitchSemi.value)
            }
        }, MoreExecutors.directExecutor())

        viewModelScope.launch {
            var tick = 0
            while (isActive) {
                delay(if (abB != null) 150 else 400)
                controller?.let { if (it.isPlaying) position = it.currentPosition }
                abB?.let { b -> abA?.let { a -> if (position >= b) seekTo(a) } }
                updateShake()
                if (sleepEndsAt != 0L && System.currentTimeMillis() > sleepEndsAt) sleepEndsAt = 0L
                if (++tick % 10 == 0) { saveQueue(); saveLecture() }
            }
        }

        currentOutput().let { outputKey = it.first; outputName = it.second }
        audioMgr.registerAudioDeviceCallback(deviceCb, Handler(Looper.getMainLooper()))

        // pick up new / deleted music without a manual refresh
        ctx.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mediaObserver)
    }


    fun refreshLibrary() {
        viewModelScope.launch {
            loading = true
            val minDur = settings.minDurationSec.value
            val list = withContext(Dispatchers.IO) { runCatching { repo.loadSongs(minDur) }.getOrDefault(emptyList()) }
            rawSongs = list
            CoverStore.registerSongs(list)
            applyLibraryFilters()
            loading = false
            sync()
            tryRestore()
        }
    }

    fun hiddenFolders(): Set<String> =
        settings.hiddenFolders.value.split("\n").filter { it.isNotEmpty() }.toSet()

    fun setFolderHidden(folder: String, hidden: Boolean) {
        val set = hiddenFolders().toMutableSet()
        if (hidden) set.add(folder) else set.remove(folder)
        settings.hiddenFolders.set(set.joinToString("\n"))
        applyLibraryFilters()
    }

    private fun applyLibraryFilters() {
        val hid = hiddenFolders()
        val list = if (hid.isEmpty()) rawSongs else rawSongs.filter { it.folder !in hid }
        songs = list; flushReady()
        byId = rawSongs.associateBy { it.id }   // a hidden song that is already queued can still resolve
    }

    // ---- remember the queue between launches ----
    private fun saveQueue() {
        val c = controller ?: return
        if (current?.online == true) return
        if (c.mediaItemCount == 0) return
        val ids = (0 until c.mediaItemCount).joinToString(",") { c.getMediaItemAt(it).mediaId }
        prefs.edit().putString("q_ids", ids).putInt("q_idx", c.currentMediaItemIndex).putLong("q_pos", c.currentPosition).apply()
    }

    private fun tryRestore() {
        val c = controller ?: return
        if (restored || byId.isEmpty()) return
        restored = true
        if (c.mediaItemCount > 0) return
        val ids = (prefs.getString("q_ids", "") ?: "").split(",").mapNotNull { it.toLongOrNull() }
        val items = ids.mapNotNull { byId[it] }.map(::toItem)
        if (items.isEmpty()) return
        val idx = prefs.getInt("q_idx", 0).coerceIn(0, items.lastIndex)
        c.setMediaItems(items, idx, prefs.getLong("q_pos", 0L))
        c.prepare()   // paused unless "start playing when the app opens" is on
        if (settings.autoPlayLaunch.value) c.play()
    }

    private fun sync() {
        val c = controller ?: return
        isPlaying = c.isPlaying
        shuffle = c.shuffleModeEnabled
        repeatMode = c.repeatMode
        speed = c.playbackParameters.speed
        queue = (0 until c.mediaItemCount).mapNotNull { c.getMediaItemAt(it).mediaId.toLongOrNull()?.let { id -> byId[id] ?: onlineSongs[id] } }
        queueIndex = c.currentMediaItemIndex
        current = c.currentMediaItem?.mediaId?.toLongOrNull()?.let { id -> byId[id] ?: onlineSongs[id] }
        duration = c.duration.takeIf { it > 0 } ?: current?.durationMs ?: 0L
        position = c.currentPosition

        val cur = current
        if (sleepEndOfSong && cur?.id != sleepSongId) sleepEndOfSong = false
        if (cur?.id != abSong) { abSong = cur?.id ?: -1L; abA = null; abB = null }
        if (cur != null && cur.id != waveFor) {
            waveFor = cur.id
            wave = Analyzer.cached(ctx, cur.id)?.peaks?.takeIf { it.isNotEmpty() }
            analyzeSong(cur, true)
        }
        queue.getOrNull(queueIndex + 1)?.let { n -> if (FxBus.gains[n.id.toString()] == null) analyzeSong(n, false) }
        if (cur != null && cur.id != lyricsFor) {
            lyricsFor = cur.id
            lyrics = null
            viewModelScope.launch {
                val l = withContext(Dispatchers.IO) {
                    runCatching { LyricsLoader.load(ctx, cur) ?: if (settings.autoLyrics.value) LyricsFetcher.fetch(ctx, cur) else null }.getOrNull()
                }
                if (lyricsFor == cur.id) lyrics = l
            }
        }
        if (cur != null && isPlaying && cur.id != lastCounted && settings.countPlays.value) {
            lastCounted = cur.id
            plays = plays + (cur.id to ((plays[cur.id] ?: 0) + 1))
            recent = (listOf(cur.id) + recent.filter { it != cur.id }).take(50)
            val hr = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            hourCounts = hourCounts.toMutableList().also { it[hr] = it[hr] + 1 }
            prefs.edit().putString("hours", hourCounts.joinToString(",")).apply()
            prefs.edit()
                .putString("plays", plays.entries.joinToString(",") { "${it.key}:${it.value}" })
                .putString("recent", recent.joinToString(","))
                .apply()
        }
    }

    fun smart(kind: Smart): List<Song> = when (kind) {
        Smart.Recent -> recent.mapNotNull { byId[it] }
        Smart.Most -> songs.filter { (plays[it.id] ?: 0) > 0 }.sortedByDescending { plays[it.id] ?: 0 }.take(100)
        Smart.Added -> songs.sortedByDescending { it.dateAdded }.take(100)
        Smart.Unplayed -> songs.filter { (plays[it.id] ?: 0) == 0 }
        Smart.Mix -> {
            val rnd = Random(mixSeed)
            val fav = songs.filter { (plays[it.id] ?: 0) > 0 }.sortedByDescending { plays[it.id] ?: 0 }.take(15)
            val rest = (songs - fav.toSet()).shuffled(rnd).take(30)
            (fav + rest).shuffled(rnd)
        }
    }

    fun backupJson(): String = com.sudosoc.sudoplay.data.Backup.export(ctx, prefs)
    fun restoreJson(json: String): Boolean = com.sudosoc.sudoplay.data.Backup.import(ctx, prefs, json)

    fun clearHistory() {
        plays = emptyMap(); recent = emptyList(); lastCounted = -1L
        prefs.edit().remove("plays").remove("recent").apply()
    }

    // ---- sound engine ----
    private fun loadEqInfo() {
        val c = controller ?: return
        val f = c.sendCustomCommand(SessionCommand(PlaybackService.CMD_EQ_INFO, Bundle.EMPTY), Bundle.EMPTY)
        f.addListener({
            runCatching { f.get().extras }.getOrNull()?.let { b ->
                eqBands = b.getInt(PlaybackService.KEY_BANDS)
                if (eqBands > 0) {
                    eqMin = b.getInt(PlaybackService.KEY_MINLVL)
                    eqMax = b.getInt(PlaybackService.KEY_MAXLVL)
                    eqFreqs = b.getIntArray(PlaybackService.KEY_FREQS)?.toList() ?: emptyList()
                    if (fx.levels.size != eqBands) fx = fx.copy(levels = List(eqBands) { 0 })
                }
                sendFx()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun sendFx() {
        controller?.sendCustomCommand(
            SessionCommand(PlaybackService.CMD_EQ_SET, Bundle.EMPTY),
            bundleOf(PlaybackService.KEY_FX to fx.toJson()),
        )
    }

    /** Update the sound settings live. Pass save = false while dragging and call [fxSave] when the finger lifts. */
    fun fxEdit(save: Boolean = true, f: (FxState) -> FxState) {
        fx = f(fx)
        sendFx()
        if (save) fxSave()
    }

    fun fxSave() {
        prefs.edit().putString("fx", fx.toJson()).apply()
        if (settings.deviceEq.value) prefs.edit().putString("dfx_$outputKey", fx.toJson()).apply()
    }
    fun toggleFx() { fxEdit { it.copy(enabled = !it.enabled) }; haptics.click() }

    fun setEqLevel(i: Int, v: Int) = fxEdit(save = false) { st ->
        st.copy(levels = st.levels.toMutableList().also { if (i in it.indices) it[i] = v }, preset = "")
    }

    fun applyCurve(name: String) {
        if (eqBands == 0) return
        fxEdit { it.copy(enabled = true, levels = EqPresets.levels(name, eqBands, eqMax), preset = name) }
    }

    fun applyScene(scene: FxScene) = fxEdit { FxScenes.apply(scene, eqBands.coerceAtLeast(5), eqMax) }
    fun applyUserScene(name: String, st: FxState) = fxEdit {
        st.copy(enabled = true, levels = if (st.levels.size == eqBands) st.levels else it.levels, preset = name)
    }

    fun resetFx() = fxEdit { FxState(enabled = it.enabled, levels = List(eqBands) { 0 }) }

    fun saveScene(name: String) {
        val n = name.trim().ifEmpty { return }
        userScenes = userScenes.filter { it.first != n } + (n to fx.copy(preset = n))
        FxScenes.saveUser(prefs, userScenes)
    }

    fun deleteScene(name: String) {
        userScenes = userScenes.filter { it.first != name }
        FxScenes.saveUser(prefs, userScenes)
    }

    /** Same song + same quality = same key, even though the signed link changes every time it is resolved. */
    private fun streamCacheKey(s: Song): String {
        val u = s.uri
        val yt = u.host?.contains("googlevideo") == true
        return if (yt) "yt-${u.getQueryParameter("id") ?: s.id}-${u.getQueryParameter("itag") ?: ""}-${u.getQueryParameter("clen") ?: ""}"
        else "sp-${s.id}-${(u.host + u.path).hashCode()}"
    }

    private fun toItem(s: Song): MediaItem = MediaItem.Builder()
        .setMediaId(s.id.toString())
        .setUri(s.uri)
        // streamed songs with a known length (not radio, not playlists) get a stable key so replays come from the cache
        .apply { if (s.online && s.durationMs > 0 && s.uri.scheme?.startsWith("http") == true && !s.uri.toString().contains(".m3u8")) setCustomCacheKey(streamCacheKey(s)) }
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(s.title).setArtist(s.artist).setAlbumTitle(s.album)
                .setArtworkUri(s.artUrl?.let { android.net.Uri.parse(it) } ?: s.uri)
                .build()
        ).build()

    // ---- playback actions ----
    fun play(list: List<Song>, index: Int, shuffleOn: Boolean? = null) { mixJob?.cancel(); playInternal(list, index, shuffleOn) }
    private fun playInternal(list: List<Song>, index: Int, shuffleOn: Boolean?) {
        val c = controller ?: return
        if (list.isEmpty()) return
        shuffleOn?.let { c.shuffleModeEnabled = it }
        val idx = index.coerceIn(0, list.lastIndex)
        val chosen = list[idx]
        val lecture = lectureSong(chosen)
        c.setMediaItems(list.map(::toItem), idx, if (lecture) prefs.getLong("lec_${chosen.id}", 0L) else 0L)
        if (lecture) prefs.getFloat("lecs_${chosen.id}", 0f).takeIf { it > 0f }?.let { c.setPlaybackSpeed(it) }
        c.prepare(); c.play()
    }

    fun shufflePlay(list: List<Song>) {
        if (list.isNotEmpty()) play(list, Random.nextInt(list.size), true)
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else { if (c.playbackState == Player.STATE_IDLE) c.prepare(); c.play() }
    }

    fun next() { controller?.seekToNext() }
    fun previous() { controller?.seekToPrevious() }
    fun seekTo(ms: Long) { controller?.seekTo(ms); position = ms }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun playNext(song: Song) {
        if (song.online && olib.get(song.id) != null) { viewModelScope.launch { materialize(song)?.let { playNext(it) } }; return }
        val c = controller ?: return
        if (c.mediaItemCount == 0) play(listOf(song), 0)
        else c.addMediaItem(c.currentMediaItemIndex + 1, toItem(song))
    }

    fun addToQueue(song: Song) {
        if (song.online && olib.get(song.id) != null) { viewModelScope.launch { materialize(song)?.let { addToQueue(it) } }; return }
        val c = controller ?: return
        if (c.mediaItemCount == 0) play(listOf(song), 0) else c.addMediaItem(toItem(song))
    }

    // ---- several songs at once (multi-select) ----
    val sel = com.sudosoc.sudoplay.ui.SelectionState()
    val undo = com.sudosoc.sudoplay.ui.UndoState()

    private suspend fun resolvedForQueue(s: Song): Song? = if (s.online && olib.get(s.id) != null) materialize(s) else s

    /** Insert [list] right after the current song, keeping the order shown on screen. */
    fun playNextAll(list: List<Song>) {
        viewModelScope.launch {
            var k = 0
            for (s in list) {
                val r = resolvedForQueue(s) ?: continue
                val c = controller ?: return@launch
                if (c.mediaItemCount == 0) { play(listOf(r), 0); k = 1 }
                else { c.addMediaItem((c.currentMediaItemIndex + 1 + k).coerceAtMost(c.mediaItemCount), toItem(r)); k++ }
            }
        }
    }

    fun addToQueueAll(list: List<Song>) {
        viewModelScope.launch {
            for (s in list) {
                val r = resolvedForQueue(s) ?: continue
                val c = controller ?: return@launch
                if (c.mediaItemCount == 0) play(listOf(r), 0) else c.addMediaItem(toItem(r))
            }
        }
    }

    /** Returns how many were newly added. */
    fun favoriteAll(list: List<Song>): Int {
        var n = 0
        list.forEach { s -> if (!store.isFav(s.id)) { store.addSong(com.sudosoc.sudoplay.data.FAV_ID, s.id); n++ } }
        return n
    }

    fun removeFromQueue(index: Int) { controller?.removeMediaItem(index) }
    fun jumpTo(index: Int) { controller?.let { it.seekToDefaultPosition(index); it.play() } }

    /** Extend a running sleep timer by [extra] minutes. */
    fun extendSleep(extra: Int) {
        val remainMin = ((sleepEndsAt - System.currentTimeMillis()).coerceAtLeast(0) + 59_999) / 60_000
        setSleep((remainMin + extra).toInt())
    }

    /** minutes > 0: pause after that long; 0: cancel; -1: pause when the current song ends. */
    fun setSleep(minutes: Int) {
        val c = controller ?: return
        c.sendCustomCommand(
            SessionCommand(PlaybackService.CMD_SLEEP, Bundle.EMPTY),
            bundleOf(PlaybackService.KEY_MIN to minutes, PlaybackService.KEY_FADE to settings.sleepFade.value),
        )
        sleepEndsAt = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L
        sleepEndOfSong = minutes < 0
        sleepSongId = current?.id ?: -1L
        if (minutes > 0) settings.lastSleepMin.set(minutes)
    }

    fun applyMisc() {
        controller?.sendCustomCommand(
            SessionCommand(PlaybackService.CMD_MISC, Bundle.EMPTY),
            bundleOf(
                PlaybackService.KEY_SKIP to settings.skipSilence.value,
                PlaybackService.KEY_XFADE to settings.crossfade.value,
                PlaybackService.KEY_RG to settings.replayGain.value,
            ),
        )
    }

    fun changePitch(semi: Int) {
        settings.pitchSemi.set(semi)
        val c = controller ?: return
        c.playbackParameters = PlaybackParameters(c.playbackParameters.speed, 2.0.pow(semi / 12.0).toFloat())
    }

    fun changeSpeedTo(s: Float) {
        controller?.setPlaybackSpeed(s)
        if (s != 1f) settings.lastSpeedPct.set((s * 100).toInt())
    }

    override fun onCleared() {
        saveQueue()
        ctx.contentResolver.unregisterContentObserver(mediaObserver)
        runCatching { audioMgr.unregisterAudioDeviceCallback(deviceCb) }
        controller?.removeListener(listener)
        controller?.release()
        super.onCleared()
    }
}

private fun parsePlays(raw: String): Map<Long, Int> =
    raw.split(",").mapNotNull {
        val p = it.split(":")
        val id = p.getOrNull(0)?.toLongOrNull()
        val n = p.getOrNull(1)?.toIntOrNull()
        if (id != null && n != null) id to n else null
    }.toMap()
