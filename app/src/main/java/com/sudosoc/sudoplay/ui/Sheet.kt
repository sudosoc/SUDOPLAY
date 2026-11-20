package com.sudosoc.sudoplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * The one "more" menu of the app: a bottom sheet in the theme's surface colour. It always opens fully
 * inside the screen (nothing clipped at an edge, nothing off-screen), follows RTL, and its rows are big
 * enough for a thumb. Used for songs, playlists, online results and anything else that used a popup.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SudoSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    subtitle: String? = null,
    header: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val t = LocalSudo.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { state.hide() }.invokeOnCompletion { onDismiss() } }
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = state,
        containerColor = t.surface, contentColor = t.text, tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        scrimColor = Color.Black.copy(alpha = 0.5f),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 6.dp).width(38.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(t.text.copy(alpha = 0.25f)))
        },
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 12.dp)) {
            if (title != null || header != null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    header?.invoke(this)
                    if (header != null) Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        if (title != null) Text(title, color = t.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (!subtitle.isNullOrBlank()) Text(subtitle, color = t.text.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), color = t.text.copy(alpha = 0.1f))
            }
            content(close)
        }
    }
}

/** One tappable row inside a [SudoSheet]. */
@Composable
fun SheetAction(icon: ImageVector, label: String, danger: Boolean = false, selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val t = LocalSudo.current
    val tint = when { danger -> MaterialTheme.colorScheme.error; selected -> t.primary; else -> t.text }
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp)
            .graphicsAlpha(if (enabled) 1f else 0.4f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (danger || selected) tint else t.text.copy(alpha = 0.75f), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(label, color = tint, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f), maxLines = 2)
        if (selected) Icon(I.Check, null, tint = t.primary, modifier = Modifier.size(20.dp))
    }
}

private fun Modifier.graphicsAlpha(a: Float) = if (a >= 1f) this else this.then(Modifier.graphicsLayer { alpha = a })

/** A settings row that shows its current value and opens a sheet to pick another. */
@Composable
internal fun ChoiceRow(title: String, sub: String?, options: List<Pair<Int, String>>, current: Int, onPick: (Int) -> Unit) {
    val t = LocalSudo.current
    var open by remember { mutableStateOf(false) }
    RowCard(onClick = { open = true }) {
        TitleSub(title, sub, Modifier.weight(1f))
        Text(options.firstOrNull { it.first == current }?.second ?: "", color = t.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
    }
    if (open) SudoSheet({ open = false }, title = title, subtitle = sub) { close ->
        options.forEach { (v, label) -> SudoMenuItem(label, selected = v == current) { onPick(v); close() } }
    }
}
