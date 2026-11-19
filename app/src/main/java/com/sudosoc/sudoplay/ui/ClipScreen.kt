package com.sudosoc.sudoplay.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sudosoc.sudoplay.PlayerViewModel
import com.sudosoc.sudoplay.clip.ClipPainter
import com.sudosoc.sudoplay.clip.ClipRenderer
import com.sudosoc.sudoplay.clip.ClipSpec
import com.sudosoc.sudoplay.data.Lyrics
import com.sudosoc.sudoplay.data.LyricsFetcher
import com.sudosoc.sudoplay.data.LyricsLoader
import com.sudosoc.sudoplay.data.Song
import com.sudosoc.sudoplay.playback.FxBus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sin

/** Make a vertical video (or a still lyric card) from part of a song: cover, title, synced lyrics and a live spectrum. */
@Composable
fun ClipScreen(vm: PlayerViewModel, song: Song, onClose: () -> Unit) {
    val t = LocalSudo.current
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val s = vm.settings
    BackHandler(onBack = onClose)

    val art by rememberArt(song, 640)
    val lyrics by produceState<Lyrics?>(null, song.id) {
        value = withContext(Dispatchers.IO) { runCatching { LyricsLoader.load(ctx, song) ?: LyricsFetcher.cached(ctx, song) }.getOrNull() }
    }
    val durS = (song.durationMs / 1000f).coerceAtLeast(6f)
    val maxLen = minOf(60f, durS)

    var start by remember { mutableFloatStateOf(if (vm.current?.id == song.id) (vm.position / 1000f).coerceAtMost((durS - 20f).coerceAtLeast(0f)) else 0f) }
    var len by remember { mutableFloatStateOf(minOf(30f, maxLen)) }
    var style by rememberSaveable { mutableIntStateOf(0) }
    var bg by rememberSaveable { mutableIntStateOf(0) }
    var showLyrics by rememberSaveable { mutableStateOf(true) }
    var showTitle by rememberSaveable { mutableStateOf(true) }
    var hd by rememberSaveable { mutableStateOf(false) }
    var credit by remember { mutableStateOf(s.clipCredit.value) }
    val startC = start.coerceIn(0f, (durS - len).coerceAtLeast(0f))

    var busy by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf("") }
    var frac by remember { mutableFloatStateOf(0f) }
    var result by remember { mutableStateOf<android.net.Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(busy) { view.keepScreenOn = busy; onDispose { view.keepScreenOn = s.keepScreenOn.value } }

    val ct = remember(art) { art?.let { coverTheme(t, it) } ?: t }
    val spec = ClipSpec(
        title = song.title, artist = song.artist, style = style, bg = bg, showLyrics = showLyrics && lyrics?.synced == true,
        showTitle = showTitle, credit = credit.trim(), accent = ct.primary.toArgb(), accent2 = ct.secondary.toArgb(), dark = ct.bgTop.toArgb(),
    )
    val painter = remember(spec.style, spec.bg, spec.showLyrics, spec.showTitle, spec.credit, spec.accent, art) { ClipPainter(spec, art) }

    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { withFrameNanos { tick = it } } }

    fun liveLevels(tSec: Float): FloatArray {
        val fresh = android.os.SystemClock.elapsedRealtime() - FxBus.lastUpdate < 350
        return if (fresh && vm.current?.id == song.id) FxBus.levels else FloatArray(32) { i -> (0.18f + 0.5f * abs(sin(tSec * 2.2f + i * 0.45f)) * (1f - i / 48f)).coerceIn(0f, 1f) }
    }

    fun lineAt(absMs: Long): Pair<String?, Float> {
        val l = lyrics ?: return null to 9f
        if (!l.synced) return null to 9f
        val i = l.lines.indexOfLast { it.timeMs <= absMs }
        return if (i >= 0) l.lines[i].text to (absMs - l.lines[i].timeMs) / 1000f else null to 9f
    }

    fun startRender() {
        s.clipCredit.set(credit)
        busy = true; error = null; result = null; frac = 0f; phase = "Starting…"
        val startMs = (startC * 1000).toLong(); val lenMs = (len * 1000).toLong()
        val a = art
        job = scope.launch(Dispatchers.Default) {
            try {
                result = ClipRenderer.render(ctx, song.uri, startMs, lenMs, spec, a, lyrics, hd, { p, f -> phase = p; frac = f }, { !isActive })
            } catch (_: ClipRenderer.Cancelled) {
            } catch (e: CancellationException) { throw e
            } catch (e: Throwable) {
                error = (e.message ?: e.javaClass.simpleName) + if (hd) " — try 720p" else ""
            } finally { busy = false }
        }
    }

    SudoBackground(song) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClose) { Icon(I.KeyboardArrowDown, null, tint = t.text, modifier = Modifier.size(30.dp)) }
                Column(Modifier.weight(1f)) {
                    Text("Video clip", color = t.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(song.title, color = t.text.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // ---- live preview (same painter as the render)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ComposeCanvas(Modifier.height(400.dp).aspectRatio(9f / 16f).clip(RoundedCornerShape(20.dp))) {
                        @Suppress("UNUSED_EXPRESSION") tick
                        val tSec = (tick / 1_000_000_000f) % len
                        val (line, age) = lineAt(((startC + tSec) * 1000).toLong())
                        drawIntoCanvas { cv -> painter.draw(cv.nativeCanvas, size.width.toInt(), size.height.toInt(), tSec / len, liveLevels(tSec), line, age) }
                    }
                }
                Text("Preview — the video is drawn with the real spectrum of the part you pick", color = t.text.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)

                // ---- which part
                ClipCard("Which part") {
                    ClipSlider("Starts at", fmt((startC * 1000).toLong()), startC, 0f..(durS - len).coerceAtLeast(0.1f)) { start = it }
                    ClipSlider("Length", "${len.toInt()} s", len, 5f..maxLen) { len = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ClipPill("Use current position", false) { if (vm.current?.id == song.id) start = vm.position / 1000f }
                        ClipPill("Play this part", false) {
                            if (vm.current?.id != song.id) vm.play(listOf(song), 0)
                            vm.seekTo((startC * 1000).toLong())
                        }
                    }
                }

                ClipCard("Look") {
                    Text("Spectrum", color = t.text.copy(alpha = 0.7f), style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Bars", "Mirror", "Ring", "Wave", "Dots").forEachIndexed { i, n -> ClipPill(n, style == i) { style = i } }
                    }
                    Text("Background", color = t.text.copy(alpha = 0.7f), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Blurred cover", "Gradient", "Black").forEachIndexed { i, n -> ClipPill(n, bg == i) { bg = i } }
                    }
                    ClipToggle("Title and artist", showTitle) { showTitle = it }
                    ClipToggle(if (lyrics?.synced == true) "Synced lyrics" else "Synced lyrics (none found for this song)", showLyrics && lyrics?.synced == true, enabled = lyrics?.synced == true) { showLyrics = it }
                    OutlinedTextField(
                        credit, { credit = it.take(40) }, singleLine = true, label = { Text("Credit line (e.g. @sakeenah)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = t.text, unfocusedTextColor = t.text, focusedBorderColor = t.primary, unfocusedBorderColor = t.text.copy(alpha = 0.3f),
                            focusedLabelColor = t.primary, unfocusedLabelColor = t.text.copy(alpha = 0.6f), cursorColor = t.primary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ClipPill("720p (fast)", !hd) { hd = false }
                        ClipPill("1080p (slower)", hd) { hd = true }
                    }
                }

                // ---- go
                if (busy) {
                    ClipCard("Making your video") {
                        Text(phase, color = t.text, fontWeight = FontWeight.SemiBold)
                        LinearProgressIndicator(progress = { frac }, Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = t.primary, trackColor = t.text.copy(alpha = 0.15f))
                        Text("Keep SUDOPLAY open until it finishes.", color = t.text.copy(alpha = 0.55f), fontSize = 12.sp)
                        ClipPill("Cancel", false) { job?.cancel() }
                    }
                } else {
                    Text(
                        "Make the video", color = t.onAccent, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(t.primary, t.secondary)))
                            .clickable { startRender() }.padding(vertical = 16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    ClipPill("Save this frame as an image", false) {
                        scope.launch(Dispatchers.Default) {
                            runCatching {
                                val bmp = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
                                val tSec = (tick / 1_000_000_000f) % len
                                val (line, age) = lineAt(((startC + tSec) * 1000).toLong())
                                ClipPainter(spec, art).draw(Canvas(bmp), 1080, 1920, tSec / len, liveLevels(tSec), line, age.coerceAtLeast(1f))
                                ClipRenderer.saveImage(ctx, bmp, "SUDOPLAY_${System.currentTimeMillis() / 1000}.png")
                                bmp.recycle()
                            }.onSuccess { withContext(Dispatchers.Main) { Toast.makeText(ctx, com.sudosoc.sudoplay.ui.Tr.t("Saved to Pictures/SUDOPLAY"), Toast.LENGTH_SHORT).show() } }
                                .onFailure { withContext(Dispatchers.Main) { Toast.makeText(ctx, com.sudosoc.sudoplay.ui.Tr.t("Could not save the image"), Toast.LENGTH_SHORT).show() } }
                        }
                    }
                }
                error?.let { Text("Could not finish: $it", color = t.text, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().sudoPanel(RoundedCornerShape(16.dp)).padding(14.dp)) }
                result?.let { uri ->
                    ClipCard("Done — saved to Movies/SUDOPLAY") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ClipPill("Share", true) {
                                val send = Intent(Intent.ACTION_SEND).apply { type = "video/mp4"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                                runCatching { ctx.startActivity(Intent.createChooser(send, "Share clip").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }
                            ClipPill("Open", false) {
                                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val t = LocalSudo.current
    Column(Modifier.fillMaxWidth().sudoPanel(RoundedCornerShape(22.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = t.text, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun ClipPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val t = LocalSudo.current
    val shape = RoundedCornerShape(50)
    Text(
        text, color = if (selected) t.onAccent else t.text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp, maxLines = 1,
        modifier = Modifier.clip(shape)
            .then(if (selected) Modifier.background(Brush.horizontalGradient(listOf(t.primary, t.secondary)), shape) else Modifier.sudoPanel(shape))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun ClipToggle(label: String, on: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val t = LocalSudo.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = t.text.copy(alpha = if (enabled) 1f else 0.5f), fontWeight = FontWeight.SemiBold)
        Switch(on, onChange, enabled = enabled, colors = SwitchDefaults.colors(checkedTrackColor = t.primary, checkedThumbColor = t.onAccent))
    }
}

@Composable
private fun ClipSlider(label: String, shown: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    val t = LocalSudo.current
    Column {
        Row {
            Text(label, Modifier.weight(1f), color = t.text, style = MaterialTheme.typography.labelLarge)
            Text(shown, color = t.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Slider(value.coerceIn(range), onChange, valueRange = range, colors = SliderDefaults.colors(thumbColor = t.primary, activeTrackColor = t.primary, inactiveTrackColor = t.text.copy(alpha = 0.2f)))
    }
}
