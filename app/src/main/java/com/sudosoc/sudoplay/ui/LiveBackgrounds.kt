package com.sudosoc.sudoplay.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import com.sudosoc.sudoplay.playback.FxBus
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/** The animated backgrounds, shared by the app themes and the player themes. */
val LIVE_KINDS = setOf(PBg.Aurora, PBg.Rain, PBg.Fireflies, PBg.Pulse, PBg.Lava, PBg.Snow, PBg.Matrix, PBg.Bokeh, PBg.Ripples, PBg.Embers, PBg.Nebula)

val PBg.label: String
    get() = when (this) {
        PBg.Aurora -> "Aurora"; PBg.Rain -> "Rain"; PBg.Fireflies -> "Fireflies"; PBg.Pulse -> "Music pulse"; PBg.Lava -> "Lava lamp"
        PBg.Snow -> "Snowfall"; PBg.Matrix -> "Code rain"; PBg.Bokeh -> "Bokeh lights"; PBg.Ripples -> "Ripples"; PBg.Embers -> "Embers"; PBg.Nebula -> "Nebula"
        else -> name
    }

private fun periodOf(k: PBg) = when (k) {
    PBg.Rain -> 9000; PBg.Pulse -> 8000; PBg.Matrix -> 12000; PBg.Embers -> 10000; PBg.Snow -> 22000; PBg.Ripples -> 12000
    else -> 24000
}

/** Draws [kind] over whatever is behind it. Motion is a function of one looping phase, so it loops without a jump and costs nothing when paused. */
@Composable
fun LiveLayer(kind: PBg, colors: List<Color>, animated: Boolean, modifier: Modifier = Modifier.fillMaxSize()) {
    val mode = LocalLiveMode.current
    val move = animated && mode > 0
    val ph = rememberPhase(move, periodOf(kind))
    val react = move && mode >= 2
    Box(modifier.drawBehind { drawLive(kind, ph.value, colors.ifEmpty { listOf(Color.White) }, if (react) bassLevel() else 0f) })
}

private fun bassLevel(): Float {
    if (SystemClock.elapsedRealtime() - FxBus.lastUpdate > 350) return 0f
    val l = FxBus.levels
    var s = 0f
    for (i in 0 until 6) s += l[i]
    return (s / 6f).coerceIn(0f, 1f)
}

private fun hash(i: Int, k: Int): Float {
    var x = i * 73856093 xor k * 19349663
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0xFFFF) / 65535f
}

private const val TAU = (2 * PI).toFloat()

