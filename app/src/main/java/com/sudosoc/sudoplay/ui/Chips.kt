package com.sudosoc.sudoplay.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.layout.RowScope
import com.sudosoc.sudoplay.PlayerViewModel
import com.sudosoc.sudoplay.data.Haptics
import com.sudosoc.sudoplay.playback.FxBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
private fun Modifier.chipLook(active: Boolean): Modifier {
    val t = LocalSudo.current
    val shape = RoundedCornerShape(50)
    return if (active) this
        .background(Brush.horizontalGradient(listOf(t.primary.copy(alpha = 0.28f), t.secondary.copy(alpha = 0.28f))), shape)
        .border(1.2.dp, Brush.horizontalGradient(listOf(t.primary, t.secondary)), shape)
    else this.sudoPanel(shape)
}

private class AboveAnchor : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
        IntOffset(
            (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(12, (windowSize.width - popupContentSize.width - 12).coerceAtLeast(12)),
            (anchorBounds.top - popupContentSize.height - 14).coerceAtLeast(12),
        )
}

/** Big themed read-out shown while the finger slides along a chip's range. */
@Composable
private fun DragDial(label: String, frac: Float) {
    val t = LocalSudo.current
    Column(
        Modifier.width(260.dp).sudoPanel(RoundedCornerShape(24.dp)).border(1.dp, t.primary.copy(alpha = 0.35f), RoundedCornerShape(24.dp)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = t.primary, fontSize = 30.sp, fontWeight = FontWeight.Bold, style = TextStyle(fontFeatureSettings = "tnum"))
        Spacer(Modifier.height(10.dp))
        Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            val n = 41
            for (i in 0 until n) {
                val f = i / (n - 1f)
                val big = i % 5 == 0
                val x = size.width * f
                val near = abs(f - frac) < 0.04f
                drawLine(
                    if (near) t.primary else t.text.copy(alpha = if (f <= frac) 0.55f else 0.22f),
                    Offset(x, size.height / 2 - (if (big) 9.dp else 5.dp).toPx()), Offset(x, size.height / 2 + (if (big) 9.dp else 5.dp).toPx()),
                    (if (near) 3.dp else 1.5.dp).toPx(),
                )
            }
            drawCircle(t.secondary, 6.dp.toPx(), Offset(size.width * frac, size.height / 2))
        }
    }
}

/**
 * A chip with three behaviours:
 *  - tap: toggle on/off
 *  - long-press then slide: pick a value along [steps] (a live dial follows the finger), release to apply
 *  - long-press then release without sliding: open a menu
 */
