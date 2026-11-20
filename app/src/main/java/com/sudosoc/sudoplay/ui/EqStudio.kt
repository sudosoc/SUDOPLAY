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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.nativeCanvas
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
import androidx.compose.ui.graphics.Color
import com.sudosoc.sudoplay.data.Biquad
import com.sudosoc.sudoplay.data.ParamPresets
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

@Composable
private fun sliderColors(): SliderColors {
    val t = LocalSudo.current
    return SliderDefaults.colors(thumbColor = t.primary, activeTrackColor = t.primary, inactiveTrackColor = t.text.copy(alpha = 0.2f))
}

private val STUDIO_TABS = listOf("EQ", "Sound", "Space", "Dynamics", "Output")

/** Full-screen sound studio. The graph on top is always the live view; the tabs below hold every control, grouped by what they do. */
@Composable
fun EqStudio(vm: PlayerViewModel, onClose: () -> Unit) {
    val t = LocalSudo.current
    val fx = vm.fx
    var saving by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sel by rememberSaveable { mutableIntStateOf(5) }
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

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AnalyzerPanel(vm, sel) { sel = it }

                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    STUDIO_TABS.forEachIndexed { i, n -> Pill(n, tab == i) { tab = i } }
                }

                when (tab) {
                    0 -> EqTab(vm, sel) { sel = it }
                    1 -> SoundTab(vm) { saving = true }
                    2 -> SpaceTab(vm)
                    3 -> DynamicsTab(vm)
                    else -> OutputTab(vm)
                }
            }
        }
    }
    if (saving) NameDialog("Save scene", { saving = false }, confirm = "Save") { vm.saveScene(it); saving = false }
}

// ---------------------------------------------------------------------------------------------- tabs
@Composable
private fun EqTab(vm: PlayerViewModel, sel: Int, onSel: (Int) -> Unit) {
    val fx = vm.fx
    var showDevice by remember { mutableStateOf(false) }
    FxCard("Parametric EQ", I.Tune, fx.pOn, { on -> vm.fxEdit { it.copy(pOn = on, preset = "") } },
        "10 bands you can place anywhere. Drag a dot on the graph: sideways = frequency, up / down = boost or cut. Double-tap a dot to flatten it.") {
        Label("Presets")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ParamPresets.all.forEach { p -> Pill(p.name, false) { vm.fxEdit { st -> p.apply(st) } } }
        }
        Hint("Tap a preset to load it, then fine-tune the bands below.")
        BandEditor(vm, sel, onSel)
        val boost = Biquad.peakBoostDb(fx)
        SubToggle("Auto headroom", fx.pAuto, "Lowers the volume by the biggest boost (now ${"%.1f".format(boost)} dB) so boosting never distorts. Turn off for max loudness.") { on -> vm.fxEdit { it.copy(pAuto = on) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Flatten all bands", false) { vm.fxEdit { it.copy(pg = FxState.DEFAULT_PG, pt = FxState.DEFAULT_PT, pf = FxState.DEFAULT_PF, pq = FxState.DEFAULT_PQ, pe = FxState.DEFAULT_PE, preset = "") } }
        }
    }
    FxCard("Device graphic EQ", I.Tune, null, {}, "The phone's own built-in equalizer, a few fixed bands. It runs together with the parametric EQ — leave it flat if you use the parametric one.") {
        Pill(if (showDevice) "Hide" else "Show device EQ", showDevice) { showDevice = !showDevice }
        if (showDevice) {
            Spectrum(vm)
            Label("Curves")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EqPresets.curves.keys.forEach { n -> Pill(n, fx.preset == n) { vm.applyCurve(n) } }
            }
        }
    }
}

