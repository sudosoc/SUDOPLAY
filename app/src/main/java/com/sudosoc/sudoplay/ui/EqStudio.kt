package com.sudosoc.sudoplay.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sudosoc.sudoplay.PlayerViewModel
import com.sudosoc.sudoplay.data.EqPresets
import com.sudosoc.sudoplay.data.FxScenes
import com.sudosoc.sudoplay.data.FxState
import com.sudosoc.sudoplay.playback.FxBus
import kotlin.math.abs
import kotlin.math.max

@Composable
private fun sliderColors(): SliderColors {
    val t = LocalSudo.current
    return SliderDefaults.colors(thumbColor = t.primary, activeTrackColor = t.primary, inactiveTrackColor = t.text.copy(alpha = 0.2f))
}

/** Full-screen sound studio: live spectrum + draggable EQ curve, scenes, level, surround, reverb and echo. */
@Composable
fun EqStudio(vm: PlayerViewModel, onClose: () -> Unit) {
    val t = LocalSudo.current
    val fx = vm.fx
    var saving by remember { mutableStateOf(false) }
    BackHandler(onBack = onClose)

    SudoBackground(vm.current) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClose) { Icon(I.KeyboardArrowDown, null, tint = t.text, modifier = Modifier.size(30.dp)) }
                Column(Modifier.weight(1f)) {
                    Text("Sound studio", color = t.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(if (fx.preset.isNotEmpty()) fx.preset else "Custom", color = t.text.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
                }
                Switch(fx.enabled, { vm.toggleFx() }, colors = SwitchDefaults.colors(checkedTrackColor = t.primary, checkedThumbColor = t.onAccent))
                Spacer(Modifier.width(8.dp))
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Spectrum(vm)

                // ---- scenes
                Label("Scenes")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FxScenes.builtIn.forEach { sc -> Pill(sc.name, fx.preset == sc.name) { vm.applyScene(sc) } }
                    vm.userScenes.forEach { (n, st) ->
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            Pill("★ $n", fx.preset == n, onLong = { menu = true }) { vm.applyUserScene(n, st) }
                            SudoMenu(menu, { menu = false }) { SudoMenuItem("Delete scene") { vm.deleteScene(n); menu = false } }
                        }
                    }
                    Pill("+ Save current", false) { saving = true }
                }

                Label("EQ curve")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EqPresets.curves.keys.forEach { n -> Pill(n, fx.preset == n) { vm.applyCurve(n) } }
                }

                FxCard("Level", I.Tune, null, {}) {
                    FxSlider("Preamp (headroom)", "${fx.preamp / 10f} dB", fx.preamp.toFloat(), -120f..0f, { v -> vm.fxEdit(false) { it.copy(preamp = v.toInt(), preset = "") } }) { vm.fxSave() }
                    FxSlider("Bass boost", "${fx.bass / 10}%", fx.bass.toFloat(), 0f..1000f, { v -> vm.fxEdit(false) { it.copy(bass = v.toInt(), preset = "") } }) { vm.fxSave() }
                    FxSlider("Loudness", "${fx.loud / 10}%", fx.loud.toFloat(), 0f..1000f, { v -> vm.fxEdit(false) { it.copy(loud = v.toInt(), preset = "") } }) { vm.fxSave() }
                }

                FxCard("Surround & space", I.Explore, fx.virtOn, { on -> vm.fxEdit { it.copy(virtOn = on, preset = "") } }) {
                    FxSlider("Virtual surround", "${fx.virt / 10}%", fx.virt.toFloat(), 0f..1000f, { v -> vm.fxEdit(false) { it.copy(virt = v.toInt(), virtOn = true, preset = "") } }) { vm.fxSave() }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Auto", "Headphones", "Speakers").forEachIndexed { i, n -> Pill(n, fx.virtMode == i) { vm.fxEdit { it.copy(virtMode = i, virtOn = true, preset = "") } } }
                    }
                    SubToggle("Stereo width", fx.widthOn) { on -> vm.fxEdit { it.copy(widthOn = on, preset = "") } }
                    FxSlider("Width", "${fx.width}%", fx.width.toFloat(), 0f..200f, { v -> vm.fxEdit(false) { it.copy(width = v.toInt(), widthOn = true, preset = "") } }) { vm.fxSave() }
                }

                FxCard("Reverb", I.Album, fx.reverbOn, { on -> vm.fxEdit { it.copy(reverbOn = on, preset = "") } }) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Room", false) { vm.fxEdit { it.copy(reverbOn = true, room = 35, damp = 55, reverbMix = 20, preset = "") } }
                        Pill("Studio", false) { vm.fxEdit { it.copy(reverbOn = true, room = 50, damp = 40, reverbMix = 24, preset = "") } }
                        Pill("Hall", false) { vm.fxEdit { it.copy(reverbOn = true, room = 75, damp = 40, reverbMix = 32, preset = "") } }
                        Pill("Cathedral", false) { vm.fxEdit { it.copy(reverbOn = true, room = 95, damp = 25, reverbMix = 50, preset = "") } }
                    }
                    FxSlider("Room size", "${fx.room}%", fx.room.toFloat(), 0f..100f, { v -> vm.fxEdit(false) { it.copy(room = v.toInt(), reverbOn = true, preset = "") } }) { vm.fxSave() }
                    FxSlider("Damping", "${fx.damp}%", fx.damp.toFloat(), 0f..100f, { v -> vm.fxEdit(false) { it.copy(damp = v.toInt(), reverbOn = true, preset = "") } }) { vm.fxSave() }
                    FxSlider("Mix", "${fx.reverbMix}%", fx.reverbMix.toFloat(), 0f..100f, { v -> vm.fxEdit(false) { it.copy(reverbMix = v.toInt(), reverbOn = true, preset = "") } }) { vm.fxSave() }
                }

                FxCard("Echo", I.Repeat, fx.echoOn, { on -> vm.fxEdit { it.copy(echoOn = on, preset = "") } }) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Slapback", false) { vm.fxEdit { it.copy(echoOn = true, echoMs = 110, echoFb = 15, echoMix = 28, preset = "") } }
                        Pill("Delay", false) { vm.fxEdit { it.copy(echoOn = true, echoMs = 320, echoFb = 35, echoMix = 30, preset = "") } }
                        Pill("Long", false) { vm.fxEdit { it.copy(echoOn = true, echoMs = 600, echoFb = 50, echoMix = 35, preset = "") } }
                        Pill("Space", false) { vm.fxEdit { it.copy(echoOn = true, echoMs = 850, echoFb = 62, echoMix = 40, preset = "") } }
                    }
                    FxSlider("Delay", "${fx.echoMs} ms", fx.echoMs.toFloat(), 40f..1000f, { v -> vm.fxEdit(false) { it.copy(echoMs = v.toInt(), echoOn = true, preset = "") } }) { vm.fxSave() }
                    FxSlider("Feedback", "${fx.echoFb}%", fx.echoFb.toFloat(), 0f..85f, { v -> vm.fxEdit(false) { it.copy(echoFb = v.toInt(), echoOn = true, preset = "") } }) { vm.fxSave() }
                    FxSlider("Mix", "${fx.echoMix}%", fx.echoMix.toFloat(), 0f..100f, { v -> vm.fxEdit(false) { it.copy(echoMix = v.toInt(), echoOn = true, preset = "") } }) { vm.fxSave() }
                }

                Pill("Reset everything", false) { vm.resetFx() }
            }
        }
    }
    if (saving) NameDialog("Save scene", { saving = false }, confirm = "Save") { vm.saveScene(it); saving = false }
}

