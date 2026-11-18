package com.sudosoc.sudoplay.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.sudosoc.sudoplay.PlayerViewModel
import com.sudosoc.sudoplay.data.Smart
import com.sudosoc.sudoplay.data.Song
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Landscape UI for a car head unit: huge touch targets, readable at arm's length, nothing that needs precision.
 * Three pages: Now playing, Library (with an A–Z strip) and Queue.
 */
@Composable
fun CarScreen(vm: PlayerViewModel, onExit: () -> Unit) {
    val t = LocalSudo.current
    val ctx = LocalContext.current
    val view = LocalView.current
    var page by rememberSaveable { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(15_000) } }
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = vm.settings.keepScreenOn.value } }
    BackHandler(onBack = onExit)

    SudoBackground(vm.current) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 28.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf("Now playing", "Library", "Queue").forEachIndexed { i, n -> CarTab(n, page == i) { page = i; vm.haptics.tick() } }
                Spacer(Modifier.weight(1f))
                Text(android.text.format.DateFormat.getTimeFormat(ctx).format(Date(now)), color = t.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Exit", color = t.text, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).sudoPanel(RoundedCornerShape(50)).clickable(onClick = onExit).padding(horizontal = 26.dp, vertical = 14.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (page) {
                    0 -> CarNowPlaying(vm)
                    1 -> CarLibrary(vm) { page = 0 }
                    else -> CarQueue(vm)
                }
            }
        }
    }
}

@Composable
private fun CarTab(label: String, on: Boolean, onClick: () -> Unit) {
    val t = LocalSudo.current
    val shape = RoundedCornerShape(50)
    Text(
        label, color = if (on) t.onAccent else t.text, fontWeight = FontWeight.Bold, fontSize = 22.sp,
        modifier = Modifier.clip(shape)
            .then(if (on) Modifier.background(Brush.horizontalGradient(listOf(t.primary, t.secondary)), shape) else Modifier.sudoPanel(shape))
            .clickable(onClick = onClick).padding(horizontal = 30.dp, vertical = 14.dp),
    )
}

