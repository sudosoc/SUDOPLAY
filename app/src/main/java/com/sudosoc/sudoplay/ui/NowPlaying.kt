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

internal class HudEvent(val id: Int, val icon: ImageVector, val dir: Int)

@Composable
internal fun PlayerContent(vm: PlayerViewModel, pt: PlayerTheme, sheet: SheetState) {
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

internal fun rubber(x: Float, w: Float): Float {
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

@Composable
internal fun PlayButton(pt: PlayerTheme, playing: Boolean, onClick: () -> Unit) {
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

