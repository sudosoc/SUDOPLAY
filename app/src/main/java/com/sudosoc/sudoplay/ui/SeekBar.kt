package com.sudosoc.sudoplay.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.sudosoc.sudoplay.playback.FxBus
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

val SeekStyles: List<Pair<String, String>> = listOf(
    "classic" to "Classic", "linedot" to "Line + dot", "thick" to "Thick pill", "glow" to "Neon glow",
    "wave" to "Wave", "segments" to "Segments", "bars" to "Waveform", "dots" to "Dots",
    "blocks" to "Blocks", "ruler" to "Ruler", "liquid" to "Liquid", "hairline" to "Hairline", "live" to "Live spectrum",
)

/** Real peak envelope of the playing song (160 bins, 0..1) when the analyser has finished; null before that. */
val LocalWave = androidx.compose.runtime.compositionLocalOf<FloatArray?> { null }

private fun hash01(i: Int, seed: Long): Float {
    var x = (i * 374761393L + seed * 668265263L) xor 0x5bd1e995L
    x = (x xor (x shr 13)) * 1274126177L
    x = x xor (x shr 16)
    return ((x and 0xFFFF).toInt()) / 65535f
}

/**
 * The song progress bar. [progress] is 0..1. Tap or drag anywhere on it; [onChange] fires while the finger moves
 * and [onDone] when it lifts. [seed] makes the waveform styles look different for every song.
 */
