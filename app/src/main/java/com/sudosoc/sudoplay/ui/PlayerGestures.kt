package com.sudosoc.sudoplay.ui

import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.sudosoc.sudoplay.PlayerViewModel
import com.sudosoc.sudoplay.online.DownloadCenter
import com.sudosoc.sudoplay.data.AppSettings
import com.sudosoc.sudoplay.data.Song
import com.sudosoc.sudoplay.playback.FxBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign
import kotlin.math.sin

// ============================================================================ gestures
/**
 * Whole-page gestures. Children (buttons, seek bar, lists) get first refusal: anything they consume is ignored here.
 *  - double-tap                → onDoubleTap
 *  - horizontal swipe          → onNext / onPrev (the page stretches like rubber while you drag)
 *  - drag down                 → closes the page; it follows the finger (sheet callbacks)
 *  - long-press, then drag     → scrub (onScrubStart / onScrub(fraction of width) / onScrubEnd)
 *  - long-press, no drag, on the cover → onLongPressArt
 */
@Composable
internal fun Modifier.playerGestures(
    doubleTap: Boolean, swipe: Boolean, scrub: Boolean, hasUp: Boolean, hasTwo: Boolean,
    artBounds: () -> Rect?,
    onDoubleTap: (Int) -> Unit, onSwipe: (Boolean) -> Unit, onSwipeUp: () -> Unit, onTwoFinger: () -> Unit,
    onLongPressRelease: (Boolean) -> Unit, onLongPressStart: () -> Unit,
    onScrubStart: () -> Unit, onScrub: (Float) -> Unit, onScrubEnd: () -> Unit,
    onStretch: (Float, Boolean) -> Unit,
    onSheetStart: () -> Unit, onSheetDrag: (Float) -> Unit, onSheetSettle: (Float) -> Unit,
): Modifier {
    val cbDouble by rememberUpdatedState(onDoubleTap)
    val cbSwipe by rememberUpdatedState(onSwipe)
    val cbUp by rememberUpdatedState(onSwipeUp)
    val cbTwo by rememberUpdatedState(onTwoFinger)
    val cbArt by rememberUpdatedState(onLongPressRelease)
    val cbLong by rememberUpdatedState(onLongPressStart)
    val cbScrubStart by rememberUpdatedState(onScrubStart)
    val cbScrub by rememberUpdatedState(onScrub)
    val cbScrubEnd by rememberUpdatedState(onScrubEnd)
    val cbStretch by rememberUpdatedState(onStretch)
    val cbSheetStart by rememberUpdatedState(onSheetStart)
    val cbSheetDrag by rememberUpdatedState(onSheetDrag)
    val cbSheetSettle by rememberUpdatedState(onSheetSettle)
    val bounds by rememberUpdatedState(artBounds)

    return pointerInput(doubleTap, swipe, scrub, hasUp, hasTwo) {
        var lastTapTime = 0L
        var lastTapPos = Offset.Zero
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            val slop = viewConfiguration.touchSlop
            val start = down.position
            var upChange: PointerInputChange? = null
            var firstPos = start

            val first = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val ev = awaitPointerEvent()
                    if (hasTwo && ev.changes.count { it.pressed } >= 2) return@withTimeoutOrNull "two"
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull "cancel"
                    if (ch.isConsumed) return@withTimeoutOrNull "cancel"
                    if (!ch.pressed) { upChange = ch; return@withTimeoutOrNull "up" }
                    if ((ch.position - start).getDistance() > slop) { firstPos = ch.position; return@withTimeoutOrNull "move" }
                }
                @Suppress("UNREACHABLE_CODE") "cancel"
            }

            when (first) {
                "cancel" -> return@awaitEachGesture
                "two" -> {
                    var moved = false
                    val done = withTimeoutOrNull(700L) {
                        while (true) {
                            val ev = awaitPointerEvent()
                            if (ev.changes.all { !it.pressed }) return@withTimeoutOrNull true
                            if (ev.changes.any { (it.position - it.previousPosition).getDistance() > slop * 2 }) moved = true
                        }
                        @Suppress("UNREACHABLE_CODE") false
                    }
                    if (done == true && !moved) cbTwo()
                }
                "up" -> {
                    val up = upChange ?: return@awaitEachGesture
                    if (up.isConsumed || !doubleTap) { lastTapTime = 0L; return@awaitEachGesture }
                    val now = up.uptimeMillis
                    if (lastTapTime != 0L && now - lastTapTime <= viewConfiguration.doubleTapTimeoutMillis &&
                        (up.position - lastTapPos).getDistance() < 120.dp.toPx()
                    ) {
                        lastTapTime = 0L
                        cbDouble((up.position.x / size.width.toFloat() * 3f).toInt().coerceIn(0, 2))
                    } else {
                        lastTapTime = now; lastTapPos = up.position
                    }
                }
                "move" -> {
                    // Deltas (not absolute positions): the page itself moves while we drag it down.
                    var total = firstPos - start
                    var horizontal = abs(total.x) > abs(total.y) * 1.2f
                    var sheetMode = false
                    var accY = 0f
                    val vt = VelocityTracker()
                    if (!horizontal) {
                        if (total.y <= 0f) {   // upward drags belong to the lists, unless the user bound a swipe-up action
                            if (hasUp) {
                                var tot = total
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    if (ch.isConsumed) return@awaitEachGesture
                                    tot += ch.positionChange()
                                    if (!ch.pressed) break
                                }
                                if (-tot.y > 72.dp.toPx() && -tot.y > abs(tot.x) * 1.2f) cbUp()
                            }
                            return@awaitEachGesture
                        }
                        sheetMode = true
                        cbSheetStart(); cbSheetDrag(total.y); accY = total.y
                        vt.addPosition(down.uptimeMillis, Offset.Zero)
                        vt.addPosition(down.uptimeMillis + 1, Offset(0f, accY))
                    }
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (ch.isConsumed) break
                        val pc = ch.positionChange()
                        total += pc
                        if (horizontal) {
                            ch.consume()
                            cbStretch(total.x, true)
                        } else if (sheetMode) {
                            ch.consume()
                            if (pc.y != 0f) { cbSheetDrag(pc.y); accY += pc.y; vt.addPosition(ch.uptimeMillis, Offset(0f, accY)) }
                        }
                        if (!ch.pressed) break
                    }
                    if (sheetMode) {
                        cbSheetSettle(vt.calculateVelocity().y)
                    } else if (horizontal) {
                        cbStretch(0f, false)
                        if (swipe && abs(total.x) > 72.dp.toPx()) cbSwipe(total.x > 0)
                    }
                }
                else -> { // long press
                    val onArt = bounds()?.contains(start) == true
                    if (onArt) cbLong()
                    var scrubbing = false
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (ch.isConsumed && !scrubbing) return@awaitEachGesture
                        val dx = ch.position.x - start.x
                        if (!scrubbing && scrub && abs(dx) > slop * 2) { scrubbing = true; cbScrubStart() }
                        if (scrubbing) { cbScrub(dx / size.width); cbStretch(dx, true); ch.consume() }
                        if (!ch.pressed) break
                    }
                    if (scrubbing) { cbScrubEnd(); cbStretch(0f, false) } else cbArt(onArt)
                }
            }
        }
    }
}