@Composable
private fun SoundTab(vm: PlayerViewModel, onSave: () -> Unit) {
    val fx = vm.fx
    Label("Scenes")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FxScenes.builtIn.forEach { sc -> Pill(sc.name, fx.preset == sc.name) { vm.applyScene(sc) } }
        vm.userScenes.forEach { (n, st) ->
            var menu by remember { mutableStateOf(false) }
            Box {
                Pill("★ $n", fx.preset == n, onLong = { menu = true }) { vm.applyUserScene(n, st) }
                if (menu) SudoSheet({ menu = false }, title = n) { close -> SheetAction(I.Delete, "Delete scene", danger = true) { vm.deleteScene(n); close() } }
            }
        }
        Pill("+ Save current", false, onClick = onSave)
    }
    Hint("A scene is a whole sound setup (EQ + effects). Long-press one of yours to delete it.")
    FxCard("Level", I.Tune, null, {}) {
        FxSlider("Preamp (headroom)", "${fx.preamp / 10f} dB", fx.preamp.toFloat(), -120f..0f, "Turns everything down before the effects. Lower it if loud songs sound crunchy.", { v -> vm.fxEdit(false) { it.copy(preamp = v.toInt(), preset = "") } }) { vm.fxSave() }
        FxSlider("Bass boost", "${fx.bass / 10}%", fx.bass.toFloat(), 0f..1000f, "Hardware bass enhancer. Adds weight to the low end.", { v -> vm.fxEdit(false) { it.copy(bass = v.toInt(), preset = "") } }) { vm.fxSave() }
        FxSlider("Loudness", "${fx.loud / 10}%", fx.loud.toFloat(), 0f..1000f, "Makes quiet music sound fuller without touching the volume key.", { v -> vm.fxEdit(false) { it.copy(loud = v.toInt(), preset = "") } }) { vm.fxSave() }
    }
    FxCard("Pitch", I.Tune, null, {}) {
        val semi = vm.settings.pitchSemi.value
        FxSlider("Key shift", (if (semi > 0) "+" else "") + "$semi semitones", semi.toFloat(), -6f..6f, "Raises or lowers the key without changing the speed.", { v -> vm.changePitch(v.toInt()) }) {}
        Pill("Reset pitch", semi == 0) { vm.changePitch(0) }
    }
}

@Composable
private fun SpaceTab(vm: PlayerViewModel) {
    val fx = vm.fx
    FxCard("Surround & width", I.Explore, fx.virtOn, { on -> vm.fxEdit { it.copy(virtOn = on, preset = "") } }, "Widens the sound around you. Best on headphones.") {
        FxSlider("Virtual surround", "${fx.virt / 10}%", fx.virt.toFloat(), 0f..1000f, "How strong the surround effect is.", { v -> vm.fxEdit(false) { it.copy(virt = v.toInt(), virtOn = true, preset = "") } }) { vm.fxSave() }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Auto", "Headphones", "Speakers").forEachIndexed { i, n -> Pill(n, fx.virtMode == i) { vm.fxEdit { it.copy(virtMode = i, virtOn = true, preset = "") } } }
        }
        Hint("Auto picks the right mode for what is connected.")
        SubToggle("Stereo width", fx.widthOn, "Make the left and right channels wider or narrower.") { on -> vm.fxEdit { it.copy(widthOn = on, preset = "") } }
        FxSlider("Width", "${fx.width}%", fx.width.toFloat(), 0f..200f, "0% = mono, 100% = original, 200% = extra wide.", { v -> vm.fxEdit(false) { it.copy(width = v.toInt(), widthOn = true, preset = "") } }) { vm.fxSave() }
        Pill("Mono", fx.widthOn && fx.width == 0) {
            if (fx.widthOn && fx.width == 0) vm.fxEdit { it.copy(widthOn = false, width = 100, preset = "") }
            else vm.fxEdit { it.copy(widthOn = true, width = 0, preset = "") }
        }
    }
    FxCard("Reverb", I.Album, fx.reverbOn, { on -> vm.fxEdit { it.copy(reverbOn = on, preset = "") } }, "Adds the sense of a room around the music.") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Room", false) { vm.fxEdit { it.copy(reverbOn = true, room = 35, damp = 55, reverbMix = 20, preset = "") } }
            Pill("Studio", false) { vm.fxEdit { it.copy(reverbOn = true, room = 50, damp = 40, reverbMix = 24, preset = "") } }
            Pill("Hall", false) { vm.fxEdit { it.copy(reverbOn = true, room = 75, damp = 40, reverbMix = 32, preset = "") } }
            Pill("Cathedral", false) { vm.fxEdit { it.copy(reverbOn = true, room = 95, damp = 25, reverbMix = 50, preset = "") } }
        }
        FxSlider("Room size", "${fx.room}%", fx.room.toFloat(), 0f..100f, "Bigger = longer tail.", { v -> vm.fxEdit(false) { it.copy(room = v.toInt(), reverbOn = true, preset = "") } }) { vm.fxSave() }
        FxSlider("Damping", "${fx.damp}%", fx.damp.toFloat(), 0f..100f, "Higher = softer, darker echoes (like a carpeted room).", { v -> vm.fxEdit(false) { it.copy(damp = v.toInt(), reverbOn = true, preset = "") } }) { vm.fxSave() }
        FxSlider("Mix", "${fx.reverbMix}%", fx.reverbMix.toFloat(), 0f..100f, "How much of the room you hear against the dry sound.", { v -> vm.fxEdit(false) { it.copy(reverbMix = v.toInt(), reverbOn = true, preset = "") } }) { vm.fxSave() }
    }
    FxCard("Echo", I.Repeat, fx.echoOn, { on -> vm.fxEdit { it.copy(echoOn = on, preset = "") } }, "Repeats the sound after a short delay.") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Slapback", false) { vm.fxEdit { it.copy(echoOn = true, echoMs = 110, echoFb = 15, echoMix = 28, preset = "") } }
            Pill("Delay", false) { vm.fxEdit { it.copy(echoOn = true, echoMs = 320, echoFb = 35, echoMix = 30, preset = "") } }
            Pill("Long", false) { vm.fxEdit { it.copy(echoOn = true, echoMs = 600, echoFb = 50, echoMix = 35, preset = "") } }
            Pill("Space", false) { vm.fxEdit { it.copy(echoOn = true, echoMs = 850, echoFb = 62, echoMix = 40, preset = "") } }
        }
        FxSlider("Delay", "${fx.echoMs} ms", fx.echoMs.toFloat(), 40f..1000f, "Time between the sound and its repeat.", { v -> vm.fxEdit(false) { it.copy(echoMs = v.toInt(), echoOn = true, preset = "") } }) { vm.fxSave() }
        FxSlider("Feedback", "${fx.echoFb}%", fx.echoFb.toFloat(), 0f..85f, "How many times it repeats before fading.", { v -> vm.fxEdit(false) { it.copy(echoFb = v.toInt(), echoOn = true, preset = "") } }) { vm.fxSave() }
        FxSlider("Mix", "${fx.echoMix}%", fx.echoMix.toFloat(), 0f..100f, "Volume of the repeats.", { v -> vm.fxEdit(false) { it.copy(echoMix = v.toInt(), echoOn = true, preset = "") } }) { vm.fxSave() }
    }
}

