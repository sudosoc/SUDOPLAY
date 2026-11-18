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
                AnalyzerPanel(vm)

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

                FxCard("Transitions & loudness", I.Tune, null, {}) {
                    val xf = vm.settings.crossfade.value
                    FxSlider("Fade between songs", if (xf == 0) "Off" else "$xf s", xf.toFloat(), 0f..12f, { v -> vm.settings.crossfade.set(v.toInt()) }) { vm.applyMisc() }
                    SubToggle("Even out loudness (ReplayGain)", vm.settings.replayGain.value) { on -> vm.settings.replayGain.set(on); vm.applyMisc() }
                }

                FxCard("Night mode", I.Tune, fx.compOn, { on -> vm.fxEdit { it.copy(compOn = on, preset = "") } }) {
                    FxSlider("Squash loud parts", "${fx.compAmt}%", fx.compAmt.toFloat(), 0f..100f, { v -> vm.fxEdit(false) { it.copy(compAmt = v.toInt(), compOn = true, preset = "") } }) { vm.fxSave() }
                }

                FxCard("Parametric EQ", I.Tune, fx.pOn, { on -> vm.fxEdit { it.copy(pOn = on, preset = "") } }) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Reset", false) { vm.fxEdit { it.copy(pf = FxState.DEFAULT_PF, pg = FxState.DEFAULT_PG, pq = FxState.DEFAULT_PQ, preset = "") } }
                        Pill("Clear vocals", false) { vm.fxEdit { it.copy(pOn = true, pg = listOf(-20, -20, 30, 40, 0), pq = listOf(10, 10, 12, 12, 10), preset = "") } }
                        Pill("Warm", false) { vm.fxEdit { it.copy(pOn = true, pg = listOf(30, 20, 0, -20, -40), pq = listOf(8, 8, 10, 10, 8), preset = "") } }
                        Pill("Bright", false) { vm.fxEdit { it.copy(pOn = true, pg = listOf(-10, 0, 0, 30, 50), pq = listOf(10, 10, 10, 10, 8), preset = "") } }
                        Pill("Cut mud", false) { vm.fxEdit { it.copy(pOn = true, pg = listOf(0, -40, 0, 10, 0), pf = listOf(60, 300, 1000, 4000, 12000), pq = listOf(10, 14, 10, 10, 10), preset = "") } }
                        Pill("Quran / speech", false) { vm.fxEdit { it.copy(pOn = true, pg = listOf(-40, -10, 30, 30, -10), pf = listOf(80, 250, 1500, 3500, 10000), pq = listOf(10, 10, 10, 12, 10), preset = "") } }
                    }
                    for (i in 0 until 5) {
                        val hz = fx.pf[i]
                        FxSlider("Band ${i + 1} • " + if (hz >= 1000) "%.1f kHz".format(hz / 1000f) else "$hz Hz", "%+.1f dB".format(fx.pg[i] / 10f), fx.pg[i].toFloat(), -120f..120f,
                            { v -> vm.fxEdit(false) { it.copy(pOn = true, pg = it.pg.toMutableList().also { l -> l[i] = (v / 5).toInt() * 5 }, preset = "") } }) { vm.fxSave() }
                        FxSlider("   frequency", if (hz >= 1000) "%.2f kHz".format(hz / 1000f) else "$hz Hz", Math.log10(hz.toDouble()).toFloat(), 1.3f..4.3f,
                            { v -> vm.fxEdit(false) { it.copy(pOn = true, pf = it.pf.toMutableList().also { l -> l[i] = Math.pow(10.0, v.toDouble()).toInt() }, preset = "") } }) { vm.fxSave() }
                        FxSlider("   width (Q)", "%.1f".format(fx.pq[i] / 10f), fx.pq[i].toFloat(), 3f..80f,
                            { v -> vm.fxEdit(false) { it.copy(pOn = true, pq = it.pq.toMutableList().also { l -> l[i] = v.toInt() }, preset = "") } }) { vm.fxSave() }
                    }
                }

                FxCard("Balance & channels", I.Tune, null, {}) {
                    FxSlider("Balance", when { fx.balance < 0 -> "L ${-fx.balance}%"; fx.balance > 0 -> "R ${fx.balance}%"; else -> "Center" }, fx.balance.toFloat(), -100f..100f, { v -> vm.fxEdit(false) { it.copy(balance = v.toInt(), preset = "") } }) { vm.fxSave() }
                    SubToggle("Swap left / right", fx.swapCh) { on -> vm.fxEdit { it.copy(swapCh = on, preset = "") } }
                }

                FxCard("Pitch", I.Tune, null, {}) {
                    val semi = vm.settings.pitchSemi.value
                    FxSlider("Key shift", (if (semi > 0) "+" else "") + "$semi semitones", semi.toFloat(), -6f..6f, { v -> vm.changePitch(v.toInt()) }) {}
                    Pill("Reset pitch", semi == 0) { vm.changePitch(0) }
                }

                FxCard("Surround & space", I.Explore, fx.virtOn, { on -> vm.fxEdit { it.copy(virtOn = on, preset = "") } }) {
                    FxSlider("Virtual surround", "${fx.virt / 10}%", fx.virt.toFloat(), 0f..1000f, { v -> vm.fxEdit(false) { it.copy(virt = v.toInt(), virtOn = true, preset = "") } }) { vm.fxSave() }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Auto", "Headphones", "Speakers").forEachIndexed { i, n -> Pill(n, fx.virtMode == i) { vm.fxEdit { it.copy(virtMode = i, virtOn = true, preset = "") } } }
                    }
                    Pill("Mono", fx.widthOn && fx.width == 0) {
                        if (fx.widthOn && fx.width == 0) vm.fxEdit { it.copy(widthOn = false, width = 100, preset = "") }
                        else vm.fxEdit { it.copy(widthOn = true, width = 0, preset = "") }
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


// ---------------------------------------------------------------------------------------------- analyzer views
@Composable
private fun AnalyzerPanel(vm: PlayerViewModel) {
    var mode by remember { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Spectrum + EQ", "Spectrogram", "Scope").forEachIndexed { i, n -> Pill(n, mode == i) { mode = i } }
        }
        when (mode) {
            0 -> Spectrum(vm)
            1 -> Spectrogram()
            else -> Scope()
        }
    }
}