@Composable
fun SeekBar(
    style: String,
    progress: Float,
    onChange: (Float) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    seed: Long = 0L,
    interactive: Boolean = true,
    animated: Boolean = true,
) {
    val t = LocalSudo.current
    val cbChange by rememberUpdatedState(onChange)
    val cbDone by rememberUpdatedState(onDone)
    val phaseState = rememberPhase(animated, 2200)
    val wave = LocalWave.current
    val p = progress.coerceIn(0f, 1f)

    val gesture = if (!interactive) Modifier else Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            val w = size.width.toFloat().coerceAtLeast(1f)
            cbChange((down.position.x / w).coerceIn(0f, 1f))
            while (true) {
                val ev = awaitPointerEvent()
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                ch.consume()
                cbChange((ch.position.x / w).coerceIn(0f, 1f))
                if (!ch.pressed) break
            }
            cbDone()
        }
    }

    val h = when (style) { "bars", "live", "wave" -> 52.dp; "ruler" -> 46.dp; else -> 38.dp }
    Canvas(modifier.fillMaxWidth().height(h).then(gesture)) {
        val phase = phaseState.value
        val on = t.primary
        val off = t.text.copy(alpha = 0.2f)
        when (style) {
            "linedot" -> {
                line(2.dp.toPx(), p, on, off)
                val c = Offset(size.width * p, size.height / 2)
                drawCircle(on.copy(alpha = 0.25f), 11.dp.toPx(), c)
                drawCircle(on, 6.dp.toPx(), c)
            }
            "thick" -> {
                val th = 16.dp.toPx(); val y = (size.height - th) / 2
                drawRoundRect(off, Offset(0f, y), Size(size.width, th), CornerRadius(th / 2))
                if (p > 0f) drawRoundRect(Brush.horizontalGradient(listOf(on, t.secondary)), Offset(0f, y), Size(max(th, size.width * p), th), CornerRadius(th / 2))
            }
            "glow" -> {
                val y = size.height / 2; val x = size.width * p
                drawLine(off, Offset(0f, y), Offset(size.width, y), 3.dp.toPx(), StrokeCap.Round)
                for (i in 4 downTo 1) drawLine(on.copy(alpha = 0.10f), Offset(0f, y), Offset(x, y), (3 + i * 4).dp.toPx(), StrokeCap.Round)
                drawLine(Brush.horizontalGradient(listOf(on, t.secondary), 0f, max(1f, x)), Offset(0f, y), Offset(x, y), 4.dp.toPx(), StrokeCap.Round)
                drawCircle(Brush.radialGradient(listOf(on.copy(alpha = 0.7f), Color.Transparent), Offset(x, y), 18.dp.toPx()), 18.dp.toPx(), Offset(x, y))
                drawCircle(Color.White, 5.dp.toPx(), Offset(x, y))
            }
            "wave" -> {
                val mid = size.height / 2; val x = size.width * p
                fun path(from: Float, to: Float, amp: Float): Path = Path().apply {
                    var px = from
                    moveTo(px, mid + amp * sin((px / 38.dp.toPx() - phase * 2) * PI.toFloat()))
                    while (px < to) { px += 3f; lineTo(px.coerceAtMost(to), mid + amp * sin((px / 38.dp.toPx() - phase * 2) * PI.toFloat())) }
                }
                drawPath(path(x, size.width, 1.5.dp.toPx()), off, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                drawPath(path(0f, x, 7.dp.toPx()), on, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                drawCircle(on, 7.dp.toPx(), Offset(x, mid + 7.dp.toPx() * sin((x / 38.dp.toPx() - phase * 2) * PI.toFloat())))
            }
            "segments" -> {
                val n = 28; val gap = 3.dp.toPx(); val sw = (size.width - gap * (n - 1)) / n; val th = 12.dp.toPx()
                for (i in 0 until n) drawRoundRect(
                    if ((i + 0.5f) / n <= p) lerp(on, t.secondary, i / n.toFloat()) else off,
                    Offset(i * (sw + gap), (size.height - th) / 2), Size(sw, th), CornerRadius(3.dp.toPx()),
                )
            }
            "bars" -> bars(p, on, off, if (wave != null) 80 else 56, seed, 3f, wave)
            "dots" -> {
                val n = 30; val y = size.height / 2
                for (i in 0 until n) {
                    val f = (i + 0.5f) / n
                    drawCircle(if (f <= p) on else off, (if (abs(f - p) < 0.5f / n + 0.015f) 7.dp else 3.dp).toPx(), Offset(size.width * f, y))
                }
            }
            "blocks" -> {
                val n = 20; val gap = 2.dp.toPx(); val bw = (size.width - gap * (n - 1)) / n; val th = 22.dp.toPx()
                for (i in 0 until n) drawRect(
                    if ((i + 0.5f) / n <= p) lerp(on, t.secondary, i / n.toFloat()) else off,
                    Offset(i * (bw + gap), (size.height - th) / 2), Size(bw, th),
                )
            }
            "ruler" -> {
                val step = 7.dp.toPx(); var i = 0; var x = 0f
                while (x <= size.width) {
                    val big = i % 5 == 0
                    val len = (if (big) 20.dp else 11.dp).toPx()
                    drawLine(if (x / size.width <= p) on else off, Offset(x, size.height / 2 - len / 2 + 6.dp.toPx()), Offset(x, size.height / 2 + len / 2 + 6.dp.toPx()), if (big) 2.dp.toPx() else 1.2.dp.toPx(), StrokeCap.Round)
                    x += step; i++
                }
                val nx = size.width * p
                drawLine(t.secondary, Offset(nx, 2.dp.toPx()), Offset(nx, size.height - 2.dp.toPx()), 2.5.dp.toPx(), StrokeCap.Round)
                drawCircle(t.secondary, 4.dp.toPx(), Offset(nx, 3.dp.toPx()))
            }
            "liquid" -> {
                val th = 14.dp.toPx(); val y = (size.height - th) / 2; val x = size.width * p
                drawRoundRect(off, Offset(0f, y), Size(size.width, th), CornerRadius(th / 2))
                if (x > 0f) drawRoundRect(Brush.horizontalGradient(listOf(t.secondary, on)), Offset(0f, y), Size(max(th, x), th), CornerRadius(th / 2))
                val c = Offset(x.coerceIn(th, size.width - th / 2), size.height / 2)
                drawCircle(Brush.radialGradient(listOf(Color.White, on), c - Offset(4.dp.toPx(), 4.dp.toPx()), 14.dp.toPx()), 11.dp.toPx(), c)
                drawCircle(Color.White.copy(alpha = 0.55f), 3.dp.toPx(), c - Offset(3.5.dp.toPx(), 3.5.dp.toPx()))
            }
            "hairline" -> {
                val y = size.height / 2; val x = size.width * p
                drawLine(off, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                drawLine(on, Offset(0f, y), Offset(x, y), 1.5.dp.toPx())
                drawLine(on, Offset(x, y - 9.dp.toPx()), Offset(x, y + 9.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
            }
            "live" -> {
                val fresh = SystemClock.elapsedRealtime() - FxBus.lastUpdate < 350
                val lv = FxBus.levels
                val n = 32; val gap = 2.5.dp.toPx(); val bw = (size.width - gap * (n - 1)) / n
                val base = size.height - 8.dp.toPx()
                for (i in 0 until n) {
                    val v = if (fresh) lv[i * lv.size / n] else 0.06f + 0.05f * hash01(i, seed)
                    val bh = max(3.dp.toPx(), v * (base - 2.dp.toPx()))
                    val f = (i + 0.5f) / n
                    drawRoundRect(
                        if (f <= p) lerp(on, t.secondary, v) else off,
                        Offset(i * (bw + gap), base - bh), Size(bw, bh), CornerRadius(bw / 2),
                    )
                }
                drawRoundRect(off, Offset(0f, size.height - 4.dp.toPx()), Size(size.width, 3.dp.toPx()), CornerRadius(2.dp.toPx()))
                drawRoundRect(on, Offset(0f, size.height - 4.dp.toPx()), Size(size.width * p, 3.dp.toPx()), CornerRadius(2.dp.toPx()))
            }
            else -> { // classic
                line(4.dp.toPx(), p, on, off)
                drawCircle(on, 8.dp.toPx(), Offset(size.width * p, size.height / 2))
            }
        }
    }
}

private fun DrawScope.line(th: Float, p: Float, on: Color, off: Color) {
    val y = size.height / 2
    drawLine(off, Offset(0f, y), Offset(size.width, y), th, StrokeCap.Round)
    if (p > 0f) drawLine(on, Offset(0f, y), Offset(size.width * p, y), th, StrokeCap.Round)
}

private fun DrawScope.bars(p: Float, on: Color, off: Color, n: Int, seed: Long, bwFactor: Float, wave: FloatArray? = null) {
    val gap = bwFactor.dp.toPx() * 0.55f
    val bw = (size.width - gap * (n - 1)) / n
    for (i in 0 until n) {
        val v = if (wave != null && wave.isNotEmpty()) {
            val a = (i * wave.size / n).coerceIn(0, wave.size - 1)
            val b = ((i + 1) * wave.size / n).coerceIn(a + 1, wave.size)
            var m = 0f; for (k in a until b) m = maxOf(m, wave[k]); m
        } else hash01(i, seed)
        val h = size.height * (if (wave != null) 0.08f + 0.92f * v else 0.22f + 0.78f * v)
        val f = (i + 0.5f) / n
        drawRoundRect(if (f <= p) on else off, Offset(i * (bw + gap), (size.height - h) / 2), Size(bw, h), CornerRadius(bw / 2))
    }
}
