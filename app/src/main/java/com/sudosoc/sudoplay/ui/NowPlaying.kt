package com.sudosoc.sudoplay.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.sudosoc.sudoplay.PlayerViewModel

@Composable
fun NowPlayingScreen(vm: PlayerViewModel, onClose: () -> Unit) {
    val t = LocalSudo.current
    val song = vm.current
    var showQueue by remember { mutableStateOf(false) }

    SudoBackground(song) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 22.dp, vertical = 8.dp)) {
            // top bar
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, null, tint = t.text, modifier = Modifier.size(32.dp)) }
                Text(
                    if (showQueue) "Queue" else "Now playing", color = t.text.copy(alpha = 0.8f),
                    modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = { showQueue = !showQueue }) {
                    Icon(Icons.Rounded.QueueMusic, null, tint = if (showQueue) t.primary else t.text)
                }
            }

            // art or queue
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (showQueue) QueueList(vm) else BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val s = minOf(maxWidth, maxHeight)
                    Artwork(song, Modifier.size(s).shadow(24.dp, RoundedCornerShape(28.dp)), 640, 28.dp)
                }
            }

            Spacer(Modifier.height(16.dp))

            // title + favorite
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

            // seek bar
            var drag by remember { mutableStateOf<Float?>(null) }
            val dur = vm.duration.coerceAtLeast(1L).toFloat()
            Slider(
                value = (drag ?: vm.position.toFloat()).coerceIn(0f, dur),
                onValueChange = { drag = it },
                onValueChangeFinished = { drag?.let { vm.seekTo(it.toLong()) }; drag = null },
                valueRange = 0f..dur,
                colors = SliderDefaults.colors(thumbColor = t.primary, activeTrackColor = t.primary, inactiveTrackColor = t.text.copy(alpha = 0.2f)),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(fmt((drag ?: vm.position.toFloat()).toLong()), color = t.text.copy(alpha = 0.65f), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Text(fmt(vm.duration), color = t.text.copy(alpha = 0.65f), style = MaterialTheme.typography.labelMedium)
            }

            // transport
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::toggleShuffle) {
                    Icon(Icons.Rounded.Shuffle, null, tint = if (vm.shuffle) t.primary else t.text.copy(alpha = 0.7f))
                }
                IconButton(onClick = vm::previous) { Icon(Icons.Rounded.SkipPrevious, null, tint = t.text, modifier = Modifier.size(38.dp)) }
                CircleIcon(onClick = vm::togglePlay, size = 72.dp) {
                    Icon(if (vm.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null,
                        tint = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.85f),
                        modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = vm::next) { Icon(Icons.Rounded.SkipNext, null, tint = t.text, modifier = Modifier.size(38.dp)) }
                IconButton(onClick = vm::cycleRepeat) {
                    Icon(
                        if (vm.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, null,
                        tint = if (vm.repeatMode == Player.REPEAT_MODE_OFF) t.text.copy(alpha = 0.7f) else t.primary,
                    )
                }
            }

            // extras: speed + sleep
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                SpeedChip(vm)
                SleepChip(vm)
            }
        }
    }
}

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
            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                DropdownMenuItem(text = { Text("${s}x") }, onClick = { vm.changeSpeed(s); open = false })
            }
        }
    }
}

@Composable
private fun SleepChip(vm: PlayerViewModel) {
    val t = LocalSudo.current
    var open by remember { mutableStateOf(false) }
    val active = vm.sleepEndsAt > System.currentTimeMillis()
    Box {
        Row(
            Modifier.sudoPanel(RoundedCornerShape(50)).clickable { open = true }.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Bedtime, null, tint = if (active) t.primary else t.text, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (active) "Sleep on" else "Sleep timer", color = if (active) t.primary else t.text)
        }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem(text = { Text("Off") }, onClick = { vm.setSleep(0); open = false })
            listOf(5, 10, 15, 30, 45, 60, 90).forEach { m ->
                DropdownMenuItem(text = { Text("$m min") }, onClick = { vm.setSleep(m); open = false })
            }
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
