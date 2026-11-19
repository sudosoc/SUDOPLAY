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

// ============================================================================ model
/** Which player buttons the user wants to see. */
@Immutable
class PlayerButtons(
    val shuffle: Boolean = true, val prev: Boolean = true, val next: Boolean = true, val repeat: Boolean = true,
    val fav: Boolean = true, val playlist: Boolean = true, val lyrics: Boolean = true, val queue: Boolean = true,
    val speed: Boolean = true, val eq: Boolean = true, val sleep: Boolean = true, val ab: Boolean = true,
    val top: List<String> = ButtonOrder.top, val title: List<String> = ButtonOrder.title,
    val transport: List<String> = ButtonOrder.transport, val chips: List<String> = ButtonOrder.chips,
) {
    val anyChip get() = speed || eq || sleep
}

fun playerButtons(s: AppSettings) = PlayerButtons(
    s.btnShuffle.value, s.btnPrev.value, s.btnNext.value, s.btnRepeat.value, s.btnFav.value, s.btnPlaylist.value,
    s.btnLyrics.value, s.btnQueue.value, s.btnSpeed.value, s.btnEq.value, s.btnSleep.value, s.btnAb.value,
    ButtonOrder.parse(s.orderTop.value, ButtonOrder.top), ButtonOrder.parse(s.orderTitle.value, ButtonOrder.title),
    ButtonOrder.parse(s.orderTransport.value, ButtonOrder.transport), ButtonOrder.parse(s.orderChips.value, ButtonOrder.chips),
)

@Immutable
class PlayerSnapshot(
    val title: String, val artist: String, val position: Long, val duration: Long,
    val playing: Boolean, val shuffle: Boolean, val repeat: Int, val fav: Boolean, val seed: Long,
)

class PlayerActions(
    val onClose: () -> Unit = {}, val onFav: () -> Unit = {}, val onAdd: () -> Unit = {},
    val onLyrics: () -> Unit = {}, val onQueue: () -> Unit = {},
    val onShuffle: () -> Unit = {}, val onPrev: () -> Unit = {}, val onPlay: () -> Unit = {}, val onNext: () -> Unit = {}, val onRepeat: () -> Unit = {},
    val onSeek: (Float) -> Unit = {}, val onSeekDone: () -> Unit = {},
)

// ============================================================================ entry
@Composable
fun NowPlayingScreen(vm: PlayerViewModel, sheet: SheetState) {
    val app = LocalSudo.current
    val pt = PlayerThemes.byId(vm.settings.playerTheme.value)
    val eff = remember(app, pt) { effectiveTheme(app, pt) }
    CompositionLocalProvider(LocalSudo provides eff, LocalWave provides vm.wave) {
        PlayerContent(vm, pt, sheet)
    }
}

private class HudEvent(val id: Int, val icon: ImageVector, val dir: Int)

