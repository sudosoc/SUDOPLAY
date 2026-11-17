package com.sudosoc.sudoplay

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
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
import com.sudosoc.sudoplay.data.LyricsLoader
import com.sudosoc.sudoplay.data.MusicRepository
import com.sudosoc.sudoplay.data.PlaylistStore
import com.sudosoc.sudoplay.data.Smart
import com.sudosoc.sudoplay.data.Song
import com.sudosoc.sudoplay.playback.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    // ---- lyrics ----
    var lyrics by mutableStateOf<Lyrics?>(null); private set
    private var lyricsFor = -1L

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
                controller = it
                it.addListener(listener)
                if (it.mediaItemCount > 0) restored = true
                sync()
                loadEqInfo()
                tryRestore()
            }
        }, MoreExecutors.directExecutor())

        viewModelScope.launch {
            var tick = 0
            while (isActive) {
                delay(400)
                controller?.let { if (it.isPlaying) position = it.currentPosition }
                if (sleepEndsAt != 0L && System.currentTimeMillis() > sleepEndsAt) sleepEndsAt = 0L
                if (++tick % 10 == 0) saveQueue()
            }
        }

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
        songs = list
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
        c.prepare()   // paused: the user taps play when ready
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
        if (cur != null && cur.id != lyricsFor) {
            lyricsFor = cur.id
            lyrics = null
            viewModelScope.launch {
                val l = withContext(Dispatchers.IO) { runCatching { LyricsLoader.load(ctx, cur) }.getOrNull() }
                if (lyricsFor == cur.id) lyrics = l
            }
        }
        if (cur != null && isPlaying && cur.id != lastCounted && settings.countPlays.value) {
            lastCounted = cur.id
            plays = plays + (cur.id to ((plays[cur.id] ?: 0) + 1))
            recent = (listOf(cur.id) + recent.filter { it != cur.id }).take(50)
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
    }

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

    fun fxSave() { prefs.edit().putString("fx", fx.toJson()).apply() }
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
        c.setMediaItems(list.map(::toItem), index.coerceIn(0, list.lastIndex), 0L)
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
            bundleOf(PlaybackService.KEY_MIN to minutes),
        )
        sleepEndsAt = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L
        sleepEndOfSong = minutes < 0
        sleepSongId = current?.id ?: -1L
        if (minutes > 0) settings.lastSleepMin.set(minutes)
    }

    fun changeSpeedTo(s: Float) {
        controller?.setPlaybackSpeed(s)
        if (s != 1f) settings.lastSpeedPct.set((s * 100).toInt())
    }

    override fun onCleared() {
        saveQueue()
        ctx.contentResolver.unregisterContentObserver(mediaObserver)
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