@Composable
private fun CarNowPlaying(vm: PlayerViewModel) {
    val t = LocalSudo.current
    val song = vm.current
    var drag by remember { mutableStateOf<Float?>(null) }
    val dur = vm.duration.coerceAtLeast(1L)
    val shown = (drag?.toLong() ?: vm.position).coerceIn(0L, dur)
    val l = vm.lyrics
    val line = if (l != null && l.synced) l.lines.lastOrNull { it.timeMs <= vm.position }?.text?.takeIf { it.isNotBlank() } else null

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(34.dp)) {
        Artwork(song, Modifier.fillMaxHeight().aspectRatio(1f), size = 720, corner = 32.dp)
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(song?.title ?: "Nothing playing", color = t.text, fontSize = 40.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 46.sp)
                Text(song?.artist ?: "", color = t.text.copy(alpha = 0.75f), fontSize = 26.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (line != null) Text(line, color = t.primary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            }
            Column {
                Slider(
                    value = shown.toFloat() / dur, onValueChange = { drag = it * dur }, onValueChangeFinished = { drag?.let { vm.seekTo(it.toLong()) }; drag = null },
                    colors = SliderDefaults.colors(thumbColor = t.primary, activeTrackColor = t.primary, inactiveTrackColor = t.text.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(fmt(shown), color = t.text.copy(alpha = 0.7f), fontSize = 20.sp)
                    Spacer(Modifier.weight(1f))
                    Text(fmt(vm.duration), color = t.text.copy(alpha = 0.7f), fontSize = 20.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton({ vm.haptics.click(); vm.toggleShuffle() }, Modifier.size(84.dp)) { Icon(I.Shuffle, null, tint = if (vm.shuffle) t.primary else t.text.copy(alpha = 0.7f), modifier = Modifier.size(44.dp)) }
                IconButton({ vm.haptics.click(); vm.previous() }, Modifier.size(110.dp)) { Icon(I.SkipPrevious, null, tint = t.text, modifier = Modifier.size(74.dp)) }
                Box(
                    Modifier.size(150.dp).clip(CircleShape).background(Brush.linearGradient(listOf(t.primary, t.secondary))).clickable { vm.haptics.click(); vm.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) { Icon(if (vm.isPlaying) I.Pause else I.PlayArrow, null, tint = t.onAccent, modifier = Modifier.size(90.dp)) }
                IconButton({ vm.haptics.click(); vm.next() }, Modifier.size(110.dp)) { Icon(I.SkipNext, null, tint = t.text, modifier = Modifier.size(74.dp)) }
                IconButton({ vm.haptics.click(); vm.cycleRepeat() }, Modifier.size(84.dp)) {
                    Icon(
                        if (vm.repeatMode == Player.REPEAT_MODE_ONE) I.RepeatOne else I.Repeat, null,
                        tint = if (vm.repeatMode == Player.REPEAT_MODE_OFF) t.text.copy(alpha = 0.7f) else t.primary, modifier = Modifier.size(44.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CarLibrary(vm: PlayerViewModel, onPlayed: () -> Unit) {
    val t = LocalSudo.current
    val scope = rememberCoroutineScope()
    var src by rememberSaveable { mutableIntStateOf(0) }
    val names = listOf("All songs", "Favorites", "Recent", "Most played", "Never played")
    val list: List<Song> = remember(vm.songs, src, vm.plays, vm.recent, vm.store.items.toList()) {
        when (src) {
            1 -> vm.songs.filter { vm.store.isFav(it.id) }.sortedBy { it.title.lowercase() }
            2 -> vm.smart(Smart.Recent)
            3 -> vm.smart(Smart.Most)
            4 -> vm.smart(Smart.Unplayed).sortedBy { it.title.lowercase() }
            else -> vm.songs.sortedBy { it.title.lowercase() }
        }
    }
    val state = rememberLazyListState()
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.width(260.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            names.forEachIndexed { i, n -> CarTab(n, src == i) { src = i } }
            Text(
                "Shuffle all", color = t.text, fontWeight = FontWeight.Bold, fontSize = 22.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).sudoPanel(RoundedCornerShape(50))
                    .clickable { vm.shufflePlay(list); onPlayed() }.padding(horizontal = 30.dp, vertical = 14.dp),
            )
        }
        LazyColumn(Modifier.weight(1f).fillMaxHeight(), state = state, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
                CarSongRow(s, vm.current?.id == s.id) { vm.play(list, i); onPlayed() }
            }
        }
        if (src == 0 || src == 1 || src == 4) {
            val letters = remember { listOf("#") + ('A'..'Z').map { it.toString() } }
            Column(Modifier.fillMaxHeight().width(44.dp), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
                letters.forEach { L ->
                    Text(
                        L, color = t.primary, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                        modifier = Modifier.clickable {
                            val idx = list.indexOfFirst {
                                val c = it.title.trim().firstOrNull()?.uppercaseChar() ?: '#'
                                if (L == "#") !c.isLetter() else c >= L[0]
                            }
                            if (idx >= 0) scope.launch { state.scrollToItem(idx) }
                        }.padding(vertical = 2.dp, horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CarQueue(vm: PlayerViewModel) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (vm.queueIndex - 1).coerceAtLeast(0))
    LazyColumn(Modifier.fillMaxSize(), state = state, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(vm.queue) { i, s -> CarSongRow(s, i == vm.queueIndex) { vm.jumpTo(i) } }
    }
}

@Composable
private fun CarSongRow(s: Song, current: Boolean, onClick: () -> Unit) {
    val t = LocalSudo.current
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).sudoPanel(shape).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Artwork(s, Modifier.size(68.dp), size = 160, corner = 14.dp)
        Column(Modifier.weight(1f)) {
            Text(s.title, color = if (current) t.primary else t.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.artist, color = t.text.copy(alpha = 0.7f), fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(fmt(s.durationMs), color = t.text.copy(alpha = 0.6f), fontSize = 18.sp)
    }
}