@Composable
private fun PlayerContent(vm: PlayerViewModel, pt: PlayerTheme, sheet: SheetState) {
    val t = LocalSudo.current
    val s = vm.settings
    val song = vm.current
    val view = LocalView.current
    val art by rememberArt(song, 640)

    var mode by remember { mutableIntStateOf(if (s.lyricsDefault.value) 1 else 0) } // 0 art, 1 lyrics, 2 queue
    var coverFor by remember { mutableStateOf<Song?>(null) }
    var showEq by remember { mutableStateOf(false) }
    var artRect by remember { mutableStateOf<Rect?>(null) }
    var scrubBase by remember { mutableLongStateOf(0L) }
    var scrubTarget by remember { mutableStateOf<Long?>(null) }
    var scrubDelta by remember { mutableLongStateOf(0L) }
    var hud by remember { mutableStateOf<HudEvent?>(null) }
    var hudId by remember { mutableIntStateOf(0) }
    var drag by remember { mutableStateOf<Float?>(null) }
    var stretchRaw by remember { mutableFloatStateOf(0f) }
    var stretchHeld by remember { mutableStateOf(false) }
    val stretch = animateFloatAsState(
        stretchRaw, if (stretchHeld) snap() else spring(dampingRatio = 0.34f, stiffness = 230f), label = "stretch",
    )

    DisposableEffect(s.keepScreenOn.value) {
        view.keepScreenOn = s.keepScreenOn.value
        onDispose { view.keepScreenOn = false }
    }

    fun flash(i: ImageVector, dir: Int = 0) { if (s.gestureIcon.value) hud = HudEvent(++hudId, i, dir) }

    val icPlay = I.PlayArrow; val icPause = I.Pause; val icNext = I.SkipNext; val icPrev = I.SkipPrevious
    val icFav = I.Favorite; val icShuffle = I.Shuffle; val icRepeat = I.Repeat; val icLyrics = I.Lyrics
    val icQueue = I.QueueMusic; val icEq = I.Equalizer; val icClose = I.KeyboardArrowDown; val icSpeed = I.Speed

    fun perform(id: String) {
        when (id) {
            "playpause" -> { val willPlay = !vm.isPlaying; vm.togglePlay(); vm.haptics.click(); flash(if (willPlay) icPlay else icPause) }
            "next" -> { vm.next(); vm.haptics.click(); flash(icNext, 1) }
            "prev" -> { vm.previous(); vm.haptics.click(); flash(icPrev, -1) }
            "back10" -> { vm.seekTo((vm.position - 10_000L).coerceAtLeast(0L)); vm.haptics.click(); flash(icPrev, -1) }
            "fwd10" -> { vm.seekTo((vm.position + 10_000L).coerceAtMost(vm.duration)); vm.haptics.click(); flash(icNext, 1) }
            "fav" -> song?.let { vm.store.toggleFav(it.id); vm.haptics.click(); flash(icFav) }
            "shuffle" -> { vm.toggleShuffle(); vm.haptics.click(); flash(icShuffle) }
            "repeat" -> { vm.cycleRepeat(); vm.haptics.click(); flash(icRepeat) }
            "speed" -> { vm.changeSpeedTo(if (abs(vm.speed - 1f) < 0.01f) s.lastSpeedPct.value / 100f else 1f); vm.haptics.click(); flash(icSpeed) }
            "lyrics" -> { mode = if (mode == 1) 0 else 1; vm.haptics.click(); flash(icLyrics) }
            "queue" -> { mode = if (mode == 2) 0 else 2; vm.haptics.click(); flash(icQueue) }
            "eq" -> { showEq = true; vm.haptics.click() }
            "ab" -> vm.abCycle()
            "cover" -> song?.let { coverFor = it }
            "carmode" -> { vm.carMode = true; sheet.close() }
            "bookmark" -> { vm.addBookmark(); android.widget.Toast.makeText(vm.appContext(), "Bookmarked", android.widget.Toast.LENGTH_SHORT).show() }
            "download" -> song?.let { s -> if (s.online) vm.downloadCurrent() else android.widget.Toast.makeText(vm.appContext(), "This song is already on your phone", android.widget.Toast.LENGTH_SHORT).show() }
            "clip" -> song?.let { vm.clipSong = it; sheet.close() }
            "close" -> { vm.haptics.tick(); sheet.close() }
        }
    }

    val gestures = Modifier.playerGestures(
        doubleTap = s.doubleTap.value, swipe = s.swipe.value, scrub = s.scrub.value,
        hasUp = s.gSwU.value != "none", hasTwo = s.gTwo.value != "none",
        artBounds = { artRect },
        onDoubleTap = { zone -> perform(when (zone) { 0 -> s.gDtL.value; 2 -> s.gDtR.value; else -> s.gDtC.value }) },
        onSwipe = { right -> perform(if (right) s.gSwR.value else s.gSwL.value) },
        onSwipeUp = { perform(s.gSwU.value) },
        onTwoFinger = { perform(s.gTwo.value) },
        onLongPressRelease = { onArt -> val a = s.gLp.value; if (a == "cover") { if (onArt) perform(a) } else if (a != "none") perform(a) },
        onLongPressStart = { vm.haptics.heavy() },
        onScrubStart = { scrubBase = vm.position; vm.haptics.heavy() },
        onScrub = { fraction ->
            val delta = (fraction * s.scrubSeconds.value * 1000f).toLong()
            if (delta / 5000 != scrubDelta / 5000) vm.haptics.tick()
            scrubDelta = delta
            scrubTarget = (scrubBase + delta).coerceIn(0L, vm.duration.coerceAtLeast(1L))
        },
        onScrubEnd = { scrubTarget?.let { vm.seekTo(it) }; scrubTarget = null },
        onStretch = { px, held -> stretchRaw = px; stretchHeld = held },
        onSheetStart = { sheet.dragStart() },
        onSheetDrag = { sheet.dragBy(it) },
        onSheetSettle = { v -> sheet.settle(v); if (v > 1400f || sheet.frac < 0.78f) vm.haptics.tick() },
    )

    val btn = playerButtons(s)
    val dur = vm.duration.coerceAtLeast(1L)
    val shown = (scrubTarget ?: drag?.toLong() ?: vm.position).coerceIn(0L, dur)
    val snap = PlayerSnapshot(
        song?.title ?: "—", song?.artist ?: "", shown, vm.duration, vm.isPlaying, vm.shuffle, vm.repeatMode,
        song?.let { vm.store.isFav(it.id) } ?: false, song?.id ?: 0L,
    )
    val actions = PlayerActions(
        onClose = { sheet.close() },
        onFav = { song?.let { vm.store.toggleFav(it.id); vm.haptics.click() } },
        onAdd = { vm.playlistTarget = song },
        onLyrics = { mode = if (mode == 1) 0 else 1 },
        onQueue = { mode = if (mode == 2) 0 else 2 },
        onShuffle = { vm.haptics.click(); vm.toggleShuffle() },
        onPrev = { vm.haptics.click(); vm.previous() },
        onPlay = { vm.haptics.click(); vm.togglePlay() },
        onNext = { vm.haptics.click(); vm.next() },
        onRepeat = { vm.haptics.click(); vm.cycleRepeat() },
        onSeek = { drag = it * dur },
        onSeekDone = { drag?.let { vm.seekTo(it.toLong()); vm.haptics.tick() }; drag = null },
    )

    val queueMid: @Composable BoxScope.() -> Unit = { QueueList(vm) }
    val lyricsMid: @Composable BoxScope.() -> Unit = { LyricsView(vm) }
    val middle: (@Composable BoxScope.() -> Unit)? = when (mode) { 2 -> queueMid; 1 -> lyricsMid; else -> null }

    PlayerBackground(pt, art, animated = true) {
        Box(Modifier.fillMaxSize().then(gestures)) {
            PlayerLayout(
                pt = pt, art = art, snap = snap, btn = btn, seekStyle = s.seekStyle.value, mode = mode, act = actions,
                animated = true, onArtBounds = { artRect = it },
                abSlot = { AbButton(vm) },
                topChip = { DownloadChip(vm) },
                middle = middle,
                extras = {
                    btn.chips.forEach { id ->
                        when (id) {
                            "speed" -> if (btn.speed) SpeedChip(vm)
                            "eq" -> if (btn.eq) EqChip(vm) { showEq = true }
                            "sleep" -> if (btn.sleep) SleepChip(vm)
                        }
                    }
                },
                modifier = Modifier.systemBarsPadding().graphicsLayer {
                    val w = size.width
                    val sv = rubber(stretch.value, w)
                    val k = abs(sv) / w
                    transformOrigin = TransformOrigin(if (sv > 0f) 0f else 1f, 0.5f)
                    scaleX = 1f + k * 0.9f
                    scaleY = 1f - k * 0.18f
                },
            )
            GestureHud(hud, Modifier.align(Alignment.Center))
            AnimatedVisibility(scrubTarget != null, Modifier.align(Alignment.Center), enter = fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.85f), exit = fadeOut()) {
                ScrubOverlay(scrubDelta, scrubTarget ?: 0L, vm.duration)
            }
        }
    }

    AnimatedVisibility(showEq, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
        EqStudio(vm) { showEq = false }
    }
    coverFor?.let { CoverFlow(it) { coverFor = null } }
}