// ============================================================================ gesture feedback
/** Little themed badge that pops, sends out rings and (for next / previous) chevrons in the swipe direction. */
@Composable
internal fun GestureHud(ev: HudEvent?, modifier: Modifier = Modifier) {
    val t = LocalSudo.current
    val prog = remember { Animatable(1f) }
    val pop = remember { Animatable(0f) }
    var last by remember { mutableStateOf<HudEvent?>(null) }
    LaunchedEffect(ev?.id) {
        if (ev == null) return@LaunchedEffect
        last = ev
        launch { pop.snapTo(0.3f); pop.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 420f)) }
        prog.snapTo(0f)
        prog.animateTo(1f, tween(760, easing = LinearEasing))
    }
    val e = last ?: return
    val p = prog.value
    if (p >= 1f) return
    val fade = 1f - ((p - 0.65f) / 0.35f).coerceIn(0f, 1f)

    Box(modifier.size(240.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            for (k in 0..1) {
                val q = ((p - k * 0.16f) / (1f - k * 0.16f)).coerceIn(0f, 1f)
                if (q <= 0f) continue
                val r = size.minDimension * (0.19f + 0.30f * q)
                drawCircle(
                    Brush.sweepGradient(listOf(t.primary, t.secondary, t.primary), c), r,
                    style = Stroke(width = (3.5f * (1f - q) + 0.8f).dp.toPx()), alpha = (1f - q) * 0.9f,
                )
            }
            if (e.dir != 0) for (i in 0..2) {
                val q = ((p * 1.25f) - i * 0.12f).coerceIn(0f, 1f)
                val x = c.x + e.dir * (size.minDimension * 0.27f + i * 16.dp.toPx() + q * 34.dp.toPx())
                val a = (1f - q) * (0.9f - i * 0.2f)
                val d = e.dir
                val path = Path().apply {
                    moveTo(x - 6.dp.toPx() * d, c.y - 11.dp.toPx())
                    lineTo(x + 4.dp.toPx() * d, c.y)
                    lineTo(x - 6.dp.toPx() * d, c.y + 11.dp.toPx())
                }
                drawPath(path, lerp(t.primary, t.secondary, i / 2f).copy(alpha = a.coerceIn(0f, 1f)), style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        val badge = CircleShape
        Box(
            Modifier.size(92.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = fade }
                .sudoPanel(badge)
                .background(Brush.linearGradient(listOf(t.primary.copy(alpha = 0.40f), t.secondary.copy(alpha = 0.40f))), badge)
                .border(2.5.dp, Brush.sweepGradient(listOf(t.primary, t.secondary, t.primary)), badge),
            contentAlignment = Alignment.Center,
        ) {
            Icon(e.icon, null, tint = t.text, modifier = Modifier.size(46.dp))
        }
    }
}

@Composable
internal fun ScrubOverlay(delta: Long, target: Long, duration: Long) {
    val t = LocalSudo.current
    val shape = RoundedCornerShape(30.dp)
    Column(
        Modifier.sudoPanel(shape).background(Color.Black.copy(alpha = if (t.dark) 0.25f else 0.05f), shape)
            .border(1.2.dp, Brush.linearGradient(listOf(t.primary, t.secondary)), shape)
            .padding(horizontal = 30.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val sec = delta / 1000
        Text(
            (if (sec >= 0) "+" else "−") + "${abs(sec)}s", color = t.primary, fontSize = 38.sp, fontWeight = FontWeight.Bold,
            style = TextStyle(fontFeatureSettings = "tnum"),
        )
        Text(fmt(target) + " / " + fmt(duration), color = t.text.copy(alpha = 0.85f), style = TextStyle(fontFeatureSettings = "tnum"))
        Spacer(Modifier.height(10.dp))
        Canvas(Modifier.width(190.dp).height(8.dp)) {
            val f = if (duration > 0) (target.toFloat() / duration).coerceIn(0f, 1f) else 0f
            drawRoundRect(t.text.copy(alpha = 0.2f), Offset.Zero, size, CornerRadius(4.dp.toPx()))
            drawRoundRect(Brush.horizontalGradient(listOf(t.primary, t.secondary)), Offset.Zero, Size(size.width * f, size.height), CornerRadius(4.dp.toPx()))
            drawCircle(t.text, 6.dp.toPx(), Offset(size.width * f, size.height / 2))
        }
    }
}