private const val SG_COLS = 96

/** Scrolling time-frequency heat map: new audio appears on the right and drifts left. Low frequencies at the bottom. */
@Composable
private fun Spectrogram() {
    val t = LocalSudo.current
    val hist = remember { Array(SG_COLS) { FloatArray(FxBus.BANDS) } }
    var head by remember { mutableIntStateOf(0) }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (now - last > 30_000_000L) {
                    last = now
                    val fresh = android.os.SystemClock.elapsedRealtime() - FxBus.lastUpdate < 350
                    val src = FxBus.levels
                    val col = hist[head]
                    for (i in col.indices) col[i] = if (fresh) src[i] else col[i] * 0.8f
                    head = (head + 1) % SG_COLS
                    frame = now
                }
            }
        }
    }
    val shape = RoundedCornerShape(24.dp)
    val cold = t.text.copy(alpha = 0.04f)
    Canvas(Modifier.fillMaxWidth().height(190.dp).sudoPanel(shape).padding(8.dp)) {
        @Suppress("UNUSED_EXPRESSION") frame
        val n = FxBus.BANDS
        val cw = size.width / SG_COLS
        val ch = size.height / n
        for (c in 0 until SG_COLS) {
            val col = hist[(head + c) % SG_COLS]
            for (b in 0 until n) {
                val v = col[b]
                if (v < 0.06f) continue
                val color = when {
                    v < 0.4f -> androidx.compose.ui.graphics.lerp(cold, t.secondary.copy(alpha = 0.8f), v / 0.4f)
                    v < 0.75f -> androidx.compose.ui.graphics.lerp(t.secondary.copy(alpha = 0.8f), t.primary, (v - 0.4f) / 0.35f)
                    else -> androidx.compose.ui.graphics.lerp(t.primary, androidx.compose.ui.graphics.Color.White, (v - 0.75f) / 0.25f)
                }
                drawRect(color, Offset(c * cw, size.height - (b + 1) * ch), Size(cw + 0.6f, ch + 0.6f))
            }
        }
    }
}

/** Oscilloscope of the audio leaving the sound engine. */
@Composable
private fun Scope() {
    val t = LocalSudo.current
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { withFrameNanos { frame = it } } }
    val shape = RoundedCornerShape(24.dp)
    Canvas(Modifier.fillMaxWidth().height(190.dp).sudoPanel(shape).padding(8.dp)) {
        @Suppress("UNUSED_EXPRESSION") frame
        val fresh = android.os.SystemClock.elapsedRealtime() - FxBus.lastUpdate < 350
        val s = FxBus.scope
        val mid = size.height / 2
        drawLine(t.text.copy(alpha = 0.15f), Offset(0f, mid), Offset(size.width, mid), 1.dp.toPx())
        val path = Path()
        for (i in s.indices) {
            val x = size.width * i / (s.size - 1)
            val y = mid - (if (fresh) s[i] else 0f).coerceIn(-1f, 1f) * mid * 0.95f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, t.primary.copy(alpha = 0.25f), style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
        drawPath(path, t.primary, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}