private fun rubber(x: Float, w: Float): Float {
    val c = w * 0.30f
    return sign(x) * c * (1f - exp(-abs(x) / c))
}

// ============================================================================ shared layout (real player + theme previews)
@Composable
fun PlayerLayout(
    pt: PlayerTheme,
    art: Bitmap?,
    snap: PlayerSnapshot,
    btn: PlayerButtons,
    seekStyle: String,
    mode: Int,
    act: PlayerActions,
    animated: Boolean,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    onArtBounds: (Rect?) -> Unit = {},
    middle: (@Composable BoxScope.() -> Unit)? = null,
    abSlot: (@Composable () -> Unit)? = null,
    extras: @Composable RowScope.() -> Unit = {},
    topChip: (@Composable () -> Unit)? = null,
) {
    val t = LocalSudo.current
    Column(modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 8.dp)) {
        // ---- top bar
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = act.onClose) { Icon(I.KeyboardArrowDown, null, tint = t.text, modifier = Modifier.size(32.dp)) }
            Text(
                when (mode) { 1 -> "Lyrics"; 2 -> "Queue"; else -> "Now playing" }, color = t.text.copy(alpha = 0.8f),
                modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            )
            val topShown = btn.top.filter { (it == "lyrics" && btn.lyrics) || (it == "queue" && btn.queue) }
            topShown.forEach { id ->
                if (id == "lyrics") IconButton(onClick = act.onLyrics) { Icon(I.Lyrics, null, tint = if (mode == 1) t.primary else t.text) }
                else IconButton(onClick = act.onQueue) { Icon(I.QueueMusic, null, tint = if (mode == 2) t.primary else t.text) }
            }
            repeat(2 - topShown.size) { Spacer(Modifier.size(48.dp)) }
        }
        if (topChip != null) Box(Modifier.padding(bottom = 4.dp)) { topChip() }

        // ---- art / lyrics / queue
        Box(
            Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { onArtBounds(if (mode == 0) it.boundsInRoot() else null) },
            contentAlignment = Alignment.Center,
        ) {
            if (middle != null) middle()
            else if (pt.bg != PBg.FullBleed) BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val side = minOf(maxWidth, maxHeight)
                val progress = if (snap.duration > 0) (snap.position.toFloat() / snap.duration).coerceIn(0f, 1f) else 0f
                PlayerArt(pt, art, side, snap.playing, progress, animated)
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- title + favorite
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(snap.title, color = t.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(snap.artist, color = t.text.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            btn.title.forEach { id ->
                if (id == "fav" && btn.fav) IconButton(onClick = act.onFav) {
                    Icon(if (snap.fav) I.Favorite else I.FavoriteBorder, null, tint = if (snap.fav) t.primary else t.text)
                }
                if (id == "add" && btn.playlist) IconButton(onClick = act.onAdd) { Icon(I.Add, null, tint = t.text) }
            }
        }

        // ---- seek bar
        val dur = snap.duration.coerceAtLeast(1L).toFloat()
        SeekBar(
            style = seekStyle, progress = snap.position / dur, onChange = act.onSeek, onDone = act.onSeekDone,
            seed = snap.seed, interactive = interactive, animated = animated,
        )
        Row(Modifier.fillMaxWidth()) {
            Text(fmt(snap.position), color = t.text.copy(alpha = 0.65f), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            if (btn.ab) { if (abSlot != null) abSlot() else Text("A–B", color = t.text.copy(alpha = 0.7f), fontSize = 12.sp) }
            Spacer(Modifier.weight(1f))
            Text(fmt(snap.duration), color = t.text.copy(alpha = 0.65f), style = MaterialTheme.typography.labelMedium)
        }

        // ---- transport
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            btn.transport.forEach { id ->
                when (id) {
                    "shuffle" -> if (btn.shuffle) IconButton(onClick = act.onShuffle) { Icon(I.Shuffle, null, tint = if (snap.shuffle) t.primary else t.text.copy(alpha = 0.7f)) }
                    "prev" -> if (btn.prev) IconButton(onClick = act.onPrev) { Icon(I.SkipPrevious, null, tint = t.text, modifier = Modifier.size(38.dp)) }
                    "play" -> PlayButton(pt, snap.playing, act.onPlay)
                    "next" -> if (btn.next) IconButton(onClick = act.onNext) { Icon(I.SkipNext, null, tint = t.text, modifier = Modifier.size(38.dp)) }
                    "repeat" -> if (btn.repeat) IconButton(onClick = act.onRepeat) {
                        Icon(
                            if (snap.repeat == Player.REPEAT_MODE_ONE) I.RepeatOne else I.Repeat, null,
                            tint = if (snap.repeat == Player.REPEAT_MODE_OFF) t.text.copy(alpha = 0.7f) else t.primary,
                        )
                    }
                }
            }
        }

        // ---- extras
        if (btn.anyChip) Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, content = extras)
    }
}

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
private fun Modifier.playerGestures(
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
private fun GestureHud(ev: HudEvent?, modifier: Modifier = Modifier) {
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
private fun ScrubOverlay(delta: Long, target: Long, duration: Long) {
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

// ============================================================================ background
@Composable
fun rememberPhase(animated: Boolean, ms: Int = 4000): State<Float> =
    if (animated) rememberInfiniteTransition(label = "phase").animateFloat(0f, 1f, infiniteRepeatable(tween(ms, easing = LinearEasing), RepeatMode.Restart), label = "p")
    else remember { mutableFloatStateOf(0.35f) }

private fun hashF(i: Int, k: Int): Float {
    var x = i * 73856093 xor k * 19349663
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0xFFFF) / 65535f
}

/** Soft, cheap "blur": a tiny copy of the artwork stretched up with smoothing. Real blur is added on Android 12+. */
@Composable
private fun SoftArt(art: Bitmap?, modifier: Modifier, alpha: Float, realBlur: Boolean) {
    val small = remember(art) { art?.let { Bitmap.createScaledBitmap(it, 48, 48, true) } }
    small?.let {
        val m = if (realBlur && Build.VERSION.SDK_INT >= 31) modifier.blur(28.dp) else modifier
        Image(it.asImageBitmap(), null, m.alpha(alpha), contentScale = ContentScale.Crop, filterQuality = FilterQuality.Medium)
    }
}

@Composable
fun PlayerBackground(pt: PlayerTheme, art: Bitmap?, animated: Boolean, content: @Composable BoxScope.() -> Unit) {
    val t = LocalSudo.current
    val c0 = pt.colors.getOrNull(0) ?: t.bgTop
    val c1 = pt.colors.getOrNull(1) ?: t.primary
    val c2 = pt.colors.getOrNull(2) ?: t.secondary
    val c3 = pt.colors.getOrNull(3) ?: c1
    when (pt.bg) {
        PBg.App -> Box(Modifier.fillMaxSize()) { AppBackdrop(if (t.style == Style.Art) art else null); content() }
        PBg.Gradient -> Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(pt.colors.ifEmpty { listOf(t.bgTop, t.bgBottom) }.let { if (it.size < 2) it + it else it })),
            content = content,
        )
        PBg.ArtGradient -> {
            val pal = remember(art) { art?.let(::computePalette) }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(pal?.first ?: t.bgTop, pal?.second ?: t.bgBottom))), content = content)
        }
        PBg.ArtBlur -> Box(Modifier.fillMaxSize().background(Color.Black)) {
            SoftArt(art, Modifier.fillMaxSize(), 0.85f, animated)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.65f)))))
            content()
        }
        PBg.FullBleed -> Box(Modifier.fillMaxSize().background(Color.Black)) {
            art?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent, Color.Black.copy(alpha = 0.92f)))))
            content()
        }
        PBg.Blobs -> {
            val a = rememberPhase(animated, 14000)
            Box(Modifier.fillMaxSize().background(c0)) {
                Box(Modifier.fillMaxSize().drawBehind {
                    val ph = a.value
                    val cols = pt.colors.drop(1).ifEmpty { listOf(t.primary, t.secondary) }
                    cols.forEachIndexed { i, col ->
                        val q = if (i % 2 == 0) ph else 1f - ph
                        val cx = size.width * (0.1f + 0.8f * ((q + i * 0.33f) % 1f))
                        val cy = size.height * (0.15f + 0.7f * (((1f - q) + i * 0.27f) % 1f))
                        val r = size.width * 0.85f
                        drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.55f), Color.Transparent), center = Offset(cx, cy), radius = r), radius = r, center = Offset(cx, cy))
                    }
                })
                content()
            }
        }
        PBg.Spotlight -> Box(Modifier.fillMaxSize().background(c0)) {
            Box(Modifier.fillMaxSize().drawBehind {
                val ctr = Offset(size.width * 0.5f, size.height * 0.20f)
                val r = size.width * 1.05f
                drawCircle(Brush.radialGradient(listOf(c1.copy(alpha = 0.60f), c1.copy(alpha = 0.18f), Color.Transparent), ctr, r), r, ctr)
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))))
            })
            content()
        }
        PBg.Waves -> {
            val ph = rememberPhase(animated, 7000)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(c0, c1.copy(alpha = 0.55f).compositeOver(c0))))) {
                Box(Modifier.fillMaxSize().drawBehind { drawWaves(ph.value, listOf(c1, c2, t.primary)) })
                content()
            }
        }
        PBg.Spectrum -> {
            val ph = rememberPhase(animated, 1000)
            Box(Modifier.fillMaxSize().background(c0)) {
                Box(Modifier.fillMaxSize().drawBehind {
                    @Suppress("UNUSED_EXPRESSION") ph.value
                    val fresh = animated && SystemClock.elapsedRealtime() - FxBus.lastUpdate < 350
                    val n = 32
                    val gap = 4.dp.toPx(); val bw = (size.width - gap * (n - 1)) / n
                    val maxH = size.height * 0.42f
                    for (i in 0 until n) {
                        val v = if (fresh) FxBus.levels[i] else 0.18f + 0.55f * hashF(i, 3) * (1f - i / (n * 1.6f))
                        val h = (maxH * v).coerceAtLeast(4.dp.toPx())
                        drawRoundRect(
                            Brush.verticalGradient(listOf(c1.copy(alpha = 0.75f), c2.copy(alpha = 0.18f)), size.height - h, size.height),
                            Offset(i * (bw + gap), size.height - h), Size(bw, h), CornerRadius(bw / 2),
                        )
                    }
                })
                content()
            }
        }
        PBg.Stars -> {
            val ph = rememberPhase(animated, 5000)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(c0, pt.colors.lastOrNull() ?: c0)))) {
                Box(Modifier.fillMaxSize().drawBehind {
                    val tw = ph.value * 2f * PI.toFloat()
                    for (i in 0 until 90) {
                        val x = hashF(i, 1) * size.width
                        val y = hashF(i, 2) * size.height
                        val r = (0.6f + 1.6f * hashF(i, 3)).dp.toPx()
                        val a = 0.30f + 0.70f * (0.5f + 0.5f * sin(tw + i * 1.3f))
                        drawCircle(Color.White.copy(alpha = a * 0.9f), r, Offset(x, y))
                    }
                })
                content()
            }
        }
        PBg.Aurora, PBg.Rain, PBg.Fireflies, PBg.Pulse, PBg.Lava, PBg.Snow, PBg.Matrix, PBg.Bokeh, PBg.Ripples, PBg.Embers, PBg.Nebula ->
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(c0, (pt.colors.getOrNull(1) ?: c0).copy(alpha = 0.35f).compositeOver(c0))))) {
                LiveLayer(pt.bg, pt.colors, animated)
                content()
            }
        PBg.Mesh -> Box(Modifier.fillMaxSize().background(c0)) {
            Box(Modifier.fillMaxSize().drawBehind {
                val spots = listOf(Offset(0f, 0f) to c0, Offset(size.width, size.height * 0.30f) to c1, Offset(0f, size.height * 0.72f) to c2, Offset(size.width, size.height) to c3)
                val r = size.width * 1.0f
                spots.forEachIndexed { i, (o, col) ->
                    if (i == 0) return@forEachIndexed
                    drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.85f), Color.Transparent), o, r), r, o)
                }
                drawCircle(Brush.radialGradient(listOf(c0.copy(alpha = 0.9f), Color.Transparent), Offset.Zero, r), r, Offset.Zero)
                drawRect(Color.Black.copy(alpha = if (t.dark) 0.18f else 0f))
            })
            content()
        }
    }
}

