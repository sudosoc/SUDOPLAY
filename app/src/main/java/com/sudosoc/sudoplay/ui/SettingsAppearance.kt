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

// ---------------------------------------------------------------- appearance
@Composable
internal fun AppearancePage(vm: PlayerViewModel, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val s = vm.settings
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubHeader("Appearance", onBack)
        SectionLabel("Themes")
        NavRow(I.Apps, "App themes", "${Themes.byId(s.theme.value).name} • ${Themes.all.size} themes, ${Themes.all.count { it.live != null }} of them live") { onOpen("app_themes") }
        NavRow(I.PlayCircle, "Player themes", "${PlayerThemes.byId(s.playerTheme.value).name} • ${PlayerThemes.all.size} looks, live previews") { onOpen("player_themes") }
        NavRow(I.PlayCircle, "App logo & icon", "${Logos.names[s.appLogo.value.coerceIn(0, Logos.count)]} • ${Logos.count} logos • also changes the launcher icon") { onOpen("logos") }
        SectionLabel("Text")
        NavRow(I.TextFields, "Fonts & colors", "${Fonts.byKey(s.fontFamily.value).label} • ${Fonts.all.size} fonts • text, accent color and size") { onOpen("fonts") }
        SectionLabel("Live backgrounds")
        RowCard {
            val tt = LocalSudo.current
            Column(Modifier.weight(1f)) {
                Text("Animated backgrounds", color = tt.text, fontWeight = FontWeight.SemiBold)
                Text("Used by the Live themes and the live player themes. \"With music\" makes pulses and glows follow the bass. Choose Still to save battery.", color = tt.text.copy(alpha = 0.62f), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Still", 1 to "Moving", 2 to "With music").forEach { (v, label) ->
                        val on = s.liveMode.value == v
                        Text(
                            label, color = if (on) tt.onAccent else tt.text, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.clip(RoundedCornerShape(50))
                                .then(if (on) Modifier.background(Brush.horizontalGradient(listOf(tt.primary, tt.secondary))) else Modifier.border(1.dp, tt.text.copy(alpha = 0.25f), RoundedCornerShape(50)))
                                .clickable { s.liveMode.set(v) }.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
        SectionLabel("Player page")
        NavRow(I.Tune, "Seek bar style", "${SeekStyles.firstOrNull { it.first == s.seekStyle.value }?.second ?: "Classic"} • ${SeekStyles.size} shapes") { onOpen("seek_styles") }
        NavRow(I.Palette, "Icon style", "${runCatching { IconStyle.valueOf(s.iconStyle.value) }.getOrDefault(IconStyle.Rounded).label} • ${IconStyle.entries.size} families") { onOpen("icon_styles") }
        NavRow(I.Extension, "Player buttons", "Choose which buttons are shown") { onOpen("player_buttons") }
        SectionLabel("App")
        NavRow(I.Apps, "Navigation bar", "Pick the tabs and move it: ${s.navPos.value}") { onOpen("nav_bar") }
        SwitchRow("Colors from the cover", "The whole app takes its colors from the song that is playing", s.coverColors.value) { s.coverColors.set(it) }
        SectionLabel("Home-screen widget")
        val ctxW = LocalContext.current
        RowCard {
            Column(Modifier.weight(1f)) {
                Text("Widget theme", color = LocalSudo.current.text, fontWeight = FontWeight.SemiBold)
                Text("Add it from your launcher: three sizes (4×1, 4×2, 2×2) — all follow this theme", color = LocalSudo.current.text.copy(alpha = 0.62f), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.sudosoc.sudoplay.widget.WidgetTheme.ids.forEach { (id, label) ->
                        val on = s.widgetTheme.value == id
                        val tt = LocalSudo.current
                        Text(
                            label, color = if (on) tt.onAccent else tt.text, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.clip(RoundedCornerShape(50))
                                .then(if (on) Modifier.background(Brush.horizontalGradient(listOf(tt.primary, tt.secondary))) else Modifier.border(1.dp, tt.text.copy(alpha = 0.25f), RoundedCornerShape(50)))
                                .clickable { s.widgetTheme.set(id); com.sudosoc.sudoplay.widget.WidgetUpdater.update(ctxW) }.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun AppThemesPage(vm: PlayerViewModel, onBack: () -> Unit) {
    val t = LocalSudo.current
    Column(Modifier.fillMaxSize()) {
        SubHeader("App themes", onBack)
        LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Themes.groups.forEach { (style, list) ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "h_${style.name}") {
                    Text("${style.label} • ${list.size}", color = t.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
                }
                items(list, key = { it.id }) { th -> ThemeCard(th, vm.settings.theme.value == th.id) { vm.settings.theme.set(th.id) } }
            }
        }
    }
}

@Composable
internal fun ThemeCard(th: AppTheme, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier.fillMaxWidth().aspectRatio(0.85f).clip(shape)
            .background(Brush.verticalGradient(listOf(th.bgTop, th.bgBottom)))
            .border(if (selected) 3.dp else 1.dp, if (selected) th.primary else Color.White.copy(alpha = 0.15f), shape)
            .clickable(onClick = onClick),
    ) {
        th.live?.let { LiveLayer(it, listOf(th.bgTop, th.primary, th.secondary, th.text), animated = true) }
        if (th.style == Style.Glass || th.style == Style.Gradient || th.style == Style.Art) {
            Box(Modifier.size(90.dp).align(Alignment.TopStart).background(Brush.radialGradient(listOf(th.primary.copy(alpha = 0.45f), Color.Transparent))))
            Box(Modifier.size(90.dp).align(Alignment.BottomEnd).background(Brush.radialGradient(listOf(th.secondary.copy(alpha = 0.45f), Color.Transparent))))
        }
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            val panelShape = RoundedCornerShape(14.dp)
            val panel = when (th.style) {
                Style.Solid -> Modifier.background(th.surface, panelShape)
                Style.Glass, Style.Live -> Modifier.background(if (th.dark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.55f), panelShape)
                Style.Art -> Modifier.background(if (th.dark) Color.Black.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.6f), panelShape)
                Style.Gradient -> Modifier.background(Brush.linearGradient(listOf(th.primary.copy(alpha = 0.28f), th.secondary.copy(alpha = 0.22f))), panelShape)
                Style.Soft -> Modifier.background(th.surface, panelShape).border(1.dp, Color.White.copy(alpha = if (th.dark) 0.07f else 0.8f), panelShape)
                Style.Outline -> Modifier.border(1.5.dp, th.primary.copy(alpha = 0.7f), panelShape)
            }
            Row(Modifier.fillMaxWidth().then(panel).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(Brush.linearGradient(listOf(th.primary, th.secondary))))
                Spacer(Modifier.width(8.dp))
                Column {
                    Box(Modifier.width(44.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(th.text.copy(alpha = 0.8f)))
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.width(28.dp).height(4.dp).clip(RoundedCornerShape(3.dp)).background(th.text.copy(alpha = 0.4f)))
                }
            }
            Column {
                Text(th.name, color = th.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(th.style.label + (th.live?.let { " • ${it.label}" } ?: "") + if (th.dynamic) " • wallpaper colors" else "", color = th.text.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
            }
        }
        if (selected) Icon(I.CheckCircle, null, tint = th.primary, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
    }
}

@Composable
internal fun PlayerThemesPage(vm: PlayerViewModel, onBack: () -> Unit) {
    val app = LocalSudo.current
    val s = vm.settings
    val art by rememberPreviewArt(vm)
    val btn = playerButtons(s)
    val title = vm.current?.title ?: "Midnight Drive"
    val artist = vm.current?.artist ?: "SUDOSOC"
    Column(Modifier.fillMaxSize()) {
        SubHeader("Player themes", onBack)
        Text("Each card is the real player page in that theme. \"Classic\" follows your app theme.", color = app.text.copy(alpha = 0.62f),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
        LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(PlayerThemes.all, key = { it.id }) { pt ->
                val selected = s.playerTheme.value == pt.id
                val eff = effectiveTheme(app, pt)
                Column(Modifier.clickable { s.playerTheme.set(pt.id) }) {
                    Box {
                        PlayerMock(pt, art, btn, s.seekStyle.value, title, artist, Modifier.fillMaxWidth().border(if (selected) 3.dp else 1.dp, if (selected) eff.primary else app.text.copy(alpha = 0.15f), RoundedCornerShape(24.dp)))
                        if (selected) Icon(I.CheckCircle, null, tint = eff.primary, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(26.dp))
                    }
                    Text(pt.name, color = if (selected) app.primary else app.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- seek bar / icons / buttons
@Composable
internal fun SeekStylesPage(vm: PlayerViewModel, onBack: () -> Unit) {
    val t = LocalSudo.current
    val s = vm.settings
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubHeader("Seek bar style", onBack)
        SeekStyles.forEach { (id, name) ->
            val on = s.seekStyle.value == id
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().sudoPanel(RoundedCornerShape(20.dp))
                    .then(if (on) Modifier.border(2.dp, t.primary, RoundedCornerShape(20.dp)) else Modifier)
                    .clickable { s.seekStyle.set(id); vm.haptics.click() }.padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, Modifier.weight(1f), color = if (on) t.primary else t.text, fontWeight = FontWeight.SemiBold)
                    if (on) Icon(I.CheckCircle, null, tint = t.primary)
                }
                SeekBar(id, 0.42f, {}, {}, seed = 11L, interactive = false, animated = true)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun IconStylesPage(vm: PlayerViewModel, onBack: () -> Unit) {
    val t = LocalSudo.current
    val s = vm.settings
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubHeader("Icon style", onBack)
        Text("Applies to every icon in the app, including the player.", color = t.text.copy(alpha = 0.62f), style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
        IconStyle.entries.forEach { st ->
            val on = s.iconStyle.value == st.name
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().sudoPanel(RoundedCornerShape(20.dp))
                    .then(if (on) Modifier.border(2.dp, t.primary, RoundedCornerShape(20.dp)) else Modifier)
                    .clickable { s.iconStyle.set(st.name); vm.haptics.click() }.padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(st.label, Modifier.weight(1f), color = if (on) t.primary else t.text, fontWeight = FontWeight.SemiBold)
                    if (on) Icon(I.CheckCircle, null, tint = t.primary)
                }
                Spacer(Modifier.height(10.dp))
                CompositionLocalProvider(LocalIconStyle provides st) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf(I.PlayArrow, I.SkipNext, I.Shuffle, I.Repeat, I.FavoriteBorder, I.QueueMusic, I.Equalizer, I.Bedtime).forEach {
                            Icon(it, null, tint = t.text, modifier = Modifier.size(26.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun PlayerButtonsPage(vm: PlayerViewModel, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val s = vm.settings
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubHeader("Player buttons", onBack)
        NavRow(I.SwapVert, "Arrange buttons", "Drag the buttons into the order you like") { onOpen("arrange") }
        Text("Hide what you don't use. Play / pause always stays.", color = LocalSudo.current.text.copy(alpha = 0.62f), style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
        SectionLabel("Top bar")
        SwitchRow("Lyrics", "Opens synced lyrics over the cover", s.btnLyrics.value) { s.btnLyrics.set(it) }
        SwitchRow("Queue", "See and reorder what plays next", s.btnQueue.value) { s.btnQueue.set(it) }
        SectionLabel("Next to the title")
        SwitchRow("Favorite", "Heart a song so it shows in Favorites", s.btnFav.value) { s.btnFav.set(it) }
        SwitchRow("Add to playlist", "Quick add to any playlist", s.btnPlaylist.value) { s.btnPlaylist.set(it) }
        SectionLabel("Transport")
        SwitchRow("Shuffle", "Plays the queue in random order", s.btnShuffle.value) { s.btnShuffle.set(it) }
        SwitchRow("Previous", "Back to the last song (or restart this one)", s.btnPrev.value) { s.btnPrev.set(it) }
        SwitchRow("Next", "Skip to the next song", s.btnNext.value) { s.btnNext.set(it) }
        SwitchRow("Repeat", "Off, repeat all, or repeat one", s.btnRepeat.value) { s.btnRepeat.set(it) }
        SectionLabel("Under the seek bar")
        SwitchRow("A–B loop", "Mark a part of the song and repeat just that", s.btnAb.value) { s.btnAb.set(it) }
        SectionLabel("Bottom chips")
        SwitchRow("Speed", "Tap on/off, hold and slide to choose", s.btnSpeed.value) { s.btnSpeed.set(it) }
        SwitchRow("EQ", "Tap on/off, hold to open the studio", s.btnEq.value) { s.btnEq.set(it) }
        SwitchRow("Sleep timer", "Tap on/off, hold and slide to choose", s.btnSleep.value) { s.btnSleep.set(it) }
        Spacer(Modifier.height(24.dp))
    }
}

