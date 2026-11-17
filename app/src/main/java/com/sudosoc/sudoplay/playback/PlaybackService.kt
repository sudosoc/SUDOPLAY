@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sudosoc.sudoplay.playback

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.common.util.BitmapLoader
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.sudosoc.sudoplay.data.FxState
import java.util.concurrent.Callable
import kotlin.math.pow
import java.util.concurrent.Executors

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private val sleepRunnable = Runnable { session?.player?.pause() }

    // audio effects live here so they survive the activity being closed
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var virt: Virtualizer? = null
    private var loud: LoudnessEnhancer? = null
    private var lastFx: FxState? = null
    private lateinit var exo: ExoPlayer
    private val fxProcessor = FxProcessor()

    override fun onCreate() {
        super.onCreate()
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf<AudioProcessor>(fxProcessor))
                    .build()
        }
        val player = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        exo = player
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) = attachEffects(audioSessionId)
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                // the "end of song" sleep timer is one-shot
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) exo.pauseAtEndOfMediaItems = false
            }
        })
        attachEffects(player.audioSessionId)
        session = MediaSession.Builder(this, player)
            .setBitmapLoader(SudoBitmapLoader(this))
            .setCallback(Callback())
            .build()
    }

    private fun attachEffects(sessionId: Int) {
        releaseEffects()
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return
        eq = runCatching { Equalizer(0, sessionId) }.getOrNull()
        bass = runCatching { BassBoost(0, sessionId) }.getOrNull()
        virt = runCatching { Virtualizer(0, sessionId) }.getOrNull()
        loud = runCatching { LoudnessEnhancer(sessionId) }.getOrNull()
        lastFx?.let { applyFx(it) }
    }

    private fun releaseEffects() {
        runCatching { eq?.release() }; runCatching { bass?.release() }
        runCatching { virt?.release() }; runCatching { loud?.release() }
        eq = null; bass = null; virt = null; loud = null
    }

    private fun applyFx(st: FxState) {
        lastFx = st
        val on = st.enabled
        eq?.let { e ->
            runCatching {
                e.enabled = on
                for (i in 0 until minOf(st.levels.size, e.numberOfBands.toInt())) e.setBandLevel(i.toShort(), st.levels[i].toShort())
            }
        }
        bass?.let { runCatching { if (it.strengthSupported) it.setStrength(st.bass.toShort()); it.enabled = on && st.bass > 0 } }
        virt?.let {
            runCatching {
                if (st.virtOn) it.forceVirtualizationMode(when (st.virtMode) { 1 -> Virtualizer.VIRTUALIZATION_MODE_BINAURAL; 2 -> Virtualizer.VIRTUALIZATION_MODE_TRANSAURAL; else -> Virtualizer.VIRTUALIZATION_MODE_AUTO })
                if (it.strengthSupported) it.setStrength(st.virt.toShort())
                it.enabled = on && st.virtOn && st.virt > 0
            }
        }
        loud?.let { runCatching { it.setTargetGain(st.loud); it.enabled = on && st.loud > 0 } }
        // preamp: attenuate before the effects so boosted EQ doesn't clip
        exo.volume = if (on) 10f.pow(st.preamp / 200f).coerceIn(0f, 1f) else 1f
        FxBus.set(st)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(sleepRunnable)
        releaseEffects()
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }

    private inner class Callback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val cmds = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_SLEEP, Bundle.EMPTY))
                .add(SessionCommand(CMD_EQ_INFO, Bundle.EMPTY))
                .add(SessionCommand(CMD_EQ_SET, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(cmds)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_SLEEP -> {
                    val minutes = args.getInt(KEY_MIN, 0)
                    handler.removeCallbacks(sleepRunnable)
                    exo.pauseAtEndOfMediaItems = minutes < 0   // -1 = stop when this song ends
                    if (minutes > 0) handler.postDelayed(sleepRunnable, minutes * 60_000L)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                CMD_EQ_INFO -> {
                    val out = Bundle()
                    val e = eq
                    if (e == null) out.putInt(KEY_BANDS, 0) else {
                        val n = e.numberOfBands.toInt()
                        val r = e.bandLevelRange
                        out.putInt(KEY_BANDS, n)
                        out.putInt(KEY_MINLVL, r[0].toInt())
                        out.putInt(KEY_MAXLVL, r[1].toInt())
                        out.putIntArray(KEY_FREQS, IntArray(n) { e.getCenterFreq(it.toShort()) / 1000 })
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, out))
                }
                CMD_EQ_SET -> {
                    applyFx(FxState.fromJson(args.getString(KEY_FX)))
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    companion object {
        const val CMD_SLEEP = "sudoplay.sleep"
        const val CMD_EQ_INFO = "sudoplay.eq.info"
        const val CMD_EQ_SET = "sudoplay.eq.set"
        const val KEY_MIN = "minutes"
        const val KEY_FX = "fx"
        const val KEY_BANDS = "bands"
        const val KEY_MINLVL = "min"
        const val KEY_MAXLVL = "max"
        const val KEY_FREQS = "freqs"
    }
}

/** Lets the media notification / lock screen show artwork straight from the audio file. */
class SudoBitmapLoader(private val ctx: Context) : BitmapLoader {
    private val exec = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(2))

    override fun supportsMimeType(mimeType: String) = true

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        exec.submit(Callable<Bitmap> {
            BitmapFactory.decodeByteArray(data, 0, data.size) ?: error("decode failed")
        })

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        exec.submit(Callable<Bitmap> { ArtLoader.load(ctx, uri, 512) ?: error("no artwork") })
}
