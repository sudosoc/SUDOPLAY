package com.sudosoc.sudoplay.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.sudosoc.sudoplay.PlayerViewModel
import com.sudosoc.sudoplay.data.Song
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.ceil

// ============================================================================ entry
@Composable
fun NowPlayingScreen(vm: PlayerViewModel, onClose: () -> Unit) {
    val app = LocalSudo.current
    val pt = PlayerThemes.byId(vm.settings.playerTheme.value)
    val palette by rememberPalette(vm.current)
    val eff = remember(app, pt) {
        val custom = pt.bg == PBg.Gradient || pt.bg == PBg.Blobs
        app.copy(
            primary = pt.accent ?: app.primary, secondary = pt.accent2 ?: app.secondary,
            text = pt.text ?: app.text, dark = pt.dark ?: app.dark,
            bgTop = if (custom) pt.colors.firstOrNull() ?: app.bgTop else app.bgTop,
            bgBottom = if (custom) pt.colors.lastOrNull() ?: app.bgBottom else app.bgBottom,
        )
    }
    CompositionLocalProvider(LocalSudo provides eff) {
        PlayerContent(vm, pt, palette, onClose)
    }
}

@Composable
private fun PlayerContent(vm: PlayerViewModel, pt: PlayerTheme, palette: Pair<Color, Color>?, onClose: () -> Unit) {
    val t = LocalSudo.current
    val s = vm.settings
    val song = vm.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current

    var mode by remember { mutableIntStateOf(if (s.lyricsDefault.value) 1 else 0) } // 0 art, 1 lyrics, 2 queue
    var coverFor by remember { mutableStateOf<Song?>(null) }
    var showEq by remember { mutableStateOf(false) }
    var artRect by remember { mutableStateOf<Rect?>(null) }
    var scrubBase by remember { mutableLongStateOf(0L) }
    var scrubTarget by remember { mutableStateOf<Long?>(null) }
    var scrubDelta by remember { mutableLongStateOf(0L) }
    var flashIcon by remember { mutableStateOf<ImageVector?>(null) }
    var flashTick by remember { mutableIntStateOf(0) }

    DisposableEffect(s.keepScreenOn.value) {
        view.keepScreenOn = s.keepScreenOn.value
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(flashTick) { if (flashTick > 0) { delay(550); flashIcon = null } }

    fun buzz() { if (s.haptics.value) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    fun flash(i: ImageVector) { flashIcon = i; flashTick++ }

    val gestures = Modifier.playerGestures(
        doubleTap = s.doubleTap.value, swipe = s.swipe.value, invert = s.swipeInvert.value, scrub = s.scrub.value,
        artBounds = { artRect },
        onDoubleTap = {
            val willPlay = !vm.isPlaying
            vm.togglePlay(); buzz()
            flash(if (willPlay) Icons.Rounded.PlayArrow else Icons.Rounded.Pause)
        },
        onNext = { vm.next(); buzz(); flash(Icons.Rounded.SkipNext) },
        onPrev = { vm.previous(); buzz(); flash(Icons.Rounded.SkipPrevious) },
        onLongPressArt = { song?.let { coverFor = it } },
        onLongPressStart = { buzz() },
        onScrubStart = { scrubBase = vm.position; buzz() },
        onScrub = { fraction ->
            val delta = (fraction * s.scrubSeconds.value * 1000f).toLong()
            scrubDelta = delta
            scrubTarget = (scrubBase + delta).coerceIn(0L, vm.duration.coerceAtLeast(1L))
        },
        onScrubEnd = { scrubTarget?.let { vm.seekTo(it) }; scrubTarget = null },
    )

    PlayerBackground(pt, song, palette) {
        Box(Modifier.fillMaxSize().then(gestures)) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 22.dp, vertical = 8.dp)) {
                // ---- top bar
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, null, tint = t.text, modifier = Modifier.size(32.dp)) }
                    Text(
                        when (mode) { 1 -> "Lyrics"; 2 -> "Queue"; else -> "Now playing" }, color = t.text.copy(alpha = 0.8f),
                        modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    )
                    IconButton(onClick = { mode = if (mode == 1) 0 else 1 }) { Icon(Icons.Rounded.Lyrics, null, tint = if (mode == 1) t.primary else t.text) }
                    IconButton(onClick = { mode = if (mode == 2) 0 else 2 }) { Icon(Icons.Rounded.QueueMusic, null, tint = if (mode == 2) t.primary else t.text) }
                }

                // ---- art / lyrics / queue
                Box(
                    Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { artRect = if (mode == 0) it.boundsInRoot() else null },
                    contentAlignment = Alignment.Center,
                ) {
                    when (mode) {
                        2 -> QueueList(vm)
                        1 -> LyricsView(vm)
                        else -> if (pt.bg != PBg.FullBleed) BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            val side = minOf(maxWidth, maxHeight)
                            val progress = if (vm.duration > 0) (vm.position.toFloat() / vm.duration).coerceIn(0f, 1f) else 0f
                            PlayerArt(pt, song, side, vm.isPlaying, progress)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ---- title + favorite
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(song?.title ?: "—", color = t.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song?.artist ?: "", color = t.text.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (song != null) {
                        val fav = vm.store.isFav(song.id)
                        IconButton(onClick = { vm.store.toggleFav(song.id) }) {
                            Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = if (fav) t.primary else t.text)
                        }
                        IconButton(onClick = { vm.playlistTarget = song }) { Icon(Icons.Rounded.Add, null, tint = t.text) }
                    }
                }

                // ---- seek bar
                var drag by remember { mutableStateOf<Float?>(null) }
                val dur = vm.duration.coerceAtLeast(1L).toFloat()
                val shown = (scrubTarget?.toFloat() ?: drag ?: vm.position.toFloat()).coerceIn(0f, dur)
                Slider(
                    value = shown,
                    onValueChange = { drag = it },
                    onValueChangeFinished = { drag?.let { vm.seekTo(it.toLong()) }; drag = null },
                    valueRange = 0f..dur,
                    colors = SliderDefaults.colors(thumbColor = t.primary, activeTrackColor = t.primary, inactiveTrackColor = t.text.copy(alpha = 0.2f)),
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(fmt(shown.toLong()), color = t.text.copy(alpha = 0.65f), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    Text(fmt(vm.duration), color = t.text.copy(alpha = 0.65f), style = MaterialTheme.typography.labelMedium)
                }

                // ---- transport
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = vm::toggleShuffle) { Icon(Icons.Rounded.Shuffle, null, tint = if (vm.shuffle) t.primary else t.text.copy(alpha = 0.7f)) }
                    IconButton(onClick = vm::previous) { Icon(Icons.Rounded.SkipPrevious, null, tint = t.text, modifier = Modifier.size(38.dp)) }
                    PlayButton(pt, vm.isPlaying, vm::togglePlay)
                    IconButton(onClick = vm::next) { Icon(Icons.Rounded.SkipNext, null, tint = t.text, modifier = Modifier.size(38.dp)) }
                    IconButton(onClick = vm::cycleRepeat) {
                        Icon(
                            if (vm.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, null,
                            tint = if (vm.repeatMode == Player.REPEAT_MODE_OFF) t.text.copy(alpha = 0.7f) else t.primary,
                        )
                    }
                }

                // ---- extras
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    SpeedChip(vm)
                    EqChip(vm) { showEq = true }
                    SleepChip(vm)
                }
            }

            // ---- gesture feedback overlays
            AnimatedVisibility(flashIcon != null, Modifier.align(Alignment.Center), enter = fadeIn() + scaleIn(initialScale = 0.6f), exit = fadeOut() + scaleOut(targetScale = 1.2f)) {
                Box(Modifier.size(96.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                    flashIcon?.let { Icon(it, null, tint = Color.White, modifier = Modifier.size(56.dp)) }
                }
            }
            AnimatedVisibility(scrubTarget != null, Modifier.align(Alignment.Center), enter = fadeIn(), exit = fadeOut()) {
                Column(
                    Modifier.clip(RoundedCornerShape(24.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 28.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val sec = scrubDelta / 1000
                    Text((if (sec >= 0) "+" else "−") + "${abs(sec)}s", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text(fmt(scrubTarget ?: 0L) + " / " + fmt(vm.duration), color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }

    if (showEq) EqSheet(vm) { showEq = false }
    coverFor?.let { CoverFlow(it) { coverFor = null } }
}

// ============================================================================ gestures
/**
 * Whole-page gestures. Children (buttons, sliders, lists) get first refusal: anything they consume is ignored here.
 *  - double-tap                → onDoubleTap
 *  - horizontal swipe          → onNext / onPrev
 *  - long-press, then drag     → scrub (onScrubStart / onScrub(fraction of width) / onScrubEnd)
 *  - long-press, no drag, on the cover → onLongPressArt
 */
@Composable
private fun Modifier.playerGestures(
    doubleTap: Boolean, swipe: Boolean, invert: Boolean, scrub: Boolean,
    artBounds: () -> Rect?,
    onDoubleTap: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit,
    onLongPressArt: () -> Unit, onLongPressStart: () -> Unit,
    onScrubStart: () -> Unit, onScrub: (Float) -> Unit, onScrubEnd: () -> Unit,
): Modifier {
    val cbDouble by rememberUpdatedState(onDoubleTap)
    val cbNext by rememberUpdatedState(onNext)
    val cbPrev by rememberUpdatedState(onPrev)
    val cbArt by rememberUpdatedState(onLongPressArt)
    val cbLong by rememberUpdatedState(onLongPressStart)
    val cbScrubStart by rememberUpdatedState(onScrubStart)
    val cbScrub by rememberUpdatedState(onScrub)
    val cbScrubEnd by rememberUpdatedState(onScrubEnd)
    val bounds by rememberUpdatedState(artBounds)

    return pointerInput(doubleTap, swipe, invert, scrub) {
        var lastTapTime = 0L
        var lastTapPos = Offset.Zero
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            val slop = viewConfiguration.touchSlop
            val start = down.position
            var upChange: PointerInputChange? = null

            val first = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull "cancel"
                    if (ch.isConsumed) return@withTimeoutOrNull "cancel"
                    if (!ch.pressed) { upChange = ch; return@withTimeoutOrNull "up" }
                    if ((ch.position - start).getDistance() > slop) return@withTimeoutOrNull "move"
                }
                @Suppress("UNREACHABLE_CODE") "cancel"
            }

            when (first) {
                "cancel" -> return@awaitEachGesture
                "up" -> {
                    val up = upChange ?: return@awaitEachGesture
                    if (up.isConsumed || !doubleTap) { lastTapTime = 0L; return@awaitEachGesture }
                    val now = up.uptimeMillis
                    if (lastTapTime != 0L && now - lastTapTime <= viewConfiguration.doubleTapTimeoutMillis &&
                        (up.position - lastTapPos).getDistance() < 120.dp.toPx()
                    ) {
                        lastTapTime = 0L
                        cbDouble()
                    } else {
                        lastTapTime = now; lastTapPos = up.position
                    }
                }
                "move" -> {
                    var decided = false
                    var horizontal = false
                    var total = Offset.Zero
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                        if (ch.isConsumed) return@awaitEachGesture
                        total = ch.position - start
                        if (!decided && total.getDistance() > slop) {
                            decided = true
                            horizontal = abs(total.x) > abs(total.y) * 1.2f
                            if (!horizontal) return@awaitEachGesture
                        }
                        if (horizontal) ch.consume()
                        if (!ch.pressed) break
                    }
                    if (swipe && abs(total.x) > 72.dp.toPx()) {
                        val toRight = total.x > 0
                        if (toRight != invert) cbNext() else cbPrev()
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
                        if (scrubbing) { cbScrub(dx / size.width); ch.consume() }
                        if (!ch.pressed) break
                    }
                    if (scrubbing) cbScrubEnd() else if (onArt) cbArt()
                }
            }
        }
    }
}

// ============================================================================ background
@Composable
private fun PlayerBackground(pt: PlayerTheme, song: Song?, palette: Pair<Color, Color>?, content: @Composable BoxScope.() -> Unit) {
    val t = LocalSudo.current
    when (pt.bg) {
        PBg.App -> SudoBackground(song, content)
        PBg.Gradient -> Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(pt.colors.ifEmpty { listOf(t.bgTop, t.bgBottom) }.let { if (it.size < 2) it + it else it })),
            content = content,
        )
        PBg.ArtGradient -> Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(palette?.first ?: t.bgTop, palette?.second ?: t.bgBottom))),
            content = content,
        )
        PBg.ArtBlur -> Box(Modifier.fillMaxSize().background(Color.Black)) {
            val bmp by rememberArt(song, 256)
            bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize().blur(56.dp).alpha(0.8f), contentScale = ContentScale.Crop) }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.65f)))))
            content()
        }
        PBg.FullBleed -> Box(Modifier.fillMaxSize().background(Color.Black)) {
            val bmp by rememberArt(song, 640)
            bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent, Color.Black.copy(alpha = 0.92f)))))
            content()
        }
        PBg.Blobs -> {
            val tr = rememberInfiniteTransition(label = "blobs")
            val a by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
            val b by tr.animateFloat(1f, 0f, infiniteRepeatable(tween(19000, easing = LinearEasing), RepeatMode.Reverse), label = "b")
            Box(Modifier.fillMaxSize().background(pt.colors.firstOrNull() ?: t.bgTop)) {
                Box(Modifier.fillMaxSize().drawBehind {
                    val cols = pt.colors.drop(1).ifEmpty { listOf(t.primary, t.secondary) }
                    cols.forEachIndexed { i, col ->
                        val ph = if (i % 2 == 0) a else b
                        val cx = size.width * (0.1f + 0.8f * ((ph + i * 0.33f) % 1f))
                        val cy = size.height * (0.15f + 0.7f * (((1f - ph) + i * 0.27f) % 1f))
                        val r = size.width * 0.85f
                        drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.55f), Color.Transparent), center = Offset(cx, cy), radius = r), radius = r, center = Offset(cx, cy))
                    }
                })
                content()
            }
        }
    }
}