private fun DrawScope.drawWaves(ph: Float, colors: List<Color>) {
    val twoPi = 2f * PI.toFloat()
    for (layer in 0 until 3) {
        val amp = size.height * (0.018f + 0.010f * layer)
        val base = size.height * (0.72f + 0.07f * layer)
        val dir = if (layer % 2 == 0) 1f else -1f
        val path = Path().apply {
            moveTo(0f, size.height); lineTo(0f, base)
            var x = 0f
            while (x <= size.width + 10f) {
                lineTo(x, base + amp * sin(x / size.width * twoPi * (1.2f + layer * 0.45f) + ph * twoPi * dir + layer))
                x += 10f
            }
            lineTo(size.width, size.height); close()
        }
        drawPath(path, colors[layer % colors.size].copy(alpha = 0.20f + 0.10f * layer))
    }
}

@Composable
private fun rememberPalette(art: Bitmap?): Pair<Color, Color>? = remember(art) { art?.let(::computePalette) }

fun computePalette(b: Bitmap): Pair<Color, Color> {
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
private val HexShape = GenericShape { size, _ ->
    val w = size.width; val h = size.height
    moveTo(w * 0.25f, 0f); lineTo(w * 0.75f, 0f); lineTo(w, h / 2f); lineTo(w * 0.75f, h); lineTo(w * 0.25f, h); lineTo(0f, h / 2f); close()
}
private val DiamondShape = GenericShape { size, _ ->
    moveTo(size.width / 2f, 0f); lineTo(size.width, size.height / 2f); lineTo(size.width / 2f, size.height); lineTo(0f, size.height / 2f); close()
}

@Composable
private fun PlayerArt(pt: PlayerTheme, art: Bitmap?, side: Dp, playing: Boolean, progress: Float, animated: Boolean) {
    val t = LocalSudo.current
    when (pt.shape) {
        PShape.Rounded -> FramedArt(pt, art, side, side, RoundedCornerShape(28.dp))
        PShape.Squircle -> FramedArt(pt, art, side, side, RoundedCornerShape(side * 0.28f))
        PShape.Square -> FramedArt(pt, art, side, side, RoundedCornerShape(4.dp))
        PShape.Hexagon -> FramedArt(pt, art, side * 0.96f, side * 0.84f, HexShape)
        PShape.Diamond -> FramedArt(pt, art, side * 0.82f, side * 0.82f, DiamondShape)
        PShape.Wide -> FramedArt(pt, art, side, side * 0.62f, RoundedCornerShape(22.dp))
        PShape.Poster -> FramedArt(pt, art, side, side, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 56.dp, bottomEnd = 56.dp))
        PShape.Circle -> CircleArt(pt, art, side, playing, progress, animated)
        PShape.Polaroid -> {
            val frame = RoundedCornerShape(6.dp)
            Column(
                Modifier.size(side * 0.94f).graphicsLayer { rotationZ = -3f }.shadow(18.dp, frame).background(Color(0xFFFDFBF7), frame)
                    .padding(start = side * 0.05f, end = side * 0.05f, top = side * 0.05f, bottom = side * 0.18f),
            ) {
                ArtImage(art, Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(2.dp))
            }
        }
        PShape.Frame -> {
            val outer = RoundedCornerShape(6.dp)
            Box(
                Modifier.size(side * 0.94f).shadow(20.dp, outer)
                    .background(Brush.linearGradient(listOf(t.primary, t.secondary, t.primary)), outer).padding(side * 0.03f),
            ) {
                Box(Modifier.fillMaxSize().background(if (t.dark) Color(0xFF14110D) else Color(0xFFFBF8F2)).padding(side * 0.055f)) {
                    ArtImage(art, Modifier.fillMaxSize(), shape = RoundedCornerShape(1.dp))
                }
            }
        }
        PShape.Stack -> {
            val sh = RoundedCornerShape(28.dp)
            Box(Modifier.size(side)) {
                Box(Modifier.fillMaxSize(0.88f).align(Alignment.Center).graphicsLayer { rotationZ = -8f; alpha = 0.45f }.clip(sh).background(t.primary.copy(alpha = 0.6f)))
                Box(Modifier.fillMaxSize(0.9f).align(Alignment.Center).graphicsLayer { rotationZ = 6f; alpha = 0.6f }.clip(sh).background(t.secondary.copy(alpha = 0.6f)))
                ArtImage(art, Modifier.fillMaxSize(0.86f).align(Alignment.Center).shadow(22.dp, sh), shape = sh)
            }
        }
    }
}