// ---------------------------------------------------------------------------------------------- pieces
@Composable
private fun Label(text: String) {
    Text(text, color = LocalSudo.current.text.copy(alpha = 0.7f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Pill(text: String, selected: Boolean, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    val t = LocalSudo.current
    val shape = RoundedCornerShape(50)
    Text(
        text, color = if (selected) t.onAccent else t.text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp, maxLines = 1,
        modifier = Modifier
            .then(if (selected) Modifier.background(Brush.horizontalGradient(listOf(t.primary, t.secondary)), shape) else Modifier.sudoPanel(shape))
            .combinedClickable(onLongClick = onLong, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun FxCard(title: String, icon: ImageVector, on: Boolean?, onToggle: (Boolean) -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val t = LocalSudo.current
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().sudoPanel(shape).then(if (on == true) Modifier.border(1.dp, t.primary.copy(alpha = 0.5f), shape) else Modifier).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (on == false) t.text.copy(alpha = 0.6f) else t.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, Modifier.weight(1f), color = t.text, fontWeight = FontWeight.Bold)
            if (on != null) Switch(on, onToggle, colors = SwitchDefaults.colors(checkedTrackColor = t.primary, checkedThumbColor = t.onAccent))
        }
        content()
    }
}

@Composable
private fun SubToggle(label: String, on: Boolean, onToggle: (Boolean) -> Unit) {
    val t = LocalSudo.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = t.text, fontWeight = FontWeight.SemiBold)
        Switch(on, onToggle, colors = SwitchDefaults.colors(checkedTrackColor = t.primary, checkedThumbColor = t.onAccent))
    }
}

@Composable
private fun FxSlider(label: String, shown: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, onDone: () -> Unit) {
    val t = LocalSudo.current
    Column {
        Row {
            Text(label, Modifier.weight(1f), color = t.text, style = MaterialTheme.typography.labelLarge)
            Text(shown, color = t.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Slider(value.coerceIn(range), onChange, valueRange = range, onValueChangeFinished = onDone, colors = sliderColors())
    }
}

/** Live spectrum of what is playing, with the EQ curve drawn on top; drag the curve to shape the sound. */
@Composable
private fun Spectrum(vm: PlayerViewModel) {
    val t = LocalSudo.current
    val fx = vm.fx
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { withFrameNanos { frame = it } } }
    val shape = RoundedCornerShape(24.dp)
    val bands = vm.eqBands
    val minMb = vm.eqMin
    val maxMb = vm.eqMax

    Column(Modifier.fillMaxWidth().sudoPanel(shape).padding(12.dp)) {
        Canvas(
            Modifier.fillMaxWidth().height(190.dp).pointerInput(bands, minMb, maxMb) {
                if (bands <= 0) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    fun apply(p: Offset) {
                        val pad = 14.dp.toPx()
                        val w = size.width - 2 * pad
                        val i = (((p.x - pad) / w) * (bands - 1)).let { Math.round(it) }.coerceIn(0, bands - 1)
                        val f = 1f - (p.y / size.height).coerceIn(0f, 1f)
                        vm.setEqLevel(i, (minMb + f * (maxMb - minMb)).toInt())
                        if (!vm.fx.enabled) vm.fxEdit(false) { it.copy(enabled = true) }
                    }
                    apply(down.position)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        ch.consume()
                        apply(ch.position)
                        if (!ch.pressed) break
                    }
                    vm.fxSave()
                }
            },
        ) {
            @Suppress("UNUSED_EXPRESSION") frame
            val lv = FxBus.levels
            val n = FxBus.BANDS
            val gap = 3.dp.toPx(); val bw = (size.width - gap * (n - 1)) / n
            for (i in 0 until n) {
                val h = max(3.dp.toPx(), lv[i] * size.height * 0.95f)
                drawRoundRect(
                    Brush.verticalGradient(listOf(t.primary.copy(alpha = if (fx.enabled) 0.95f else 0.45f), t.secondary.copy(alpha = if (fx.enabled) 0.45f else 0.15f)), size.height - h, size.height),
                    Offset(i * (bw + gap), size.height - h), Size(bw, h), CornerRadius(bw / 2),
                )
            }
            // zero line
            val zeroY = size.height * (maxMb.toFloat() / max(1, maxMb - minMb))
            drawLine(t.text.copy(alpha = 0.2f), Offset(0f, zeroY), Offset(size.width, zeroY), 1.dp.toPx())
            if (bands > 0 && fx.levels.size == bands) {
                val pad = 14.dp.toPx(); val w = size.width - 2 * pad
                val pts = List(bands) { i -> Offset(pad + w * i / max(1, bands - 1), size.height * (1f - (fx.levels[i] - minMb).toFloat() / (maxMb - minMb))) }
                val path = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    for (i in 1 until pts.size) {
                        val a = pts[i - 1]; val b = pts[i]; val mx = (a.x + b.x) / 2
                        cubicTo(mx, a.y, mx, b.y, b.x, b.y)
                    }
                }
                drawPath(path, t.text.copy(alpha = 0.95f), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                pts.forEach { drawCircle(t.text, 7.dp.toPx(), it); drawCircle(t.primary, 4.5.dp.toPx(), it) }
            }
        }
        if (vm.eqFreqs.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            vm.eqFreqs.forEachIndexed { i, hz ->
                Text(
                    if (hz >= 1000) "${hz / 1000}k" else "$hz", Modifier.weight(1f), color = t.text.copy(alpha = 0.55f), fontSize = 11.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}