@Composable
private fun rememberPalette(song: Song?): State<Pair<Color, Color>?> {
    val bmp by rememberArt(song, 64)
    val b = bmp
    return remember(b) { mutableStateOf(b?.let { computePalette(it) }) }
}

private fun computePalette(b: Bitmap): Pair<Color, Color> {
    val sm = Bitmap.createScaledBitmap(b, 2, 2, true)
    val px = intArrayOf(sm.getPixel(0, 0), sm.getPixel(1, 0), sm.getPixel(0, 1), sm.getPixel(1, 1))
    val hsv = FloatArray(3)
    val best = px.maxBy { android.graphics.Color.colorToHSV(it, hsv); hsv[1] * hsv[2] }
    android.graphics.Color.colorToHSV(best, hsv)
    hsv[2] = hsv[2].coerceIn(0.30f, 0.62f)
    val c1 = android.graphics.Color.HSVToColor(hsv)
    hsv[2] *= 0.35f
    val c2 = android.graphics.Color.HSVToColor(hsv)
    return Color(c1) to Color(c2)
}

// ============================================================================ artwork shapes
@Composable
private fun PlayerArt(pt: PlayerTheme, song: Song?, side: Dp, playing: Boolean, progress: Float) {
    val t = LocalSudo.current
    when (pt.shape) {
        PShape.Rounded -> FramedArt(pt, song, side, 28.dp, RoundedCornerShape(28.dp))
        PShape.Squircle -> FramedArt(pt, song, side, side * 0.28f, RoundedCornerShape(side * 0.28f))
        PShape.Poster -> FramedArt(pt, song, side, 12.dp, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 56.dp, bottomEnd = 56.dp))
        PShape.Circle -> CircleArt(pt, song, side, playing, progress)
        PShape.Polaroid -> {
            val frame = RoundedCornerShape(6.dp)
            Column(
                Modifier.size(side * 0.94f).graphicsLayer { rotationZ = -3f }.shadow(18.dp, frame).background(Color(0xFFFDFBF7), frame)
                    .padding(start = side * 0.05f, end = side * 0.05f, top = side * 0.05f, bottom = side * 0.18f),
            ) {
                Artwork(song, Modifier.fillMaxWidth().weight(1f), 640, shape = RoundedCornerShape(2.dp))
            }
        }
        PShape.Stack -> {
            val sh = RoundedCornerShape(28.dp)
            Box(Modifier.size(side)) {
                Box(Modifier.fillMaxSize(0.88f).align(Alignment.Center).graphicsLayer { rotationZ = -8f; alpha = 0.45f }.clip(sh).background(t.primary.copy(alpha = 0.6f)))
                Box(Modifier.fillMaxSize(0.9f).align(Alignment.Center).graphicsLayer { rotationZ = 6f; alpha = 0.6f }.clip(sh).background(t.secondary.copy(alpha = 0.6f)))
                Artwork(song, Modifier.fillMaxSize(0.86f).align(Alignment.Center).shadow(22.dp, sh), 640, shape = sh)
            }
        }
    }
}

