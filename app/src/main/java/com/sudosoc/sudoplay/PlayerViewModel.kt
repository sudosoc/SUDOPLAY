package com.sudosoc.sudoplay

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
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
import com.sudosoc.sudoplay.data.MusicRepository
import com.sudosoc.sudoplay.data.PlaylistStore
import com.sudosoc.sudoplay.data.Song
import com.sudosoc.sudoplay.playback.PlaybackService
import kotlinx.coroutines.Dispatchers
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

    // ---- library ----
    var songs by mutableStateOf<List<Song>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    private var byId: Map<Long, Song> = emptyMap()

    // ---- theme ----
    var themeId by mutableStateOf(prefs.getString("theme", "aurora") ?: "aurora"); private set

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

    // ---- UI helpers ----
    var playlistTarget by mutableStateOf<Song?>(null)

    private var controller: MediaController? = null

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
                sync()
            }
        }, MoreExecutors.directExecutor())

        viewModelScope.launch {
            while (isActive) {
                delay(400)
                controller?.let { if (it.isPlaying) position = it.currentPosition }
                if (sleepEndsAt != 0L && System.currentTimeMillis() > sleepEndsAt) sleepEndsAt = 0L
            }
        }
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            loading = true
            val list = withContext(Dispatchers.IO) { runCatching { repo.loadSongs() }.getOrDefault(emptyList()) }
            songs = list
            byId = list.associateBy { it.id }
            loading = false
            sync()
        }
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
    fun changeSpeed(s: Float) { controller?.setPlaybackSpeed(s) }

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

    fun setSleep(minutes: Int) {
        val c = controller ?: return
        c.sendCustomCommand(
            SessionCommand(PlaybackService.CMD_SLEEP, Bundle.EMPTY),
            bundleOf(PlaybackService.KEY_MIN to minutes),
        )
        sleepEndsAt = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L
    }

    // ---- theme ----
    fun setTheme(id: String) {
        themeId = id
        prefs.edit().putString("theme", id).apply()
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
        super.onCleared()
    }
}
