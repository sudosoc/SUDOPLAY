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
import androidx.media3.common.util.BitmapLoader
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Callable
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
    private var lastCfg: Bundle? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
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
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) = attachEffects(audioSessionId)
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
        lastCfg?.let { applyCfg(it) }
    }

    private fun releaseEffects() {
        runCatching { eq?.release() }; runCatching { bass?.release() }
        runCatching { virt?.release() }; runCatching { loud?.release() }
        eq = null; bass = null; virt = null; loud = null
    }

    private fun applyCfg(b: Bundle) {
        lastCfg = b
        val on = b.getBoolean(KEY_ON)
        eq?.let { e ->
            runCatching {
                e.enabled = on
                val lv = b.getIntArray(KEY_LEVELS)
                if (lv != null) for (i in 0 until minOf(lv.size, e.numberOfBands.toInt())) e.setBandLevel(i.toShort(), lv[i].toShort())
            }
        }
        bass?.let { runCatching { val s = b.getInt(KEY_BASS); if (it.strengthSupported) it.setStrength(s.toShort()); it.enabled = on && s > 0 } }
        virt?.let { runCatching { val s = b.getInt(KEY_VIRT); if (it.strengthSupported) it.setStrength(s.toShort()); it.enabled = on && s > 0 } }
        loud?.let { runCatching { val g = b.getInt(KEY_LOUD); it.setTargetGain(g); it.enabled = on && g > 0 } }
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
                    applyCfg(args)
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
        const val KEY_ON = "enabled"
        const val KEY_LEVELS = "levels"
        const val KEY_BASS = "bass"
        const val KEY_VIRT = "virt"
        const val KEY_LOUD = "loud"
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