@Composable
private fun FramedArt(pt: PlayerTheme, song: Song?, side: Dp, corner: Dp, shape: androidx.compose.ui.graphics.Shape) {
    val t = LocalSudo.current
    val pulse by rememberInfiniteTransition(label = "glow").animateFloat(
        0.65f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse), label = "pulse",
    )
    val glow = if (pt.glow) Modifier.drawBehind {
        val c = corner.toPx()
        for (i in 1..9) {
            val d = i * 6f
            drawRoundRect(
                t.primary.copy(alpha = 0.11f * (10 - i) / 9f * pulse),
                topLeft = Offset(-d, -d), size = Size(size.width + 2 * d, size.height + 2 * d), cornerRadius = CornerRadius(c + d),
            )
        }
    } else Modifier
    Box(Modifier.size(side).then(glow)) {
        Artwork(song, Modifier.fillMaxSize().shadow(20.dp, shape), 640, shape = shape)
        if (pt.glow) Box(Modifier.fillMaxSize().border(2.dp, t.primary.copy(alpha = 0.9f), shape))
    }
}

@Composable
private fun CircleArt(pt: PlayerTheme, song: Song?, side: Dp, playing: Boolean, progress: Float) {
    val t = LocalSudo.current
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing, pt.spin) {
        if (pt.spin && playing) {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                angle = (angle + (now - last) / 1e9f * 30f) % 360f
                last = now
            }
        }
    }
    Box(Modifier.size(side), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().padding(if (pt.ring) 16.dp else 0.dp).graphicsLayer { rotationZ = if (pt.spin) angle else 0f }.shadow(16.dp, CircleShape)) {
            Artwork(song, Modifier.fillMaxSize(), 640, shape = CircleShape)
            if (pt.spin) Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                for (i in 1..7) drawCircle(Color.White.copy(alpha = 0.05f), radius = r * (0.30f + i * 0.095f), style = Stroke(1.2f))
                drawCircle(Color.Black.copy(alpha = 0.9f), radius = r * 0.055f)
                drawCircle(Color.White.copy(alpha = 0.3f), radius = r * 0.075f, style = Stroke(2f))
            }
        }
        if (pt.ring) Canvas(Modifier.fillMaxSize()) {
            val w = 6.dp.toPx()
            val tl = Offset(w / 2f, w / 2f)
            val sz = Size(size.width - w, size.height - w)
            drawArc(t.text.copy(alpha = 0.18f), -90f, 360f, false, tl, sz, style = Stroke(w))
            drawArc(t.primary, -90f, 360f * progress, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun PlayButton(pt: PlayerTheme, playing: Boolean, onClick: () -> Unit) {
    val t = LocalSudo.current
    val icon = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow
    when (pt.controls) {
        PControls.Gradient -> CircleIcon(onClick, size = 72.dp) { Icon(icon, null, tint = t.onAccent, modifier = Modifier.size(40.dp)) }
        PControls.Outline -> Box(Modifier.size(72.dp).clip(CircleShape).border(2.5.dp, t.primary, CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = t.primary, modifier = Modifier.size(40.dp))
        }
        PControls.Plain -> IconButton(onClick, Modifier.size(72.dp)) { Icon(icon, null, tint = t.text, modifier = Modifier.size(60.dp)) }
    }
}

// ============================================================================ chips
@Composable
private fun SpeedChip(vm: PlayerViewModel) {
    val t = LocalSudo.current
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.sudoPanel(RoundedCornerShape(50)).clickable { open = true }.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Speed, null, tint = t.text, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("${"%.2f".format(vm.speed).trimEnd('0').trimEnd('.')}x", color = t.text)
        }
        DropdownMenu(open, { open = false }) {
            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { sp ->
                DropdownMenuItem(text = { Text("${sp}x") }, onClick = { vm.changeSpeed(sp); open = false })
            }
        }
    }
}

