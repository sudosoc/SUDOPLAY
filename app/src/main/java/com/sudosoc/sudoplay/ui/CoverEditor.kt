package com.sudosoc.sudoplay.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sudosoc.sudoplay.data.CoverStore
import com.sudosoc.sudoplay.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

private enum class Step { Source, Gallery, Edit }

/** Long-press cover flow: choose a source (app gallery / device) → position it in the frame → save. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverFlow(song: Song, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(Step.Source) }
    var picked by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val bmp = withContext(Dispatchers.IO) { decodeUri(ctx, uri) }
                busy = false
                if (bmp != null) { picked = bmp; step = Step.Edit }
            }
        }
    }

    when (step) {
        Step.Source -> {
            val t = LocalSudo.current
            ModalBottomSheet(onDismissRequest = onDismiss, containerColor = t.surface, contentColor = t.text) {
                Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                    Text("Change cover", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(song.title, color = t.text.copy(alpha = 0.6f), maxLines = 1, modifier = Modifier.padding(bottom = 12.dp))
                    SourceRow(Icons.Rounded.Collections, "Choose from app", "Ready-made covers inside SUDOPLAY") { step = Step.Gallery }
                    SourceRow(Icons.Rounded.PhotoLibrary, "Choose from device", "Pick any photo from your phone") {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    if (CoverStore.hasCustom(ctx, song)) {
                        SourceRow(Icons.Rounded.RestartAlt, "Use original cover", "Remove the custom cover") {
                            CoverStore.reset(ctx, song); onDismiss()
                        }
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
                }
            }
        }
        Step.Gallery -> GalleryDialog(
            onBack = { step = Step.Source },
            onPick = { bmp -> picked = bmp; step = Step.Edit },
        )
        Step.Edit -> picked?.let { bmp ->
            CropEditor(
                bitmap = bmp,
                onCancel = { step = Step.Source },
                onSave = { out, wholeAlbum -> CoverStore.save(ctx, song, out, wholeAlbum); onDismiss() },
            )
        }
    }
}

@Composable
private fun SourceRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, onClick: () -> Unit) {
    val t = LocalSudo.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = t.primary, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(sub, color = t.text.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ------------------------------------------------------------ gallery of built-in covers
@Composable
private fun GalleryDialog(onBack: () -> Unit, onPick: (Bitmap) -> Unit) {
    val t = LocalSudo.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val names = remember { (ctx.assets.list("covers") ?: emptyArray()).sorted() }
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(t.bgTop).systemBarsPadding()) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.Rounded.Close, null, tint = t.text) }
                Text("Covers in SUDOPLAY", color = t.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(names) { _, n ->
                    val thumb by produceState<Bitmap?>(null, n) { value = withContext(Dispatchers.IO) { loadAsset(ctx, n, 256) } }
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(t.surface)
                            .clickable { scope.launch { withContext(Dispatchers.IO) { loadAsset(ctx, n, 1600) }?.let(onPick) } },
                    ) {
                        thumb?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------ crop / position editor
@Composable
private fun CropEditor(bitmap: Bitmap, onCancel: () -> Unit, onSave: (Bitmap, Boolean) -> Unit) {
    val t = LocalSudo.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var wholeAlbum by remember { mutableStateOf(false) }
    var framePx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    // image as displayed at scale 1 (center-crop fill of the square frame)
    fun clamp(o: Offset, sc: Float): Offset {
        if (framePx <= 0f) return o
        val fit = framePx / min(bitmap.width, bitmap.height)
        val maxX = max(0f, (bitmap.width * fit * sc - framePx) / 2f)
        val maxY = max(0f, (bitmap.height * fit * sc - framePx) / 2f)
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(t.bgTop).systemBarsPadding().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onCancel) { Text("Back", color = t.text) }
                Text("Position cover", Modifier.weight(1f), color = t.text, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                TextButton(onClick = { onSave(renderCrop(bitmap, scale, offset, framePx), wholeAlbum) }) { Text("Save", color = t.primary, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(8.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val side = minOf(maxWidth, maxHeight)
                framePx = with(density) { side.toPx() }
                Box(
                    Modifier.size(side).clip(RoundedCornerShape(28.dp)).background(t.surface)
                        .pointerInput(bitmap) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val ns = (scale * zoom).coerceIn(1f, 5f)
                                scale = ns
                                offset = clamp(offset + pan, ns)
                            }
                        },
                ) {
                    Image(
                        bitmap.asImageBitmap(), null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
                        },
                    )
                }
            }
            Text("Drag to move • pinch to zoom", color = t.text.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
            Slider(
                value = scale, onValueChange = { scale = it; offset = clamp(offset, it) }, valueRange = 1f..5f,
                colors = SliderDefaults.colors(thumbColor = t.primary, activeTrackColor = t.primary, inactiveTrackColor = t.text.copy(alpha = 0.2f)),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Apply to the whole album", color = t.text, fontWeight = FontWeight.SemiBold)
                    Text("Every song of this album gets this cover", color = t.text.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                }
                Switch(wholeAlbum, { wholeAlbum = it })
            }
            TextButton(onClick = { scale = 1f; offset = Offset.Zero }) { Text("Reset position", color = t.primary) }
            Spacer(Modifier.height(10.dp))
        }
    }
}

/** Renders what the user sees in the frame into a 640×640 bitmap. */
private fun renderCrop(src: Bitmap, scale: Float, offset: Offset, framePx: Float): Bitmap {
    val out = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
    if (framePx <= 0f) return out
    val s0 = 640f / min(src.width, src.height)
    val ratio = 640f / framePx
    val m = Matrix().apply {
        postTranslate(-src.width / 2f, -src.height / 2f)
        postScale(s0 * scale, s0 * scale)
        postTranslate(320f + offset.x * ratio, 320f + offset.y * ratio)
    }
    Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
    return out
}

// ------------------------------------------------------------ decoding helpers
private fun loadAsset(ctx: Context, name: String, maxSide: Int): Bitmap? = runCatching {
    ctx.assets.open("covers/$name").use { ins ->
        val bytes = ins.readBytes()
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var sample = 1
        while (max(o.outWidth, o.outHeight) / (sample * 2) >= maxSide) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}.getOrNull()

private fun decodeUri(ctx: Context, uri: Uri): Bitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val big = max(info.size.width, info.size.height)
            if (big > 2048) decoder.setTargetSampleSize(Integer.highestOneBit(big / 2048 + 1))
        }
    } else {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var sample = 1
        while (max(o.outWidth, o.outHeight) / (sample * 2) >= 2048) sample *= 2
        ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
}.getOrNull()