@Composable
private fun DynamicsTab(vm: PlayerViewModel) {
    val fx = vm.fx
    FxCard("Night mode", I.Tune, fx.compOn, { on -> vm.fxEdit { it.copy(compOn = on, preset = "") } }, "A compressor: quiets explosions and lifts whispers, so you can listen low without missing words.") {
        FxSlider("Squash loud parts", "${fx.compAmt}%", fx.compAmt.toFloat(), 0f..100f, "Higher = flatter volume, less dynamic.", { v -> vm.fxEdit(false) { it.copy(compAmt = v.toInt(), compOn = true, preset = "") } }) { vm.fxSave() }
    }
    FxCard("Songs & loudness", I.Tune, null, {}) {
        val xf = vm.settings.crossfade.value
        FxSlider("Fade between songs", if (xf == 0) "Off" else "$xf s", xf.toFloat(), 0f..12f, "Overlaps the end of one song with the start of the next.", { v -> vm.settings.crossfade.set(v.toInt()) }) { vm.applyMisc() }
        SubToggle("Even out loudness (ReplayGain)", vm.settings.replayGain.value, "Brings every song to a similar volume, so you stop reaching for the volume key.") { on -> vm.settings.replayGain.set(on); vm.applyMisc() }
    }
    FxCard("Output protection", I.Tune, null, {}) {
        SubToggle("Soft limiter", fx.limiter, "Rounds off peaks that would otherwise crackle. Leave on unless you want the raw signal.") { on -> vm.fxEdit { it.copy(limiter = on) } }
    }
}

@Composable
private fun OutputTab(vm: PlayerViewModel) {
    val fx = vm.fx
    FxCard("Balance & channels", I.Tune, null, {}) {
        FxSlider("Balance", when { fx.balance < 0 -> "L ${-fx.balance}%"; fx.balance > 0 -> "R ${fx.balance}%"; else -> "Center" }, fx.balance.toFloat(), -100f..100f, "Shift the sound toward one ear. Useful if one side is weaker.", { v -> vm.fxEdit(false) { it.copy(balance = v.toInt(), preset = "") } }) { vm.fxSave() }
        SubToggle("Swap left / right", fx.swapCh, "Flips the two channels.") { on -> vm.fxEdit { it.copy(swapCh = on, preset = "") } }
    }
    Pill("Reset everything", false) { vm.resetFx() }
    Hint("Turns off every effect and flattens the EQ. Your saved scenes are kept.")
}