@Composable
private fun EqChip(vm: PlayerViewModel, onClick: () -> Unit) {
    val t = LocalSudo.current
    Row(
        Modifier.sudoPanel(RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Equalizer, null, tint = if (vm.eqEnabled) t.primary else t.text, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("EQ", color = if (vm.eqEnabled) t.primary else t.text)
    }
}

private fun countdown(ms: Long): String {
    val total = ceil(ms / 1000.0).toLong().coerceAtLeast(0)
    val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** Shows "Sleep timer" normally; while a timer runs the chip itself becomes a live mm:ss countdown. */
@Composable
private fun SleepChip(vm: PlayerViewModel) {
    val t = LocalSudo.current
    var open by remember { mutableStateOf(false) }
    val now by produceState(System.currentTimeMillis(), vm.sleepEndsAt) {
        value = System.currentTimeMillis()
        while (vm.sleepEndsAt != 0L) { delay(1000); value = System.currentTimeMillis() }
    }
    val remain = vm.sleepEndsAt - now
    val active = vm.sleepEndsAt != 0L && remain > 0
    Box {
        Row(
            Modifier.sudoPanel(RoundedCornerShape(50)).clickable { open = true }.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Bedtime, null, tint = if (active) t.primary else t.text, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (active) countdown(remain) else "Sleep timer",
                color = if (active) t.primary else t.text, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
            )
        }
        DropdownMenu(open, { open = false }) {
            if (active) {
                DropdownMenuItem(text = { Text("+5 min") }, onClick = { vm.extendSleep(5); open = false })
                DropdownMenuItem(text = { Text("Cancel timer") }, onClick = { vm.setSleep(0); open = false })
            }
            listOf(5, 10, 15, 30, 45, 60, 90).forEach { m ->
                DropdownMenuItem(text = { Text("$m min") }, onClick = { vm.setSleep(m); open = false })
            }
        }
    }
}

// ============================================================================ lyrics / queue
@Composable
private fun LyricsView(vm: PlayerViewModel) {
    val t = LocalSudo.current
    val l = vm.lyrics
    val shape = RoundedCornerShape(24.dp)
    if (l == null) {
        Box(Modifier.fillMaxSize().sudoPanel(shape), contentAlignment = Alignment.Center) {
            Text(
                "No lyrics found\nEmbedded in the MP3, or a .lrc file next to the song",
                color = t.text.copy(alpha = 0.65f), textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp),
            )
        }
        return
    }
    val idx = if (l.synced) l.lines.indexOfLast { it.timeMs <= vm.position }.coerceAtLeast(0) else -1
    val state = rememberLazyListState()
    LaunchedEffect(idx, l) { if (idx >= 0) state.animateScrollToItem((idx - 2).coerceAtLeast(0)) }
    LazyColumn(
        Modifier.fillMaxSize().sudoPanel(shape), state = state,
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(l.lines) { i, line ->
            val cur = i == idx
            Text(
                line.text.ifBlank { "♪" },
                color = if (cur || !l.synced) (if (l.synced) t.primary else t.text) else t.text.copy(alpha = 0.55f),
                fontSize = if (cur) 22.sp else 18.sp,
                fontWeight = if (cur) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.clickable(enabled = l.synced) { vm.seekTo(line.timeMs) },
            )
        }
    }
}

@Composable
private fun QueueList(vm: PlayerViewModel) {
    val t = LocalSudo.current
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (vm.queueIndex - 2).coerceAtLeast(0))
    LazyColumn(Modifier.fillMaxSize().sudoPanel(RoundedCornerShape(24.dp)), state = state) {
        itemsIndexed(vm.queue) { i, s ->
            val cur = i == vm.queueIndex
            Row(
                Modifier.fillMaxWidth().clickable { vm.jumpTo(i) }.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(s, Modifier.size(42.dp), 96, 10.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.title, color = if (cur) t.primary else t.text, fontWeight = if (cur) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(s.artist, color = t.text.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { vm.removeFromQueue(i) }) { Icon(Icons.Rounded.Close, null, tint = t.text.copy(alpha = 0.6f)) }
            }
        }
    }
}
