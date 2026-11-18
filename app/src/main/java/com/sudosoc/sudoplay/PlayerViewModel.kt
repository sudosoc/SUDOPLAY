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

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync()
    }

    init {
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            runCatching { future.get() }.getOrNull()?.let {
                controller = it; flushReady()
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
        queue = (0 until c.mediaItemCount).mapNotNull { byId[c.getMediaItemAt(it).mediaId.toLongOrNull()] }
        queueIndex = c.currentMediaItemIndex
        current = byId[c.currentMediaItem?.mediaId?.toLongOrNull()]
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

    private fun toItem(s: Song): MediaItem = MediaItem.Builder()
        .setMediaId(s.id.toString())
        .setUri(s.uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(s.title).setArtist(s.artist).setAlbumTitle(s.album)
                .setArtworkUri(s.uri)
                .build()
        ).build()

    // ---- playback actions ----
    fun play(list: List<Song>, index: Int, shuffleOn: Boolean? = null) {
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
        val c = controller ?: return
        if (c.mediaItemCount == 0) play(listOf(song), 0)
        else c.addMediaItem(c.currentMediaItemIndex + 1, toItem(song))
    }

    fun addToQueue(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) play(listOf(song), 0) else c.addMediaItem(toItem(song))
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