// ---------------------------------------------------------------------------------------------- parametric graph
private val BAND_COLORS = List(FxState.BANDS) { Color.hsv((it * 34f) % 360f, 0.55f, 1f) }
private fun fPos(f: Double) = (ln(f / 20.0) / ln(1000.0)).coerceIn(0.0, 1.0)
private fun fAt(x: Double) = 20.0 * 1000.0.pow(x.coerceIn(0.0, 1.0))
private fun hzText(hz: Int) = if (hz >= 1000) "%.2f kHz".format(hz / 1000f).replace(".00", "") else "$hz Hz"

@Composable
private fun ParamGraph(vm: PlayerViewModel, sel: Int, range: Int, onSel: (Int) -> Unit) {
    val t = LocalSudo.current
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { withFrameNanos { frame = it } } }
    val shape = RoundedCornerShape(24.dp)
    var lastTapAt by remember { mutableLongStateOf(0L) }
    var lastTapBand by remember { mutableIntStateOf(-1) }

    Canvas(
        Modifier.fillMaxWidth().height(240.dp).sudoPanel(shape).pointerInput(range) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val pad = 16.dp.toPx(); val w = size.width - 2 * pad; val h = size.height.toFloat()
                val st0 = vm.fx
                fun yOf(i: Int, s: FxState) = if (Biquad.usesGain(s.pt[i])) h / 2 - (s.pg[i] / 10f) / range * (h / 2) else h / 2
                var best = -1; var bd = 52.dp.toPx()
                for (i in 0 until FxState.BANDS) {
                    val d = Offset(pad + (w * fPos(st0.pf[i].toDouble())).toFloat(), yOf(i, st0)).minus(down.position).getDistance()
                    if (d < bd) { bd = d; best = i }
                }
                if (best < 0) return@awaitEachGesture
                down.consume(); onSel(best)
                if (!vm.fx.enabled) vm.fxEdit(false) { it.copy(enabled = true) }
                var moved = false
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) break
                    if ((ch.position - down.position).getDistance() > 8.dp.toPx()) moved = true
                    if (moved) {
                        ch.consume()
                        val hz = fAt(((ch.position.x - pad) / w).toDouble()).toInt()
                        val g = (((h / 2 - ch.position.y) / (h / 2)) * range * 10f).toInt().coerceIn(-150, 150)
                        vm.fxEdit(false) { s -> s.band(best, f = hz, g = if (Biquad.usesGain(s.pt[best])) g else null, e = true) }
                    }
                }
                if (!moved) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (lastTapBand == best && now - lastTapAt < 350) vm.fxEdit(false) { it.band(best, g = 0) }
                    lastTapBand = best; lastTapAt = now
                }
                vm.fxSave()
            }
        },
    ) {
        @Suppress("UNUSED_EXPRESSION") frame
        val st = vm.fx
        val pad = 16.dp.toPx(); val w = size.width - 2 * pad; val h = size.height
        val mid = h / 2
        fun xOf(f: Double) = pad + (w * fPos(f)).toFloat()
        // grid
        for (f in listOf(50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0, 10000.0)) drawLine(t.text.copy(alpha = 0.08f), Offset(xOf(f), 0f), Offset(xOf(f), h), 1.dp.toPx())
        for (d in -range..range step (if (range <= 6) 3 else 6)) {
            val y = mid - d.toFloat() / range * mid
            drawLine(t.text.copy(alpha = if (d == 0) 0.28f else 0.08f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
        // live spectrum behind the curve
        val fresh = android.os.SystemClock.elapsedRealtime() - FxBus.lastUpdate < 350
        val n = FxBus.BANDS
        for (b in 0 until n) {
            val f0 = 40.0 * (400.0).pow(b / n.toDouble()); val f1 = 40.0 * (400.0).pow((b + 1) / n.toDouble())
            val x0 = xOf(f0); val x1 = xOf(f1)
            val v = if (fresh) FxBus.levels[b] else 0f
            val bh = max(2.dp.toPx(), v * h * 0.8f)
            drawRoundRect(t.primary.copy(alpha = if (st.enabled) 0.22f else 0.1f), Offset(x0 + 1f, h - bh), Size(max(1f, x1 - x0 - 2f), bh), CornerRadius(3.dp.toPx()))
        }
        // combined response
        val path = Path(); val fill = Path()
        var x = 0f
        var first = true
        while (x <= w) {
            val db = Biquad.totalDb(st, fAt((x / w).toDouble())).toFloat().coerceIn(-range.toFloat(), range.toFloat())
            val y = mid - db / range * mid
            if (first) { path.moveTo(pad + x, y); fill.moveTo(pad + x, mid); fill.lineTo(pad + x, y); first = false } else { path.lineTo(pad + x, y); fill.lineTo(pad + x, y) }
            x += 3f
        }
        fill.lineTo(pad + w, mid); fill.close()
        val on = st.enabled && st.pOn
        drawPath(fill, Brush.verticalGradient(listOf(t.primary.copy(alpha = if (on) 0.30f else 0.10f), Color.Transparent), 0f, h))
        drawPath(path, t.text.copy(alpha = if (on) 0.95f else 0.4f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        // nodes
        for (i in 0 until FxState.BANDS) {
            val act = Biquad.active(st.pt[i], st.pe[i], st.pg[i])
            val cx = xOf(st.pf[i].toDouble())
            val cy = if (Biquad.usesGain(st.pt[i])) (mid - (st.pg[i] / 10f) / range * mid).coerceIn(10f, h - 10f) else mid
            val col = BAND_COLORS[i]
            if (i == sel) { drawCircle(col.copy(alpha = 0.25f), 20.dp.toPx(), Offset(cx, cy)); drawCircle(col, 13.dp.toPx(), Offset(cx, cy), style = Stroke(2.dp.toPx())) }
            drawCircle(if (act || i == sel) col else col.copy(alpha = 0.35f), 7.dp.toPx(), Offset(cx, cy))
            drawCircle(Color.Black.copy(alpha = 0.55f), 2.5.dp.toPx(), Offset(cx, cy))
        }
        // labels
        val paint = android.graphics.Paint().apply { isAntiAlias = true; textSize = 10.sp.toPx(); color = android.graphics.Color.argb(150, (t.text.red * 255).toInt(), (t.text.green * 255).toInt(), (t.text.blue * 255).toInt()) }
        for ((f, lbl) in listOf(50.0 to "50", 100.0 to "100", 200.0 to "200", 500.0 to "500", 1000.0 to "1k", 2000.0 to "2k", 5000.0 to "5k", 10000.0 to "10k"))
            drawContext.canvas.nativeCanvas.drawText(lbl, xOf(f) + 3.dp.toPx(), h - 4.dp.toPx(), paint)
        for (d in listOf(range, 0, -range)) drawContext.canvas.nativeCanvas.drawText((if (d > 0) "+" else "") + "$d", 4.dp.toPx(), mid - d.toFloat() / range * mid + (if (d == range) 12.dp.toPx() else if (d == -range) -4.dp.toPx() else -4.dp.toPx()), paint)
    }
}

@Composable
private fun BandEditor(vm: PlayerViewModel, i: Int, onSel: (Int) -> Unit) {
    val t = LocalSudo.current
    val fx = vm.fx
    Label("Band ${i + 1} of ${FxState.BANDS}")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (b in 0 until FxState.BANDS) {
            val act = Biquad.active(fx.pt[b], fx.pe[b], fx.pg[b])
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (b == i) BAND_COLORS[b] else BAND_COLORS[b].copy(alpha = if (act) 0.35f else 0.12f))
                    .clickable { onSel(b) },
                contentAlignment = Alignment.Center,
            ) { Text("${b + 1}", color = if (b == i) Color.Black.copy(alpha = 0.85f) else t.text, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Biquad.typeNames.forEachIndexed { k, n -> Pill(n, fx.pt[i] == k) { vm.fxEdit { it.band(i, t = k, e = true) } } }
    }
    Hint(when (fx.pt[i]) {
        Biquad.PEAK -> "Bell: lifts or cuts a region around the frequency."
        Biquad.LOW_SHELF -> "Low shelf: lifts or cuts everything below the frequency."
        Biquad.HIGH_SHELF -> "High shelf: lifts or cuts everything above the frequency."
        Biquad.HIGH_PASS -> "High-pass: removes everything below the frequency (rumble, wind)."
        Biquad.LOW_PASS -> "Low-pass: removes everything above the frequency (hiss, harshness)."
        else -> "Notch: a deep narrow cut — kills one annoying tone or hum."
    })
    SubToggle("Band on", fx.pe[i], "Switch this band off without losing its settings.") { on -> vm.fxEdit { it.band(i, e = on) } }
    val hz = fx.pf[i]
    FxSlider("Frequency", hzText(hz), Math.log10(hz.toDouble()).toFloat(), 1.301f..4.301f, "Where the band acts. Fine steps below move it by a twelfth of an octave.",
        { v -> vm.fxEdit(false) { it.band(i, f = Math.pow(10.0, v.toDouble()).toInt()) } }) { vm.fxSave() }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("◄ lower", false) { vm.fxEdit { it.band(i, f = (hz / 1.05946).toInt()) } }
        Pill("higher ►", false) { vm.fxEdit { it.band(i, f = (hz * 1.05946).toInt() + 1) } }
    }
    if (Biquad.usesGain(fx.pt[i])) {
        FxSlider("Gain", "%+.1f dB".format(fx.pg[i] / 10f), fx.pg[i].toFloat(), -150f..150f, "Boost (+) or cut (−) at this band.",
            { v -> vm.fxEdit(false) { it.band(i, g = v.toInt()) } }) { vm.fxSave() }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("−0.1 dB", false) { vm.fxEdit { it.band(i, g = fx.pg[i] - 1) } }
            Pill("+0.1 dB", false) { vm.fxEdit { it.band(i, g = fx.pg[i] + 1) } }
            Pill("0", false) { vm.fxEdit { it.band(i, g = 0) } }
        }
    }
    val q = fx.pq[i] / 10.0
    FxSlider("Width (Q)", "%.2f".format(q), Math.log10(q).toFloat(), -0.523f..1.301f, "Low Q = wide and gentle, high Q = narrow and surgical.",
        { v -> vm.fxEdit(false) { it.band(i, q = (Math.pow(10.0, v.toDouble()) * 10).toInt()) } }) { vm.fxSave() }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("wider", false) { vm.fxEdit { it.band(i, q = fx.pq[i] - 1) } }
        Pill("narrower", false) { vm.fxEdit { it.band(i, q = fx.pq[i] + 1) } }
    }
}

// ---------------------------------------------------------------------------------------------- analyzer views
@Composable
private fun AnalyzerPanel(vm: PlayerViewModel, sel: Int, onSel: (Int) -> Unit) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var range by rememberSaveable { mutableIntStateOf(12) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("EQ + spectrum", "Spectrogram", "Scope").forEachIndexed { i, n -> Pill(n, mode == i) { mode = i } }
            if (mode == 0) listOf(6, 12, 18).forEach { r -> Pill("±$r dB", range == r) { range = r } }
        }
        when (mode) {
            0 -> ParamGraph(vm, sel, range, onSel)
            1 -> Spectrogram()
            else -> Scope()
        }
    }
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
private fun FxCard(title: String, icon: ImageVector, on: Boolean?, onToggle: (Boolean) -> Unit, hint: String? = null, content: @Composable ColumnScope.() -> Unit) {
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
        if (hint != null) Hint(hint)
        content()
    }
}

@Composable
private fun SubToggle(label: String, on: Boolean, hint: String? = null, onToggle: (Boolean) -> Unit) {
    val t = LocalSudo.current
    Row(Modifier.fillMaxWidth().clickable { onToggle(!on) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = t.text, fontWeight = FontWeight.SemiBold)
            if (hint != null) Hint(hint)
        }
        Switch(on, onToggle, colors = SwitchDefaults.colors(checkedTrackColor = t.primary, checkedThumbColor = t.onAccent))
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = LocalSudo.current.text.copy(alpha = 0.55f), fontSize = 11.5.sp, lineHeight = 15.sp)
}

@Composable
private fun FxSlider(label: String, shown: String, value: Float, range: ClosedFloatingPointRange<Float>, hint: String? = null, onChange: (Float) -> Unit, onDone: () -> Unit) {
    val t = LocalSudo.current
    Column {
        Row {
            Text(label, Modifier.weight(1f), color = t.text, style = MaterialTheme.typography.labelLarge)
            Text(shown, color = t.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Slider(value.coerceIn(range), onChange, valueRange = range, onValueChangeFinished = onDone, colors = sliderColors())
        if (hint != null) Hint(hint)
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