private fun DrawScope.drawLive(kind: PBg, ph: Float, cols: List<Color>, bass: Float) {
    fun col(i: Int) = cols[i % cols.size]
    val w = size.width; val h = size.height
    val dp = density
    when (kind) {
        PBg.Aurora -> for (k in 0 until 4) {
            val c = col(k + 1)
            val baseY = h * (0.14f + 0.11f * k); val amp = h * 0.06f; val band = h * 0.30f * (0.85f + 0.15f * sin(ph * TAU * 2 + k))
            fun top(x: Float) = baseY + amp * sin(x / w * TAU * (1.3f + 0.4f * k) + ph * TAU * (1 + k % 2) + k) + amp * 0.5f * sin(x / w * TAU * 3 - ph * TAU)
            val p = Path()
            var x = 0f; p.moveTo(0f, top(0f))
            while (x < w) { x += 12f; p.lineTo(x.coerceAtMost(w), top(x.coerceAtMost(w))) }
            x = w; while (x > 0f) { p.lineTo(x, top(x) + band); x -= 12f }
            p.lineTo(0f, top(0f) + band); p.close()
            drawPath(p, Brush.verticalGradient(listOf(Color.Transparent, c.copy(alpha = (0.42f + 0.3f * bass).coerceAtMost(0.8f)), Color.Transparent), baseY - amp * 1.5f, baseY + amp * 1.5f + band))
        }
        PBg.Rain -> {
            for (i in 0 until 70) {
                val x = hash(i, 1) * w; val spd = 3 + i % 4
                val len = (16 + 24 * hash(i, 3)) * dp
                val y = ((ph * spd + hash(i, 2)) % 1f) * (h + len * 2) - len
                drawLine(col(1).copy(alpha = 0.18f + 0.35f * hash(i, 4)), Offset(x, y), Offset(x - len * 0.22f, y + len), 1.3f * dp)
            }
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, col(2).copy(alpha = 0.18f + 0.1f * bass)), h * 0.6f, h))
        }
        PBg.Fireflies -> for (i in 0 until 36) {
            val x = hash(i, 1) * w + sin(ph * TAU * (1 + i % 3) + i) * 30 * dp
            val y = hash(i, 2) * h + kotlin.math.cos(ph * TAU * (1 + (i + 1) % 3) + i * 1.7f) * 40 * dp
            val pulse = 0.5f + 0.5f * sin(ph * TAU * (2 + i % 4) + i)
            val r = (2.5f + 3.5f * hash(i, 3)) * dp * (1f + 0.4f * bass)
            val c = col(1 + i % max(1, cols.size - 1))
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.55f * pulse), Color.Transparent), Offset(x, y), r * 5f), r * 5f, Offset(x, y))
            drawCircle(c.copy(alpha = 0.5f + 0.5f * pulse), r, Offset(x, y))
        }
        PBg.Pulse -> {
            val ctr = Offset(w / 2f, h * 0.36f); val maxR = max(w, h) * 0.75f
            drawCircle(Brush.radialGradient(listOf(col(1).copy(alpha = 0.45f), Color.Transparent), ctr, w * (0.35f + 0.3f * bass)), w * (0.35f + 0.3f * bass), ctr)
            for (i in 0 until 5) {
                val t = (ph + i / 5f) % 1f
                drawCircle(col(1 + i % 2).copy(alpha = ((1f - t).pow(1.5f) * (0.30f + 0.5f * bass)).coerceIn(0f, 1f)), t * maxR, ctr, style = Stroke((2f + 10f * bass) * dp))
            }
        }
        PBg.Lava -> for (i in 0 until 7) {
            val cx = w * (0.5f + 0.38f * sin(ph * TAU * (1 + i % 2) + i * 1.9f))
            val cy = h * (0.5f + 0.42f * sin(ph * TAU * (1 + (i + 1) % 3) + i * 0.8f))
            val r = w * (0.28f + 0.12f * hash(i, 5)) * (1f + 0.2f * bass)
            drawCircle(Brush.radialGradient(listOf(col(1 + i).copy(alpha = 0.7f), Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
        }
        PBg.Snow -> for (i in 0 until 90) {
            val x = hash(i, 1) * w + sin(ph * TAU * (1 + i % 3) + i) * 14 * dp
            val y = ((ph * (1 + i % 3) + hash(i, 2)) % 1f) * (h + 20 * dp) - 10 * dp
            drawCircle(Color.White.copy(alpha = 0.45f + 0.45f * hash(i, 4)), (1f + 2.5f * hash(i, 3)) * dp, Offset(x, y))
        }
        PBg.Matrix -> {
            val n = 18; val cell = 17 * dp; val cw = w / n
            val glyphs = "01ｱｲｳｴｵｶｷｸｹｺ"
            val paint = android.graphics.Paint().apply { isAntiAlias = true; textSize = cell * 0.85f; textAlign = android.graphics.Paint.Align.CENTER; typeface = android.graphics.Typeface.MONOSPACE }
            val tick = (ph * 36).toInt()
            for (i in 0 until n) {
                val spd = 1 + i % 3
                val head = ((ph * spd + hash(i, 1)) % 1f) * (h + cell * 12) - cell * 2
                for (j in 0 until 11) {
                    val y = head - j * cell
                    if (y < -cell || y > h + cell) continue
                    val a = (1f - j / 11f).pow(2f)
                    val c = if (j == 0) lerp(col(1), Color.White, 0.7f) else col(1)
                    paint.color = c.copy(alpha = (0.15f + 0.75f * a + 0.2f * bass).coerceIn(0f, 1f)).toArgb()
                    drawContext.canvas.nativeCanvas.drawText(glyphs[(hash(i * 31 + j, tick / (2 + i % 3)) * glyphs.length).toInt().coerceIn(0, glyphs.length - 1)].toString(), i * cw + cw / 2, y, paint)
                }
            }
        }
        PBg.Bokeh -> for (i in 0 until 16) {
            val x = hash(i, 1) * w + sin(ph * TAU + i) * 20 * dp
            val r = (28 + 60 * hash(i, 3)) * dp * (1f + 0.25f * bass)
            val y = h + r - ((ph * (1 + i % 2) + hash(i, 2)) % 1f) * (h + r * 2)
            val a = 0.10f + 0.14f * hash(i, 4)
            drawCircle(col(1 + i).copy(alpha = a), r, Offset(x, y))
            drawCircle(col(1 + i).copy(alpha = a * 1.6f), r, Offset(x, y), style = Stroke(1.5f * dp))
        }
        PBg.Ripples -> for (i in 0 until 5) {
            val ox = w * (0.15f + 0.7f * hash(i, 1)); val oy = h * (0.3f + 0.6f * hash(i, 2))
            for (k in 0 until 3) {
                val t = (ph * 2 + i * 0.2f + k / 3f) % 1f
                val rx = t * w * 0.5f; val ry = rx * 0.35f
                drawOval(col(1 + i % 2).copy(alpha = ((1f - t) * (0.42f + 0.3f * bass)).coerceIn(0f, 1f)), Offset(ox - rx, oy - ry), Size(rx * 2, ry * 2), style = Stroke(1.6f * dp))
            }
        }
        PBg.Embers -> {
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, col(1).copy(alpha = 0.30f + 0.2f * bass)), h * 0.55f, h))
            for (i in 0 until 60) {
                val p = ((ph * (2 + i % 3) + hash(i, 2)) % 1f)
                val x = hash(i, 1) * w + sin(p * 6f + i) * 24 * dp
                val y = h - p * h * 1.1f
                val a = (1f - p) * (0.6f + 0.4f * sin(ph * TAU * 7 + i))
                drawCircle(lerp(col(1), col(2), p).copy(alpha = a.coerceIn(0f, 1f)), (1f + 2.5f * hash(i, 3)) * dp * (1f - p * 0.6f), Offset(x, y))
            }
        }
        PBg.Nebula -> {
            for (i in 0 until 5) {
                val cx = w * (0.5f + 0.35f * sin(ph * TAU + i * 1.3f)); val cy = h * (0.5f + 0.38f * sin(ph * TAU * (1 + i % 2) + i * 2.1f))
                val r = w * (0.55f + 0.2f * hash(i, 5)) * (1f + 0.1f * bass)
                drawCircle(Brush.radialGradient(listOf(col(1 + i).copy(alpha = 0.38f), Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
            }
            for (i in 0 until 60) drawCircle(Color.White.copy(alpha = 0.25f + 0.65f * (0.5f + 0.5f * sin(ph * TAU * (1 + i % 3) + i * 1.3f))), (0.5f + 1.2f * hash(i, 3)) * dp, Offset(hash(i, 1) * w, hash(i, 2) * h))
        }
        else -> {}
    }
}

private fun Color.toArgb(): Int = android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())
