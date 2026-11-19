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

// ============================================================================ background
@Composable
fun rememberPhase(animated: Boolean, ms: Int = 4000): State<Float> =
    if (animated) rememberInfiniteTransition(label = "phase").animateFloat(0f, 1f, infiniteRepeatable(tween(ms, easing = LinearEasing), RepeatMode.Restart), label = "p")
    else remember { mutableFloatStateOf(0.35f) }

internal fun hashF(i: Int, k: Int): Float {
    var x = i * 73856093 xor k * 19349663
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0xFFFF) / 65535f
}

/** Soft, cheap "blur": a tiny copy of the artwork stretched up with smoothing. Real blur is added on Android 12+. */
@Composable
internal fun SoftArt(art: Bitmap?, modifier: Modifier, alpha: Float, realBlur: Boolean) {
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

internal fun DrawScope.drawWaves(ph: Float, colors: List<Color>) {
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
internal fun rememberPalette(art: Bitmap?): Pair<Color, Color>? = remember(art) { art?.let(::computePalette) }

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
internal val HexShape = GenericShape { size, _ ->
    val w = size.width; val h = size.height
    moveTo(w * 0.25f, 0f); lineTo(w * 0.75f, 0f); lineTo(w, h / 2f); lineTo(w * 0.75f, h); lineTo(w * 0.25f, h); lineTo(0f, h / 2f); close()
}
internal val DiamondShape = GenericShape { size, _ ->
    moveTo(size.width / 2f, 0f); lineTo(size.width, size.height / 2f); lineTo(size.width / 2f, size.height); lineTo(0f, size.height / 2f); close()
}

@Composable
internal fun PlayerArt(pt: PlayerTheme, art: Bitmap?, side: Dp, playing: Boolean, progress: Float, animated: Boolean) {
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
internal fun FramedArt(pt: PlayerTheme, art: Bitmap?, w: Dp, h: Dp, shape: Shape) {
    val t = LocalSudo.current
    val sh = if (pt.glow) Modifier.shadow(28.dp, shape, ambientColor = t.primary, spotColor = t.primary) else Modifier.shadow(20.dp, shape)
    Box(Modifier.size(w, h)) {
        ArtImage(art, Modifier.fillMaxSize().then(sh), shape = shape)
        if (pt.glow) Box(Modifier.fillMaxSize().border(2.dp, t.primary.copy(alpha = 0.9f), shape))
    }
}

@Composable
internal fun CircleArt(pt: PlayerTheme, art: Bitmap?, side: Dp, playing: Boolean, progress: Float, animated: Boolean) {
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