@Composable
fun ValueChip(
    icon: ImageVector,
    text: String,
    active: Boolean,
    haptics: Haptics,
    steps: List<String>,
    startIndex: () -> Int,
    onToggle: () -> Unit,
    onPick: (Int) -> Unit,
    menuContent: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalSudo.current
    val cbToggle by rememberUpdatedState(onToggle)
    val cbPick by rememberUpdatedState(onPick)
    val cbStart by rememberUpdatedState(startIndex)
    var dragIdx by remember { mutableStateOf<Int?>(null) }
    var menu by remember { mutableStateOf(false) }

    Box(modifier) {
        Row(
            Modifier.chipLook(active).pointerInput(steps.size) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val slop = viewConfiguration.touchSlop * 2
                    val r = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull 2
                            ch.consume()
                            if (!ch.pressed) return@withTimeoutOrNull 1
                            if ((ch.position - down.position).getDistance() > slop) return@withTimeoutOrNull 2
                        }
                        @Suppress("UNREACHABLE_CODE") 2
                    }
                    if (r == 1) { cbToggle(); haptics.click(); return@awaitEachGesture }
                    if (r == 2) return@awaitEachGesture

                    // long press
                    haptics.heavy()
                    val n = steps.size
                    val start = cbStart().coerceIn(0, n - 1)
                    var idx = start
                    var dragged = false
                    val sweep = 300.dp.toPx()
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        ch.consume()
                        val dx = ch.position.x - down.position.x
                        if (!dragged && abs(dx) > viewConfiguration.touchSlop) dragged = true
                        if (dragged) {
                            val ni = (start + dx / sweep * (n - 1)).roundToInt().coerceIn(0, n - 1)
                            if (ni != idx) { idx = ni; haptics.tick() }
                            dragIdx = idx
                        }
                        if (!ch.pressed) break
                    }
                    dragIdx = null
                    if (dragged) { cbPick(idx); haptics.click() } else menu = true
                }
            }.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(icon, null, tint = if (active) t.primary else t.text, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text, color = if (active) t.primary else t.text, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                style = TextStyle(fontFeatureSettings = "tnum"), maxLines = 1,
            )
        }
        SudoMenu(menu, { menu = false }) { menuContent { menu = false } }
        dragIdx?.let { i ->
            Popup(popupPositionProvider = AboveAnchor(), properties = PopupProperties(focusable = false)) {
                DragDial(steps[i], i / max(1f, steps.size - 1f))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- speed
private val speedSteps = List(56) { (25 + it * 5) }                 // 0.25x … 3.00x
private fun speedText(f: Float) = "%.2f".format(f).trimEnd('0').trimEnd('.') + "x"

@Composable
fun SpeedChip(vm: PlayerViewModel) {
    val steps = remember { speedSteps.map { speedText(it / 100f) } }
    ValueChip(
        icon = I.Speed, text = speedText(vm.speed), active = abs(vm.speed - 1f) > 0.001f, haptics = vm.haptics,
        steps = steps,
        startIndex = { ((vm.speed * 100).roundToInt() - 25) / 5 },
        onToggle = { if (abs(vm.speed - 1f) > 0.001f) vm.changeSpeedTo(1f) else vm.changeSpeedTo(vm.settings.lastSpeedPct.value / 100f) },
        onPick = { vm.changeSpeedTo(speedSteps[it] / 100f) },
        menuContent = { dismiss ->
            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f).forEach { sp ->
                SudoMenuItem(speedText(sp), abs(vm.speed - sp) < 0.001f) { vm.changeSpeedTo(sp); dismiss() }
            }
        },
    )
}

// ---------------------------------------------------------------------------------------------- sleep
private val sleepSteps = listOf(1, 2, 3, 5, 7, 10, 12, 15, 20, 25, 30, 40, 45, 50, 60, 75, 90, 105, 120, 150, 180, -1)

private fun countdown(ms: Long): String {
    val total = ceil(ms / 1000.0).toLong().coerceAtLeast(0)
    val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun sleepText(m: Int) = when {
    m < 0 -> "End of song"
    m >= 60 && m % 60 == 0 -> "${m / 60} h"
    m > 60 -> "${m / 60} h ${m % 60} min"
    else -> "$m min"
}

/** While a timer runs the chip becomes a live mm:ss countdown. */
@Composable
fun SleepChip(vm: PlayerViewModel) {
    val now by produceState(System.currentTimeMillis(), vm.sleepEndsAt) {
        value = System.currentTimeMillis()
        while (vm.sleepEndsAt != 0L) { delay(1000); value = System.currentTimeMillis() }
    }
    val remain = vm.sleepEndsAt - now
    val timed = vm.sleepEndsAt != 0L && remain > 0
    val active = timed || vm.sleepEndOfSong
    val steps = remember { sleepSteps.map(::sleepText) }
    ValueChip(
        icon = I.Bedtime,
        text = when { timed -> countdown(remain); vm.sleepEndOfSong -> "End of song"; else -> "Sleep" },
        active = active, haptics = vm.haptics, steps = steps,
        startIndex = {
            val want = if (timed) ((remain + 59_999) / 60_000).toInt() else vm.settings.lastSleepMin.value
            sleepSteps.indexOfFirst { it >= want }.let { if (it < 0) sleepSteps.size - 2 else it }
        },
        onToggle = { if (active) vm.setSleep(0) else vm.setSleep(vm.settings.lastSleepMin.value) },
        onPick = { vm.setSleep(sleepSteps[it]) },
        menuContent = { dismiss ->
            if (active) {
                if (timed) SudoMenuItem("+5 min") { vm.extendSleep(5); dismiss() }
                SudoMenuItem("Turn off", true) { vm.setSleep(0); dismiss() }
            }
            listOf(5, 10, 15, 20, 30, 45, 60, 90, 120, -1).forEach { m ->
                SudoMenuItem(sleepText(m)) { vm.setSleep(m); dismiss() }
            }
        },
    )
}

// ---------------------------------------------------------------------------------------------- EQ
/** Tap toggles the sound engine. Long-press opens the EQ studio. The bars dance with the music while it's on. */
@Composable
fun EqChip(vm: PlayerViewModel, onOpen: () -> Unit) {
    val t = LocalSudo.current
    val on = vm.fx.enabled
    val cbOpen by rememberUpdatedState(onOpen)
    val tr = rememberInfiniteTransition(label = "eqchip")
    val ph by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "ph")
    Row(
        Modifier.chipLook(on).pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                val r = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull 2
                        ch.consume()
                        if (!ch.pressed) return@withTimeoutOrNull 1
                        if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop * 2) return@withTimeoutOrNull 2
                    }
                    @Suppress("UNREACHABLE_CODE") 2
                }
                when (r) {
                    1 -> vm.toggleFx()
                    null -> { vm.haptics.heavy(); cbOpen(); // swallow the release
                        while (true) { val ev = awaitPointerEvent(); ev.changes.forEach { it.consume() }; if (ev.changes.none { it.pressed }) break } }
                }
            }
        }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val lv = FxBus.levels
            val bw = size.width / 9f
            val idx = intArrayOf(3, 10, 17, 24)
            for (i in 0 until 4) {
                val v = if (on) (lv[idx[i]] * 0.85f + 0.12f + 0.1f * kotlin.math.sin((ph * 6.283f) + i * 1.7f)).coerceIn(0.12f, 1f) else 0.3f + 0.1f * (i % 2)
                val bh = size.height * v
                drawRoundRect(
                    if (on) t.primary else t.text, Offset(bw * (1 + i * 2f) - bw * 0.2f, size.height - bh), Size(bw * 1.4f, bh), CornerRadius(bw / 2),
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text("EQ", color = if (on) t.primary else t.text, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
    }
}

/** Non-interactive chip used by the theme previews. */
@Composable
fun StaticChip(icon: ImageVector, text: String, active: Boolean = false) {
    val t = LocalSudo.current
    Row(Modifier.chipLook(active).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Icon(icon, null, tint = if (active) t.primary else t.text, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = if (active) t.primary else t.text, maxLines = 1)
    }
}
