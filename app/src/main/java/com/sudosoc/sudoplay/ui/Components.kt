package com.sudosoc.sudoplay.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sudosoc.sudoplay.PlayerViewModel
import com.sudosoc.sudoplay.data.CoverStore
import com.sudosoc.sudoplay.data.Song
import com.sudosoc.sudoplay.playback.ArtLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun rememberArt(song: Song?, size: Int): State<Bitmap?> {
    val ctx = LocalContext.current
    val ver = CoverStore.version
    return produceState<Bitmap?>(null, song?.id, size, ver) {
        value = if (song == null) null else withContext(Dispatchers.IO) { ArtLoader.load(ctx, song.uri, size) }
    }
}

@Composable
fun Artwork(song: Song?, modifier: Modifier = Modifier, size: Int = 256, corner: Dp = 12.dp, shape: Shape? = null) {
    val t = LocalSudo.current
    val bmp by rememberArt(song, size)
    Box(
        modifier
            .clip(shape ?: RoundedCornerShape(corner))
            .background(Brush.linearGradient(listOf(t.primary.copy(alpha = 0.55f), t.secondary.copy(alpha = 0.55f)))),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.fillMaxSize(0.45f))
    }
}

/** Surface look depends on the active theme style: solid fill, frosted glass, or dark scrim over artwork. */
@Composable
fun Modifier.sudoPanel(shape: Shape = RoundedCornerShape(20.dp)): Modifier {
    val t = LocalSudo.current
    return when (t.style) {
        Style.Solid -> this.clip(shape).background(t.surface)
        Style.Glass -> this.clip(shape)
            .background(if (t.dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.50f))
            .border(1.dp, (if (t.dark) Color.White else Color.White).copy(alpha = if (t.dark) 0.18f else 0.7f), shape)
        Style.Art -> this.clip(shape)
            .background(if (t.dark) Color.Black.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.55f))
            .border(1.dp, t.text.copy(alpha = 0.10f), shape)
        Style.Gradient -> this.clip(shape)
            .background(Brush.linearGradient(listOf(t.primary.copy(alpha = 0.24f), t.secondary.copy(alpha = 0.18f))))
            .border(1.dp, t.text.copy(alpha = 0.10f), shape)
        Style.Soft -> this.shadow(8.dp, shape, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.5f))
            .background(t.surface, shape)
            .border(1.dp, Color.White.copy(alpha = if (t.dark) 0.06f else 0.7f), shape)
        Style.Outline -> this.clip(shape)
            .border(1.5.dp, t.primary.copy(alpha = 0.6f), shape)
    }
}

@Composable
fun SudoBackground(artSong: Song?, content: @Composable BoxScope.() -> Unit) {
    val t = LocalSudo.current
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(t.bgTop, t.bgBottom)))) {
        when (t.style) {
            Style.Glass, Style.Gradient -> {
                Box(Modifier.fillMaxSize().drawBehind {
                    val r = size.width * 0.9f
                    drawCircle(
                        Brush.radialGradient(listOf(t.primary.copy(alpha = 0.35f), Color.Transparent),
                            center = Offset(size.width * 0.1f, size.height * 0.12f), radius = r),
                        radius = r, center = Offset(size.width * 0.1f, size.height * 0.12f),
                    )
                    drawCircle(
                        Brush.radialGradient(listOf(t.secondary.copy(alpha = 0.32f), Color.Transparent),
                            center = Offset(size.width * 0.95f, size.height * 0.75f), radius = r),
                        radius = r, center = Offset(size.width * 0.95f, size.height * 0.75f),
                    )
                })
            }
            Style.Art -> {
                val bmp by rememberArt(artSong, 256)
                bmp?.let {
                    Image(
                        it.asImageBitmap(), null,
                        Modifier.fillMaxSize().blur(48.dp).alpha(if (t.dark) 0.75f else 0.55f),
                        contentScale = ContentScale.Crop,
                    )
                }
                Box(Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(t.bgTop.copy(alpha = 0.35f), t.bgBottom.copy(alpha = 0.85f)))
                ))
            }
            Style.Solid, Style.Soft, Style.Outline -> {}
        }
        content()
    }
}

@Composable
fun SongRow(song: Song, vm: PlayerViewModel, onClick: () -> Unit, extraMenu: (@Composable (dismiss: () -> Unit) -> Unit)? = null) {
    val t = LocalSudo.current
    val isCur = vm.current?.id == song.id
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(song, Modifier.size(50.dp), 128, 12.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title, color = if (isCur) t.primary else t.text,
                fontWeight = if (isCur) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${song.artist}  •  ${fmt(song.durationMs)}", color = t.text.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (isCur && vm.isPlaying) Icon(Icons.Rounded.Equalizer, null, tint = t.primary, modifier = Modifier.padding(end = 4.dp))
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, null, tint = t.text.copy(alpha = 0.7f)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Play next") }, onClick = { vm.playNext(song); menu = false })
                DropdownMenuItem(text = { Text("Add to queue") }, onClick = { vm.addToQueue(song); menu = false })
                DropdownMenuItem(
                    text = { Text(if (vm.store.isFav(song.id)) "Remove from favorites" else "Add to favorites") },
                    onClick = { vm.store.toggleFav(song.id); menu = false },
                )
                DropdownMenuItem(text = { Text("Add to playlist…") }, onClick = { vm.playlistTarget = song; menu = false })
                extraMenu?.invoke { menu = false }
            }
        }
    }
}

@Composable
fun MiniPlayer(vm: PlayerViewModel, onOpen: () -> Unit) {
    val t = LocalSudo.current
    val song = vm.current ?: return
    Column(
        Modifier.padding(horizontal = 10.dp, vertical = 6.dp).fillMaxWidth()
            .sudoPanel(RoundedCornerShape(22.dp)).clickable(onClick = onOpen)
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(song, Modifier.size(44.dp), 128, 12.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, color = t.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = t.text.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = vm::togglePlay) {
                Icon(if (vm.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = t.text, modifier = Modifier.size(30.dp))
            }
            IconButton(onClick = vm::next) { Icon(Icons.Rounded.SkipNext, null, tint = t.text) }
        }
        val p = if (vm.duration > 0) (vm.position.toFloat() / vm.duration).coerceIn(0f, 1f) else 0f
        LinearProgressIndicator(
            progress = { p }, modifier = Modifier.fillMaxWidth().height(2.dp),
            color = t.primary, trackColor = Color.Transparent,
        )
    }
}

@Composable
fun CircleIcon(onClick: () -> Unit, size: Dp = 64.dp, content: @Composable () -> Unit) {
    val t = LocalSudo.current
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(Brush.linearGradient(listOf(t.primary, t.secondary)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
