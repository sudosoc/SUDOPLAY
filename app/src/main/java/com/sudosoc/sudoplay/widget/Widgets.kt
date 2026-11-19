package com.sudosoc.sudoplay.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import android.widget.Toast
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.sudosoc.sudoplay.MainActivity
import com.sudosoc.sudoplay.R
import com.sudosoc.sudoplay.playback.ArtLoader
import com.sudosoc.sudoplay.playback.PlaybackService
import java.util.concurrent.Executors

class SudoWidgetSmall : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) = WidgetUpdater.update(context)
}
class SudoWidgetLarge : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) = WidgetUpdater.update(context)
}
class SudoWidgetSquare : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) = WidgetUpdater.update(context)
}

/** Handles the three buttons on the widgets by briefly connecting to the playback service. */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull()
            if (c == null) { pending.finish(); return@addListener }
            if (c.mediaItemCount == 0) {
                Toast.makeText(ctx, com.sudosoc.sudoplay.ui.Tr.t("Open SUDOPLAY and pick a song first"), Toast.LENGTH_SHORT).show()
            } else when (intent.action) {
                ACTION_PLAY -> if (c.isPlaying) c.pause() else { if (c.playbackState == Player.STATE_IDLE) c.prepare(); c.play() }
                ACTION_NEXT -> c.seekToNext()
                ACTION_PREV -> c.seekToPrevious()
            }
            // let the command reach the service before the connection goes away
            Handler(Looper.getMainLooper()).postDelayed({ c.release(); pending.finish() }, 800)
        }, MoreExecutors.directExecutor())
    }

    companion object {
        const val ACTION_PLAY = "com.sudosoc.sudoplay.W_PLAY"
        const val ACTION_NEXT = "com.sudosoc.sudoplay.W_NEXT"
        const val ACTION_PREV = "com.sudosoc.sudoplay.W_PREV"
    }
}

object WidgetUpdater {
    private val exec = Executors.newSingleThreadExecutor()

    /** Remember what is playing (the widgets draw from this) and redraw them. Call on the main thread. */
    fun publish(ctx: Context, p: Player) {
        val md = p.mediaMetadata
        ctx.getSharedPreferences("sudoplay", Context.MODE_PRIVATE).edit()
            .putString("w_title", md.title?.toString()).putString("w_artist", md.artist?.toString())
            .putBoolean("w_playing", p.isPlaying).putString("w_art", md.artworkUri?.toString()).apply()
        update(ctx)
    }

    fun update(ctx: Context) {
        val app = ctx.applicationContext
        exec.execute { runCatching { render(app) } }
    }

    private fun intentFor(ctx: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(ctx, code, Intent(ctx, WidgetActionReceiver::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun render(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val kinds = listOf(
            SudoWidgetSmall::class.java to R.layout.widget_small,
            SudoWidgetLarge::class.java to R.layout.widget_large,
            SudoWidgetSquare::class.java to R.layout.widget_square,
        )
        val live = kinds.map { (cls, layout) -> Triple(cls, layout, mgr.getAppWidgetIds(ComponentName(ctx, cls))) }.filter { it.third.isNotEmpty() }
        if (live.isEmpty()) return

        val prefs = ctx.getSharedPreferences("sudoplay", Context.MODE_PRIVATE)
        val title = prefs.getString("w_title", null) ?: "SUDOPLAY"
        val artist = prefs.getString("w_artist", null) ?: "Nothing playing"
        val playing = prefs.getBoolean("w_playing", false)
        val art = prefs.getString("w_art", null)?.let { u -> runCatching { ArtLoader.load(ctx, Uri.parse(u), 320) }.getOrNull() }
        val theme = WidgetTheme.of(prefs.getString("w_theme", "dark") ?: "dark", art)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_PLAYER).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        for ((_, layout, ids) in live) {
            val rv = RemoteViews(ctx.packageName, layout)
            rv.setInt(R.id.w_bg, "setColorFilter", theme.bg)
            rv.setInt(R.id.w_bg, "setImageAlpha", theme.bgAlpha)
            rv.setTextViewText(R.id.w_title, title)
            rv.setTextViewText(R.id.w_artist, artist)
            rv.setTextColor(R.id.w_title, theme.text)
            rv.setTextColor(R.id.w_artist, theme.sub)
            if (art != null) { rv.setImageViewBitmap(R.id.w_art, art); rv.setViewPadding(R.id.w_art, 0, 0, 0, 0) }
            rv.setImageViewResource(R.id.w_play, if (playing) R.drawable.ic_w_pause else R.drawable.ic_w_play)
            for (id in intArrayOf(R.id.w_play, R.id.w_prev, R.id.w_next)) rv.setInt(id, "setColorFilter", theme.text)
            rv.setOnClickPendingIntent(R.id.w_play, intentFor(ctx, WidgetActionReceiver.ACTION_PLAY, 1))
            rv.setOnClickPendingIntent(R.id.w_next, intentFor(ctx, WidgetActionReceiver.ACTION_NEXT, 2))
            rv.setOnClickPendingIntent(R.id.w_prev, intentFor(ctx, WidgetActionReceiver.ACTION_PREV, 3))
            rv.setOnClickPendingIntent(R.id.w_art, open)
            rv.setOnClickPendingIntent(R.id.w_text, open)
            mgr.updateAppWidget(ids, rv)
        }
    }
}

class WidgetTheme(val bg: Int, val bgAlpha: Int, val text: Int, val sub: Int) {
    companion object {
        val ids = listOf("dark" to "Dark", "light" to "Light", "glass" to "Glass", "black" to "Black", "accent" to "Cover color", "clear" to "Clear")

        fun of(id: String, art: android.graphics.Bitmap?): WidgetTheme = when (id) {
            "light" -> WidgetTheme(0xFFF3F4FA.toInt(), 255, 0xFF14213D.toInt(), 0xAA14213D.toInt())
            "glass" -> WidgetTheme(0xFF1E2238.toInt(), 170, 0xFFFFFFFF.toInt(), 0xBBFFFFFF.toInt())
            "black" -> WidgetTheme(0xFF000000.toInt(), 255, 0xFFFFFFFF.toInt(), 0xAAFFFFFF.toInt())
            "clear" -> WidgetTheme(0xFF000000.toInt(), 0, 0xFFFFFFFF.toInt(), 0xCCFFFFFF.toInt())
            "accent" -> {
                val c = art?.let { com.sudosoc.sudoplay.ui.computePalette(it).first }
                val argb = c?.let { androidx.core.graphics.ColorUtils.setAlphaComponent(android.graphics.Color.argb(255, (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt()), 255) } ?: 0xFF2B2F55.toInt()
                WidgetTheme(argb, 255, 0xFFFFFFFF.toInt(), 0xCCFFFFFF.toInt())
            }
            else -> WidgetTheme(0xFF161A2E.toInt(), 255, 0xFFF2F4FF.toInt(), 0xAAF2F4FF.toInt())
        }
    }
}