@Composable
private fun FramedArt(pt: PlayerTheme, art: Bitmap?, w: Dp, h: Dp, shape: Shape) {
    val t = LocalSudo.current
    val sh = if (pt.glow) Modifier.shadow(28.dp, shape, ambientColor = t.primary, spotColor = t.primary) else Modifier.shadow(20.dp, shape)
    Box(Modifier.size(w, h)) {
        ArtImage(art, Modifier.fillMaxSize().then(sh), shape = shape)
        if (pt.glow) Box(Modifier.fillMaxSize().border(2.dp, t.primary.copy(alpha = 0.9f), shape))
    }
}

@Composable
private fun CircleArt(pt: PlayerTheme, art: Bitmap?, side: Dp, playing: Boolean, progress: Float, animated: Boolean) {
    val t = LocalSudo.current
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing, pt.spin, animated) {
        if (pt.spin && playing && animated) {
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
            ArtImage(art, Modifier.fillMaxSize(), shape = CircleShape)
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
    val icon = if (playing) I.Pause else I.PlayArrow
    when (pt.controls) {
        PControls.Gradient -> CircleIcon(onClick, size = 72.dp) { Icon(icon, null, tint = t.onAccent, modifier = Modifier.size(40.dp)) }
        PControls.Outline -> Box(Modifier.size(72.dp).clip(CircleShape).border(2.5.dp, t.primary, CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = t.primary, modifier = Modifier.size(40.dp))
        }
        PControls.Plain -> IconButton(onClick, Modifier.size(72.dp)) { Icon(icon, null, tint = t.text, modifier = Modifier.size(60.dp)) }
        PControls.Pill -> Box(
            Modifier.size(width = 108.dp, height = 60.dp).clip(RoundedCornerShape(30.dp))
                .background(Brush.horizontalGradient(listOf(t.primary, t.secondary))).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = t.onAccent, modifier = Modifier.size(36.dp)) }
        PControls.Glass -> Box(
            Modifier.size(72.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.08f))))
                .border(1.2.dp, Color.White.copy(alpha = 0.45f), CircleShape).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = t.text, modifier = Modifier.size(38.dp)) }
        PControls.Neon -> Box(
            Modifier.size(72.dp).drawBehind {
                for (i in 1..6) drawCircle(t.primary.copy(alpha = 0.10f * (7 - i) / 6f), size.minDimension / 2f + i * 3.dp.toPx(), style = Stroke(3.dp.toPx()))
            }.clip(CircleShape).border(2.dp, t.primary, CircleShape).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = t.primary, modifier = Modifier.size(38.dp)) }
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
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text(
                    "No lyrics found\nEmbedded in the MP3, or a .lrc file next to the song",
                    color = t.text.copy(alpha = 0.65f), textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    if (vm.lyricsBusy) "Searching…" else "Find lyrics online", color = t.onAccent, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(t.primary, t.secondary)))
                        .clickable(enabled = !vm.lyricsBusy) { vm.findLyricsOnline() }.padding(horizontal = 22.dp, vertical = 10.dp),
                )
                Text("Sends only the title, artist and length to lrclib.net", color = t.text.copy(alpha = 0.45f), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            }
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
                IconButton(onClick = { vm.removeFromQueue(i) }) { Icon(I.Close, null, tint = t.text.copy(alpha = 0.6f)) }
            }
        }
    }
}
