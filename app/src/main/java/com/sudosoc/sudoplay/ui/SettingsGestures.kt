package com.sudosoc.sudoplay.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.luminance
import com.sudosoc.sudoplay.PlayerViewModel
import com.sudosoc.sudoplay.data.CoverStore

// ---------------------------------------------------------------- gesture editor
@Composable
internal fun ActionPicker(title: String, sub: String?, current: String, onPick: (String) -> Unit) {
    val t = LocalSudo.current
    var open by remember { mutableStateOf(false) }
    RowCard(onClick = { open = true }) {
        TitleSub(title, sub, Modifier.weight(1f))
        Text(GestureActions.label(current), color = t.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
    }
    if (open) SudoSheet({ open = false }, title = title, subtitle = sub) { close ->
        GestureActions.all.forEach { (id, label) -> SudoMenuItem(label, selected = id == current) { onPick(id); close() } }
    }
}

@Composable
internal fun GestureEditorPage(vm: PlayerViewModel, onBack: () -> Unit) {
    val s = vm.settings
    val t = LocalSudo.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubHeader("Gesture editor", onBack)
        Text("Pick the action for each gesture on the player page. Buttons and lists keep working as usual.", color = t.text.copy(alpha = 0.62f),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
        SectionLabel("Double-tap")
        SwitchRow("Double-tap gestures", null, s.doubleTap.value) { s.doubleTap.set(it) }
        ActionPicker("Left third", "Double-tap on the left side", s.gDtL.value) { s.gDtL.set(it) }
        ActionPicker("Middle", "Double-tap in the center", s.gDtC.value) { s.gDtC.set(it) }
        ActionPicker("Right third", "Double-tap on the right side", s.gDtR.value) { s.gDtR.set(it) }
        SectionLabel("Swipes")
        SwitchRow("Swipe gestures", null, s.swipe.value) { s.swipe.set(it) }
        ActionPicker("Swipe right", null, s.gSwR.value) { s.gSwR.set(it) }
        ActionPicker("Swipe left", null, s.gSwL.value) { s.gSwL.set(it) }
        ActionPicker("Swipe up", "On the cover area; lists keep scrolling", s.gSwU.value) { s.gSwU.set(it) }
        SectionLabel("Touch and hold")
        SwitchRow("Hold and drag to seek", null, s.scrub.value) { s.scrub.set(it) }
        ActionPicker("Hold, then let go", "Without dragging", s.gLp.value) { s.gLp.set(it) }
        ActionPicker("Two-finger tap", "Tap with two fingers at once", s.gTwo.value) { s.gTwo.set(it) }
        RowCard(onClick = {
            s.gDtL.set("playpause"); s.gDtC.set("playpause"); s.gDtR.set("playpause")
            s.gSwR.set("next"); s.gSwL.set("prev"); s.gSwU.set("none"); s.gLp.set("cover"); s.gTwo.set("none")
        }) {
            Icon(I.RestartAlt, null, tint = t.primary)
            Spacer(Modifier.width(12.dp))
            TitleSub("Back to defaults", null, Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------- arrange buttons
@Composable
internal fun ReorderRow(order: List<String>, shown: (String) -> Boolean, onChange: (List<String>) -> Unit) {
    val t = LocalSudo.current
    val cur by rememberUpdatedState(order)
    val cb by rememberUpdatedState(onChange)
    var rowW by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var off by remember { mutableFloatStateOf(0f) }
    Row(Modifier.padding(horizontal = 14.dp, vertical = 4.dp).fillMaxWidth().onSizeChanged { rowW = it.width.toFloat() }) {
        order.forEach { id ->
            key(id) {
                val on = shown(id)
                val isDrag = dragging == id
                Box(
                    Modifier.weight(1f).padding(3.dp)
                        .graphicsLayer { translationX = if (isDrag) off else 0f; scaleX = if (isDrag) 1.08f else 1f; scaleY = if (isDrag) 1.08f else 1f; alpha = if (on) 1f else 0.45f }
                        .sudoPanel(RoundedCornerShape(16.dp))
                        .then(if (isDrag) Modifier.border(2.dp, t.primary, RoundedCornerShape(16.dp)) else Modifier)
                        .pointerInput(id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragging = id; off = 0f },
                                onDrag = { ch, d ->
                                    ch.consume()
                                    off += d.x
                                    val step = rowW / cur.size
                                    val i = cur.indexOf(id)
                                    val target = (i + Math.round(off / step)).coerceIn(0, cur.lastIndex)
                                    if (target != i) {
                                        val l = cur.toMutableList(); l.removeAt(i); l.add(target, id)
                                        off -= (target - i) * step
                                        cb(l)
                                    }
                                },
                                onDragEnd = { dragging = null; off = 0f },
                                onDragCancel = { dragging = null; off = 0f },
                            )
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(ButtonOrder.names[id] ?: id, color = if (isDrag) t.primary else t.text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
    }
}

@Composable
internal fun ArrangePage(vm: PlayerViewModel, onBack: () -> Unit) {
    val s = vm.settings
    val t = LocalSudo.current
    val art by rememberPreviewArt(vm)
    val btn = playerButtons(s)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubHeader("Arrange buttons", onBack)
        Text("Press and hold a button, then drag it left or right. Dimmed ones are hidden in \"Player buttons\".", color = t.text.copy(alpha = 0.62f),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
        Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            PlayerMock(PlayerThemes.byId(s.playerTheme.value), art, btn, s.seekStyle.value, vm.current?.title ?: "Midnight Drive", vm.current?.artist ?: "SUDOSOC", Modifier.width(190.dp))
        }
        SectionLabel("Top bar")
        ReorderRow(btn.top, { if (it == "lyrics") btn.lyrics else btn.queue }) { s.orderTop.set(it.joinToString(",")) }
        SectionLabel("Next to the title")
        ReorderRow(btn.title, { if (it == "fav") btn.fav else btn.playlist }) { s.orderTitle.set(it.joinToString(",")) }
        SectionLabel("Transport")
        ReorderRow(btn.transport, { when (it) { "shuffle" -> btn.shuffle; "prev" -> btn.prev; "next" -> btn.next; "repeat" -> btn.repeat; else -> true } }) { s.orderTransport.set(it.joinToString(",")) }
        SectionLabel("Bottom chips")
        ReorderRow(btn.chips, { when (it) { "speed" -> btn.speed; "eq" -> btn.eq; else -> btn.sleep } }) { s.orderChips.set(it.joinToString(",")) }
        RowCard(onClick = {
            s.orderTop.set("lyrics,queue"); s.orderTitle.set("fav,add"); s.orderTransport.set("shuffle,prev,play,next,repeat"); s.orderChips.set("speed,eq,sleep")
        }) {
            Icon(I.RestartAlt, null, tint = t.primary)
            Spacer(Modifier.width(12.dp))
            TitleSub("Back to the default order", null, Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
    }
}


