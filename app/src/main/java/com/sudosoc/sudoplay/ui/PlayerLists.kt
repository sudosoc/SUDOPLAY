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

// ============================================================================ lyrics / queue
@Composable
internal fun LyricsView(vm: PlayerViewModel) {
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
internal fun QueueList(vm: PlayerViewModel) {
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

