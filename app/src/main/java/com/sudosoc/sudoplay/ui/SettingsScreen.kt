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

const val APP_VERSION = "0.14.1"

@Composable
fun SettingsTab(vm: PlayerViewModel) {
    var page by rememberSaveable { mutableStateOf("home") }

    BackHandler(enabled = page != "home") {
        page = when (page) {
            "app_themes", "player_themes", "seek_styles", "icon_styles", "player_buttons", "nav_bar", "fonts", "logos" -> "appearance"
            "arrange" -> "player_buttons"
            "stats", "dupes" -> "extras"
            "gesture_editor" -> "props"
            "hidden_folders" -> "extras"
            "netlog" -> "online"
            else -> "home"
        }
    }

    androidx.compose.animation.Crossfade(page, animationSpec = androidx.compose.animation.core.tween(150), label = "settings") { pg ->
    when (pg) {
        "appearance" -> AppearancePage(vm, onOpen = { page = it }, onBack = { page = "home" })
        "app_themes" -> AppThemesPage(vm) { page = "appearance" }
        "player_themes" -> PlayerThemesPage(vm) { page = "appearance" }
        "seek_styles" -> SeekStylesPage(vm) { page = "appearance" }
        "icon_styles" -> IconStylesPage(vm) { page = "appearance" }
        "player_buttons" -> PlayerButtonsPage(vm, onOpen = { page = it }) { page = "appearance" }
        "arrange" -> ArrangePage(vm) { page = "player_buttons" }
        "gesture_editor" -> GestureEditorPage(vm) { page = "props" }
        "nav_bar" -> NavBarPage(vm) { page = "appearance" }
        "fonts" -> FontsPage(vm) { page = "appearance" }
        "logos" -> LogosPage(vm) { page = "appearance" }
        "about" -> AboutPage(vm) { page = "home" }
        "stats" -> StatsPage(vm) { page = "extras" }
        "dupes" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { SubHeader("Duplicate finder", { page = "extras" }); DuplicatesBody(vm) }
        "extras" -> ExtrasPage(vm, onOpen = { page = it }) { page = "home" }
        "hidden_folders" -> HiddenFoldersPage(vm) { page = "extras" }
        "netlog" -> NetLogPage { page = "online" }
        "props" -> PropertiesPage(vm, onOpen = { page = it }) { page = "home" }
        "sound" -> SoundPage(vm) { page = "home" }
        "car" -> CarPage(vm) { page = "home" }
        "online" -> OnlineSettingsPage(vm, onOpen = { page = it }) { page = "home" }
        "backup" -> BackupPage(vm) { page = "home" }
        else -> HomePage(vm) { page = it }
    }
    }
}

// ---------------------------------------------------------------- shared bits
@Composable
private fun SubHeader(title: String, onBack: () -> Unit) {
    val t = LocalSudo.current
    Row(Modifier.padding(top = 6.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack) { Icon(I.ArrowBack, null, tint = t.text) }
        Text(title, color = t.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionLabel(text: String) {
    val t = LocalSudo.current
    Text(text.uppercase(), color = t.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp))
}

@Composable
private fun RowCard(onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val m = Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().sudoPanel(shape)
    Row(
        (if (onClick != null) m.clickable(onClick = onClick) else m).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, content = content,
    )
}

/** A short grey line under a row, explaining what the option above it does. */
@Composable
private fun HintLine(text: String) {
    Text(text, color = LocalSudo.current.text.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 22.dp, vertical = 2.dp))
}

@Composable
private fun TitleSub(title: String, sub: String?, modifier: Modifier = Modifier) {
    val t = LocalSudo.current
    Column(modifier) {
        Text(title, color = t.text, fontWeight = FontWeight.SemiBold)
        if (sub != null) Text(sub, color = t.text.copy(alpha = 0.62f), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    RowCard(onClick = { onChange(!checked) }) {
        TitleSub(title, sub, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
private fun SliderRow(title: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onDone: (() -> Unit)? = null, onChange: (Float) -> Unit) {
    val t = LocalSudo.current
    RowCard {
        Column(Modifier.weight(1f)) {
            Row {
                Text(title, color = t.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(valueText, color = t.primary, fontWeight = FontWeight.Bold)
            }
            Slider(value, onChange, valueRange = range, steps = steps, onValueChangeFinished = onDone,
                colors = SliderDefaults.colors(thumbColor = t.primary, activeTrackColor = t.primary, inactiveTrackColor = t.text.copy(alpha = 0.2f)))
        }
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, sub: String?, onClick: () -> Unit) {
    val t = LocalSudo.current
    RowCard(onClick = onClick) {
        Icon(icon, null, tint = t.primary)
        Spacer(Modifier.width(14.dp))
        TitleSub(title, sub, Modifier.weight(1f))
        Icon(I.ChevronRight, null, tint = t.text.copy(alpha = 0.5f))
    }
}

// ---------------------------------------------------------------- home
@Composable
private fun HomePage(vm: PlayerViewModel, onOpen: (String) -> Unit) {
    val t = LocalSudo.current
    val st = vm.settings
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            LogoImage(st.appLogo.value, 48.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("SUDOPLAY", color = t.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, letterSpacing = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Sp))
                Text("Settings • v$APP_VERSION", color = t.text.copy(alpha = 0.55f), style = MaterialTheme.typography.labelMedium)
            }
        }
        SectionLabel("Look & feel")
        BigSection(I.Palette, "Appearance", "المظهر", "${Themes.byId(st.theme.value).name} • ${PlayerThemes.byId(st.playerTheme.value).name} • live backgrounds, icons, seek bar, tabs, widget") { onOpen("appearance") }
        SectionLabel("Listening")
        BigSection(I.Equalizer, "Sound & playback", "الصوت", "Sound studio, crossfade, ReplayGain, skip silence, lecture mode") { onOpen("sound") }
        BigSection(I.Tune, "Gestures & feedback", "اللمس", "What every tap and swipe does, seek sensitivity, vibration") { onOpen("props") }
        BigSection(I.Explore, "Car & launch", "السيارة", "Car mode, autoplay on open, keep screen on") { onOpen("car") }
        SectionLabel("Your stuff")
        BigSection(I.LibraryMusic, "Library & files", "المكتبة", "Hidden folders, short tracks, lyrics, covers, duplicates, listening stats") { onOpen("extras") }
        BigSection(I.Search, "Online & downloads", "الأونلاين", "Downloads, Discovery, search suggestions, network log, what leaves your phone") { onOpen("online") }
        BigSection(I.Collections, "Backup & restore", "النسخ الاحتياطي", "Back up and restore everything in one file") { onOpen("backup") }
        SectionLabel("About")
        BigSection(I.PlayCircle, "About SUDOPLAY", "عن التطبيق", "What it does, how it treats your data, and who made it") { onOpen("about") }
        Text("SUDOPLAY $APP_VERSION • by SUDOSOC", color = t.text.copy(alpha = 0.4f), style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(20.dp))
    }
}

@Composable
private fun BigSection(icon: ImageVector, title: String, arabic: String, desc: String, onClick: () -> Unit) {
    val t = LocalSudo.current
    RowCard(onClick = onClick) {
        Box(Modifier.size(50.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(t.primary, t.secondary))), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = t.onAccent)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = t.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Text(arabic, color = t.primary, style = MaterialTheme.typography.bodyMedium)
            }
            Text(desc, color = t.text.copy(alpha = 0.62f), style = MaterialTheme.typography.bodySmall)
        }
        Icon(I.ChevronRight, null, tint = t.text.copy(alpha = 0.5f))
    }
}

// ---------------------------------------------------------------- appearance
@Composable
private fun AppearancePage(vm: PlayerViewModel, onOpen: (String) -> Unit, onBack: () -> Unit) {
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
private fun AppThemesPage(vm: PlayerViewModel, onBack: () -> Unit) {
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
private fun ThemeCard(th: AppTheme, selected: Boolean, onClick: () -> Unit) {
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
private fun PlayerThemesPage(vm: PlayerViewModel, onBack: () -> Unit) {
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
private fun SeekStylesPage(vm: PlayerViewModel, onBack: () -> Unit) {
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
private fun IconStylesPage(vm: PlayerViewModel, onBack: () -> Unit) {
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
private fun PlayerButtonsPage(vm: PlayerViewModel, onOpen: (String) -> Unit, onBack: () -> Unit) {
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

// ---------------------------------------------------------------- extras
@Composable
private fun ExtrasPage(vm: PlayerViewModel, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val t = LocalSudo.current
    val s = vm.settings
    val ctx = LocalContext.current
    var covers by remember { mutableIntStateOf(CoverStore.count(ctx)) }
    var minDur by remember { mutableFloatStateOf(s.minDurationSec.value.toFloat()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubHeader("Library & files", onBack)

        SectionLabel("Library")
        SliderRow(
            "Ignore short tracks", if (minDur.toInt() == 0) "Show everything" else "Shorter than ${minDur.toInt()}s", minDur, 0f..120f, steps = 23,
            onDone = { s.minDurationSec.set(minDur.toInt()); vm.refreshLibrary() },
        ) { minDur = (it / 5).toInt() * 5f }
        NavRow(I.Folder, "Hidden folders", "${vm.hiddenFolders().size} hidden • WhatsApp audio, recordings…") { onOpen("hidden_folders") }
        RowCard(onClick = { vm.refreshLibrary() }) {
            Icon(I.RestartAlt, null, tint = t.primary)
            Spacer(Modifier.width(12.dp))
            TitleSub("Rescan library", "${vm.rawSongs.size} tracks found • new files are picked up automatically", Modifier.weight(1f))
        }

        SectionLabel("Plays & history")
        NavRow(I.Whatshot, "Your stats", "Listening time, top tracks, artists and albums") { onOpen("stats") }
        SwitchRow("Count plays", "Feeds Recently played / Most played / Never played / Mix", s.countPlays.value) { s.countPlays.set(it) }
        RowCard(onClick = { vm.clearHistory() }) {
            Icon(I.DeleteSweep, null, tint = t.primary)
            Spacer(Modifier.width(12.dp))
            TitleSub("Clear play history", "Resets play counts and the recent list", Modifier.weight(1f))
        }
        NavRow(I.Search, "Duplicate finder", "Finds the same song stored twice") { onOpen("dupes") }
        RowCard {
            Column(Modifier.weight(1f)) {
                Text("When you listen", color = t.text, fontWeight = FontWeight.SemiBold)
                val mx = (vm.hourCounts.maxOrNull() ?: 0).coerceAtLeast(1)
                Row(Modifier.fillMaxWidth().height(48.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                    vm.hourCounts.forEach { c -> Box(Modifier.weight(1f).fillMaxHeight((0.08f + 0.92f * c / mx)).background(t.primary.copy(alpha = if (c == 0) 0.2f else 0.9f), RoundedCornerShape(2.dp))) }
                }
                Text("0h                      12h                    23h", color = t.text.copy(alpha = 0.45f), fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp))
            }
        }

        SectionLabel("Lyrics")
        val canLrc = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()
        NavRow(I.Lyrics, "Lyrics from .lrc files", if (canLrc) "Enabled" else "Tap to allow all-files access") {
            if (!canLrc) ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            )
        }

        HintLine("Downloading missing lyrics and the network log are under Online & privacy.")

        SectionLabel("Covers")
        RowCard {
            TitleSub("Custom covers", "$covers saved • long-press the cover in the player to change one", Modifier.weight(1f))
        }
        RowCard(onClick = { CoverStore.clearAll(ctx); covers = 0 }) {
            Icon(I.Delete, null, tint = t.primary)
            Spacer(Modifier.width(12.dp))
            TitleSub("Reset all custom covers", null, Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HiddenFoldersPage(vm: PlayerViewModel, onBack: () -> Unit) {
    val t = LocalSudo.current
    val hidden = vm.hiddenFolders()
    val folders = remember(vm.rawSongs) {
        vm.rawSongs.groupBy { it.folder }.map { (f, l) -> f to l.size }.sortedBy { it.first.lowercase() }
    }
    Column(Modifier.fillMaxSize()) {
        SubHeader("Hidden folders", onBack)
        Text("Songs in a folder you switch off disappear from the library.", color = t.text.copy(alpha = 0.62f), style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(folders.size, key = { folders[it].first }) { i ->
                val (f, n) = folders[i]
                val visible = f !in hidden
                RowCard(onClick = { vm.setFolderHidden(f, visible) }) {
                    TitleSub(f.trimEnd('/').substringAfterLast('/').ifEmpty { "(root)" }, "$n tracks • ${f.trimEnd('/')}", Modifier.weight(1f))
                    Switch(visible, { vm.setFolderHidden(f, !it) })
                }
            }
        }
    }
}

// ---------------------------------------------------------------- properties
@Composable
private fun PropertiesPage(vm: PlayerViewModel, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val s = vm.settings
    val t = LocalSudo.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubHeader("Properties", onBack)
        NavRow(I.Tune, "Gesture editor", "Choose what every gesture does") { onOpen("gesture_editor") }
        SectionLabel("Player gestures")
        SwitchRow("Double-tap to play / pause", "Double-tap anywhere on the player page", s.doubleTap.value) { s.doubleTap.set(it) }
        SwitchRow("Swipe gestures", "Horizontal swipes on the player page", s.swipe.value) { s.swipe.set(it) }
        SwitchRow("Long-press and drag to seek", "Hold, then drag left or right", s.scrub.value) { s.scrub.set(it) }
        SliderRow(
            "Seek sensitivity", "${s.scrubSeconds.value}s per full swipe", s.scrubSeconds.value.toFloat(), 30f..300f, steps = 8,
        ) { s.scrubSeconds.set((it / 15).toInt() * 15) }

        SectionLabel("Feedback")
        SwitchRow("Gesture icon", "The badge shown on double-tap and swipes. Off keeps only the rubber stretch", s.gestureIcon.value) { s.gestureIcon.set(it) }

        SectionLabel("Vibration")
        RowCard {
            Column(Modifier.weight(1f)) {
                Text("Haptic strength", color = t.text, fontWeight = FontWeight.SemiBold)
                Text(
                    if (vm.haptics.available) "Real vibration for taps, gestures and sliders" else "No vibration motor found on this device",
                    color = t.text.copy(alpha = 0.62f), style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Off", "Light", "Medium", "Strong").forEachIndexed { i, label ->
                        val on = s.hapticLevel.value == i
                        Text(
                            label, color = if (on) t.onAccent else t.text, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.clip(RoundedCornerShape(50))
                                .then(if (on) Modifier.background(Brush.horizontalGradient(listOf(t.primary, t.secondary))) else Modifier.border(1.dp, t.text.copy(alpha = 0.25f), RoundedCornerShape(50)))
                                .clickable { s.hapticLevel.set(i); if (i > 0) vm.haptics.test(i) }.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
        RowCard(onClick = { vm.haptics.test(s.hapticLevel.value.coerceAtLeast(1)) }) {
            Icon(I.Whatshot, null, tint = t.primary)
            Spacer(Modifier.width(12.dp))
            TitleSub("Test vibration", "Plays one tap at the selected strength", Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------- navigation bar
@Composable
private fun NavBarPage(vm: PlayerViewModel, onBack: () -> Unit) {
    val t = LocalSudo.current
    val s = vm.settings
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubHeader("Navigation bar", onBack)
        SectionLabel("Position")
        RowCard {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("bottom" to "Bottom", "top" to "Top", "left" to "Left", "right" to "Right").forEach { (id, label) ->
                    val on = s.navPos.value == id
                    Text(
                        label, color = if (on) t.onAccent else t.text, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.clip(RoundedCornerShape(50))
                            .then(if (on) Modifier.background(Brush.horizontalGradient(listOf(t.primary, t.secondary))) else Modifier.border(1.dp, t.text.copy(alpha = 0.25f), RoundedCornerShape(50)))
                            .clickable { s.navPos.set(id); vm.haptics.click() }.padding(horizontal = 16.dp, vertical = 9.dp),
                    )
                }
            }
        }
        SectionLabel("Tabs")
        SwitchRow("Home", "Greeting, continue listening, your mixes — sections you can reorder", s.navHome.value) { s.navHome.set(it) }
        SwitchRow("Songs", "Your whole library, sortable and searchable", s.navSongs.value) { s.navSongs.set(it) }
        SwitchRow("Albums", "Cover grid of your albums", s.navAlbums.value) { s.navAlbums.set(it) }
        SwitchRow("Browse", "Artists and folders", s.navBrowse.value) { s.navBrowse.set(it) }
        SwitchRow("Playlists", "Your playlists and smart lists", s.navPlaylists.value) { s.navPlaylists.set(it) }
        SwitchRow("Online", "YouTube, SoundCloud, radio and links — no account or API key", s.navOnline.value) { s.navOnline.set(it) }
        SwitchRow("Downloads", "Everything you downloaded, with the running downloads on top", s.navDownloads.value) { s.navDownloads.set(it) }
        RowCard { TitleSub("Settings", "Always shown so you can never lock yourself out", Modifier.weight(1f)) }
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------- stats
@Composable
private fun StatsPage(vm: PlayerViewModel, onBack: () -> Unit) {
    val t = LocalSudo.current
    val byId = remember(vm.rawSongs) { vm.rawSongs.associateBy { it.id } }
    val played = vm.plays.filter { byId.containsKey(it.key) }
    val totalPlays = played.values.sum()
    val totalMs = played.entries.sumOf { (byId[it.key]?.durationMs ?: 0L) * it.value }
    val topSongs = played.entries.sortedByDescending { it.value }.take(5).mapNotNull { e -> byId[e.key]?.let { it to e.value } }
    val topArtists = played.entries.groupBy { byId[it.key]?.artist ?: "" }.mapValues { it.value.sumOf { e -> e.value } }.entries
        .filter { it.key.isNotBlank() }.sortedByDescending { it.value }.take(5)
    val topAlbums = played.entries.groupBy { byId[it.key]?.album ?: "" }.mapValues { it.value.sumOf { e -> e.value } }.entries
        .filter { it.key.isNotBlank() }.sortedByDescending { it.value }.take(3)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubHeader("Your stats", onBack)
        RowCard {
            Column(Modifier.weight(1f)) {
                Text("${totalMs / 3_600_000} h ${totalMs / 60_000 % 60} min", color = t.primary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("listened • $totalPlays plays • ${played.size} different tracks", color = t.text.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (topSongs.isEmpty()) RowCard { TitleSub("Nothing yet", "Play some music and come back", Modifier.weight(1f)) }
        SectionLabel("Top tracks")
        topSongs.forEachIndexed { i, (song, n) -> RowCard { TitleSub("${i + 1}. ${song.title}", "${song.artist} • $n plays", Modifier.weight(1f)) } }
        SectionLabel("Top artists")
        topArtists.forEachIndexed { i, e -> RowCard { TitleSub("${i + 1}. ${e.key}", "${e.value} plays", Modifier.weight(1f)) } }
        SectionLabel("Top albums")
        topAlbums.forEachIndexed { i, e -> RowCard { TitleSub("${i + 1}. ${e.key}", "${e.value} plays", Modifier.weight(1f)) } }
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------- features
@Composable
private fun SoundPage(vm: PlayerViewModel, onBack: () -> Unit) {
    val s = vm.settings
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubHeader("Sound & playback", onBack)
        SectionLabel("Sound")
        NavRow(I.Equalizer, "Sound studio", "10-band parametric EQ, scenes, reverb, echo, surround, night mode") { vm.openStudio = true }
        SliderRow("Fade between songs", if (s.crossfade.value == 0) "Off" else "${s.crossfade.value} s", s.crossfade.value.toFloat(), 0f..12f, steps = 11, onDone = { vm.applyMisc() }) { s.crossfade.set(it.toInt()) }
        HintLine("Overlaps the end of a song with the start of the next. Takes effect on the next track change.")
        SwitchRow("Even out loudness (ReplayGain)", "Brings every song to a similar volume so you stop touching the volume key", s.replayGain.value) { s.replayGain.set(it); vm.applyMisc() }
        SwitchRow("Sound per output", "Remembers your EQ for each headphones / speaker (now: ${vm.outputName})", s.deviceEq.value) { s.deviceEq.set(it) }
        SectionLabel("Playback")
        SwitchRow("Skip silence", "Jumps over silent gaps inside tracks — good for lectures and live recordings", s.skipSilence.value) { s.skipSilence.set(it); vm.applyMisc() }
        SwitchRow("Fade out when the sleep timer ends", "Volume eases down over the last 15 seconds instead of cutting off", s.sleepFade.value) { s.sleepFade.set(it) }
        SwitchRow("Shake to skip", "Shake the phone to jump to the next track (only while playing)", s.shake.value) { s.shake.set(it) }
        SwitchRow("Resume when headphones connect", "Starts playing again when Bluetooth or wired audio connects", s.resumeOnConnect.value) { s.resumeOnConnect.set(it) }
        SectionLabel("Long recordings")
        SwitchRow("Lecture mode", "Recordings longer than ${s.lectureMinutes.value} min remember where you stopped and the speed you used", s.lectureMode.value) { s.lectureMode.set(it) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CarPage(vm: PlayerViewModel, onBack: () -> Unit) {
    val s = vm.settings
    val t = LocalSudo.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubHeader("Car & launch", onBack)
        SectionLabel("Car mode")
        RowCard {
            Column(Modifier.weight(1f)) {
                Text("Car mode", color = t.text, fontWeight = FontWeight.SemiBold)
                Text("Big buttons for a head unit. Auto = on big landscape screens", color = t.text.copy(alpha = 0.62f), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("never" to "Never", "auto" to "Auto", "always" to "Always").forEach { (id, label) ->
                        val on = s.carStart.value == id
                        Text(
                            label, color = if (on) t.onAccent else t.text, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.clip(RoundedCornerShape(50))
                                .then(if (on) Modifier.background(Brush.horizontalGradient(listOf(t.primary, t.secondary))) else Modifier.border(1.dp, t.text.copy(alpha = 0.25f), RoundedCornerShape(50)))
                                .clickable { s.carStart.set(id) }.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
        RowCard(onClick = { vm.carMode = true }) {
            Icon(I.Explore, null, tint = t.primary)
            Spacer(Modifier.width(12.dp))
            TitleSub("Open car mode now", null, Modifier.weight(1f))
        }
        SwitchRow("Play when the app opens", "Resumes your last queue by itself — useful when the head unit launches the app", s.autoPlayLaunch.value) { s.autoPlayLaunch.set(it) }
        SectionLabel("Player")
        SwitchRow("Keep screen on", "While the player page is open", s.keepScreenOn.value) { s.keepScreenOn.set(it) }
        SwitchRow("Open on lyrics", "Show lyrics first instead of the cover", s.lyricsDefault.value) { s.lyricsDefault.set(it) }
        SectionLabel("Player page")
        SwitchRow("Keep screen on", "While the player page is open", s.keepScreenOn.value) { s.keepScreenOn.set(it) }
        SwitchRow("Open on lyrics", "Show lyrics first instead of the cover", s.lyricsDefault.value) { s.lyricsDefault.set(it) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun OnlineSettingsPage(vm: PlayerViewModel, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val s = vm.settings
    val t = LocalSudo.current
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("sudoplay", android.content.Context.MODE_PRIVATE) }
    var suggest by remember { mutableStateOf(prefs.getBoolean("online_suggest", false)) }
    var hist by remember { mutableStateOf((prefs.getString("online_hist", "") ?: "").split("\n").count { it.isNotBlank() }) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubHeader("Online & downloads", onBack)
        SectionLabel("Sources")
        HintLine("Which sources a search uses is chosen in the Online tab (Sources button). Everything works without accounts or API keys.")
        SwitchRow("Show the Online tab", "YouTube, SoundCloud, Audius, Quran, archive.org, radio and links", s.navOnline.value) { s.navOnline.set(it) }
        SectionLabel("Downloads")
        SwitchRow("Save cover & lyrics with downloads", "After a download finishes, fetches the picture and (from lrclib.net) the lyrics so the song looks complete offline. Sends only title and artist", s.dlExtras.value) { s.dlExtras.set(it) }
        SwitchRow("Download on Wi-Fi only", "Refuses new downloads on mobile data", s.dlWifiOnly.value) { s.dlWifiOnly.set(it) }
        SectionLabel("Discovery")
        SwitchRow("Personalized “For you”", "Sends the names of your most-played artists to Deezer to find related music. Off by default; charts and genres never send anything about you", s.discoverPersonal.value) { s.discoverPersonal.set(it) }
        SectionLabel("Privacy")
        SwitchRow("Search suggestions", "Sends what you type to Google's suggestion service while you type. Off by default", suggest) { suggest = it; prefs.edit().putBoolean("online_suggest", it).apply() }
        SwitchRow("Download missing lyrics", "Asks lrclib.net (free, no account) when a song has none. Sends only title, artist, album and length", s.autoLyrics.value) { s.autoLyrics.set(it) }
        NavRow(I.Search, "Network log", "${com.sudosoc.sudoplay.data.NetLog.entries.size} requests since the app started • see exactly what leaves your phone") { onOpen("netlog") }
        RowCard(onClick = { prefs.edit().remove("online_hist").apply(); hist = 0 }) {
            Icon(I.DeleteSweep, null, tint = t.primary)
            Spacer(Modifier.width(12.dp))
            TitleSub("Clear online search history", "$hist saved searches", Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BackupPage(vm: PlayerViewModel, onBack: () -> Unit) {
    val t = LocalSudo.current
    val ctx = LocalContext.current
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(vm.backupJson().toByteArray()) }
            Toast.makeText(ctx, "Backup saved", Toast.LENGTH_SHORT).show()
        }.onFailure { Toast.makeText(ctx, "Could not save the backup", Toast.LENGTH_SHORT).show() }
    }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            if (text != null && vm.restoreJson(text)) {
                Toast.makeText(ctx, "Restored — restarting…", Toast.LENGTH_SHORT).show()
                ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.component?.let { c ->
                    ctx.startActivity(Intent.makeRestartActivityTask(c))
                }
                Runtime.getRuntime().exit(0)
            } else Toast.makeText(ctx, "That is not a SUDOPLAY backup", Toast.LENGTH_SHORT).show()
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubHeader("Backup & restore", onBack)
        SectionLabel("Backup")
        HintLine("One file with your settings, EQ scenes, playlists and play counts. Restoring restarts the app.")
        RowCard(onClick = { saver.launch("sudoplay-backup.json") }) {
            Icon(I.Collections, null, tint = t.primary)
            Spacer(Modifier.width(12.dp))
            TitleSub("Back up", "Settings, scenes, play counts and playlists in one file", Modifier.weight(1f))
        }
        RowCard(onClick = { opener.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }) {
            Icon(I.RestartAlt, null, tint = t.primary)
            Spacer(Modifier.width(12.dp))
            TitleSub("Restore", "Pick a backup file; the app restarts", Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------- gesture editor
@Composable
private fun ActionPicker(title: String, sub: String?, current: String, onPick: (String) -> Unit) {
    val t = LocalSudo.current
    var open by remember { mutableStateOf(false) }
    Box {
        RowCard(onClick = { open = true }) {
            TitleSub(title, sub, Modifier.weight(1f))
            Text(GestureActions.label(current), color = t.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        }
        SudoMenu(open, { open = false }) {
            GestureActions.all.forEach { (id, label) -> SudoMenuItem(label, selected = id == current) { onPick(id); open = false } }
        }
    }
}

@Composable
private fun GestureEditorPage(vm: PlayerViewModel, onBack: () -> Unit) {
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
private fun ReorderRow(order: List<String>, shown: (String) -> Boolean, onChange: (List<String>) -> Unit) {
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
private fun ArrangePage(vm: PlayerViewModel, onBack: () -> Unit) {
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


// ---------------------------------------------------------------- network log
@Composable
private fun NetLogPage(onBack: () -> Unit) {
    val t = LocalSudo.current
    val entries = com.sudosoc.sudoplay.data.NetLog.entries
    Column(Modifier.fillMaxSize()) {
        SubHeader("Network log", onBack)
        Text(
            "Every request this app makes. With \"Download missing lyrics\" off, nothing here will ever appear: SUDOPLAY has no other network code.",
            color = t.text.copy(alpha = 0.62f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
        )
        if (entries.isEmpty()) Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
            Text("No requests yet", color = t.text.copy(alpha = 0.6f))
        } else LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            lazyItems(entries.toList()) { e ->
                val fmtT = remember(e.time) { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(e.time)) }
                RowCard {
                    Column(Modifier.weight(1f)) {
                        Text("${e.method} ${e.host}${e.path}", color = t.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$fmtT • ${if (e.status > 0) "HTTP ${e.status}" else "failed"} • ${e.bytes} bytes • ${e.ms} ms", color = t.text.copy(alpha = 0.62f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- fonts & colors
@Composable
private fun FontsPage(vm: PlayerViewModel, onBack: () -> Unit) {
    val s = vm.settings
    val t = LocalSudo.current
    var hexText by remember { mutableStateOf(s.textColor.value) }
    var hexAccent by remember { mutableStateOf(s.accentColor.value) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubHeader("Fonts & colors", onBack)
        // live preview
        RowCard {
            Column(Modifier.weight(1f)) {
                Text("SUDOPLAY", color = t.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Text("The quick brown fox • أهلاً بك في مشغّل الموسيقى", color = t.text, style = MaterialTheme.typography.bodyLarge)
                Text("Artist name  •  3:45  •  ١٢٣٤٥٦٧٨٩٠ 1234567890", color = t.text.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
            }
        }
        val lowContrast = Fonts.contrast(t.text, t.bgTop) < 3f
        if (lowContrast) HintLine("⚠ This text color is hard to read on the current theme. Pick another color or reset it.")

        SectionLabel("Font")
        HintLine("Applies to the whole app. All fonts are built in and work offline. Fonts marked Arabic draw Arabic letters in their own style.")
        Fonts.all.forEach { f ->
            val on = s.fontFamily.value == f.key
            RowCard(onClick = { s.fontFamily.set(f.key) }) {
                Column(Modifier.weight(1f)) {
                    Text(f.label + "  ·  أهلاً 123", color = if (on) t.primary else t.text, fontWeight = FontWeight.SemiBold, fontFamily = f.family)
                    Text(f.note, color = t.text.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                }
                if (on) Icon(I.Check, null, tint = t.primary)
            }
        }

        SectionLabel("Text size")
        RowCard {
            Column(Modifier.weight(1f)) {
                Text("${s.fontScale.value}%", color = t.text, fontWeight = FontWeight.SemiBold)
                Slider(s.fontScale.value.toFloat(), { s.fontScale.set((it / 5).toInt() * 5) }, valueRange = 80f..140f)
                Text("Scales every text in the app on top of your phone's own font size.", color = t.text.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
            }
        }

        SectionLabel("Text color")
        HintLine("Overrides the theme's text color everywhere. Pick “Theme” to go back to the theme's own.")
        ColorPicker(s.textColor.value, hexText, { hexText = it }) { s.textColor.set(it); hexText = it }

        SectionLabel("Accent color")
        HintLine("Buttons, highlights, the playing song, sliders. Pick “Theme” to go back.")
        ColorPicker(s.accentColor.value, hexAccent, { hexAccent = it }) { s.accentColor.set(it); hexAccent = it }

        RowCard(onClick = { s.fontFamily.set("default"); s.fontScale.set(100); s.textColor.set(""); s.accentColor.set(""); hexText = ""; hexAccent = "" }) {
            Icon(I.RestartAlt, null, tint = t.primary)
            Spacer(Modifier.width(12.dp))
            TitleSub("Reset fonts & colors", "Back to the theme's own look", Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPicker(current: String, hexText: String, onHexText: (String) -> Unit, onPick: (String) -> Unit) {
    val t = LocalSudo.current
    RowCard {
        Column(Modifier.weight(1f)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val themeOn = current.isBlank()
                Text(
                    "Theme", color = if (themeOn) t.onAccent else t.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.height(36.dp).clip(RoundedCornerShape(50))
                        .then(if (themeOn) Modifier.background(Brush.horizontalGradient(listOf(t.primary, t.secondary))) else Modifier.border(1.dp, t.text.copy(alpha = 0.3f), RoundedCornerShape(50)))
                        .clickable { onPick("") }.padding(horizontal = 14.dp, vertical = 9.dp),
                )
                Fonts.swatches.forEach { (name, rgb) ->
                    val c = Color(0xFF000000 or rgb.toLong())
                    val hex = Fonts.hexOf(c)
                    val on = current.equals(hex, true)
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(c)
                            .border(if (on) 3.dp else 1.dp, if (on) t.primary else t.text.copy(alpha = 0.3f), CircleShape)
                            .clickable { onPick(hex) },
                        contentAlignment = Alignment.Center,
                    ) { if (on) Icon(I.Check, name, tint = if (c.luminance() > 0.5f) Color.Black else Color.White, modifier = Modifier.size(18.dp)) }
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                hexText, { onHexText(it); Fonts.parse(it)?.let { _ -> onPick(it.trim().let { h -> if (h.startsWith("#")) h else "#$h" }) } },
                singleLine = true, label = { Text("Custom color (#RRGGBB)") }, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ---------------------------------------------------------------- logos
@Composable
private fun LogosPage(vm: PlayerViewModel, onBack: () -> Unit) {
    val s = vm.settings
    val t = LocalSudo.current
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubHeader("App logo & icon", onBack)
        HintLine("Pick the logo you like. It is used in Settings and About, and it becomes the app icon on your home screen / app drawer. Some launchers need a few seconds to show the new icon.")
        RowCard {
            LogoImage(s.appLogo.value, 72.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Wordmark("SUDO", "PLAY", 26.sp)
                Text(Logos.names[s.appLogo.value.coerceIn(0, Logos.count)], color = t.text.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
            }
        }
        SectionLabel("Choose")
        val all = (1..Logos.count).toList() + 0
        all.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { n ->
                    val on = s.appLogo.value == n
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                            .border(if (on) 2.dp else 1.dp, if (on) t.primary else t.text.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                            .clickable {
                                s.appLogo.set(n); Logos.applyLauncher(ctx, n); vm.haptics.click()
                                Toast.makeText(ctx, "Icon changed — the launcher may take a moment to update", Toast.LENGTH_SHORT).show()
                            }.padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        LogoImage(n, 64.dp)
                        Text(Logos.names[n], color = if (on) t.primary else t.text.copy(alpha = 0.7f), fontSize = 10.sp, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        HintLine("If your launcher shows a blank or old icon, long-press the app → remove the shortcut, then drag SUDOPLAY out of the app drawer again.")
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------- about
private data class FeatureGroup(val icon: ImageVector, val title: String, val lines: List<String>)

@Composable
private fun AboutPage(vm: PlayerViewModel, onBack: () -> Unit) {
    val s = vm.settings
    val t = LocalSudo.current
    val ctx = LocalContext.current
    var dev by remember { mutableStateOf(false) }
    val groups = listOf(
        FeatureGroup(I.MusicNote, "Your music", listOf(
            "Songs, albums, artists and folders, with fast search that understands Arabic",
            "Playlists, favorites and smart lists: recently played, most played, newly added, unplayed and a daily mix",
            "Queue, play next, A-B loop, bookmarks, sleep timer with fade-out and lecture mode that remembers position and speed",
            "Synced lyrics (file, tags or optional lrclib) and a real waveform seek bar",
        )),
        FeatureGroup(I.Equalizer, "Sound studio", listOf(
            "10-band parametric equalizer with live response graph and spectrum, ${com.sudosoc.sudoplay.data.ParamPresets.all.size} presets and saved scenes",
            "Bass, virtualizer, reverb, loudness, limiter, ReplayGain, crossfade, skip silence, pitch and speed",
        )),
        FeatureGroup(I.Palette, "Looks", listOf(
            "${Themes.all.size} app themes (${Themes.all.count { it.live != null }} animated) and ${PlayerThemes.all.size} player themes with live backgrounds that follow the bass",
            "${Logos.count} logos that also change the launcher icon, ${Fonts.all.size} fonts, 24 text and accent colors, ${IconStyle.entries.size} icon families, seek bar shapes and a movable navigation bar",
            "Cover colors: the whole app can take its palette from the album art",
        )),
        FeatureGroup(I.Explore, "Online — no account, no API key", listOf(
            "One search over YouTube, SoundCloud, Audius, archive.org, radio and a full Quran library (every sheikh and surah)",
            "Discovery: trending by genre, playlists to import in one tap, Audius trending and optional suggestions from what you play",
            "Import playlists from YouTube, SoundCloud, Spotify, Deezer, Audius, pasted lists, .m3u and .csv files",
            "Download any song for offline use with its cover and lyrics. Playlists show ONLINE or ON DEVICE on every song",
        )),
        FeatureGroup(I.Extension, "Everything else", listOf(
            "Gestures you can remap, haptics, 3 home-screen widgets, video clip maker, car mode for head units",
            "Duplicate finder, listening stats, hidden folders, one-file backup and restore",
        )),
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubHeader("About", onBack)
        // header
        Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            LogoImage(s.appLogo.value, 96.dp)
            Spacer(Modifier.height(12.dp))
            Wordmark("SUDO", "PLAY", 34.sp)
            Text("Music Player by SUDOSOC", color = t.text.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium)
            Text("v$APP_VERSION", color = t.primary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
        }
        HintLine("A private, offline-first music player for Android that also knows the internet — built for one person first, then shared with friends. No ads, no account, no tracking.")

        SectionLabel("What it can do")
        groups.forEach { g ->
            RowCard {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(t.primary, t.secondary))), contentAlignment = Alignment.Center) {
                    Icon(g.icon, null, tint = t.onAccent)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(g.title, color = t.text, fontWeight = FontWeight.Bold)
                    g.lines.forEach { Text("•  $it", color = t.text.copy(alpha = 0.68f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 3.dp)) }
                }
            }
        }

        SectionLabel("Your privacy")
        RowCard {
            Column(Modifier.weight(1f)) {
                Text(
                    "Nothing leaves your phone unless you use an online feature, and every request is written to the Network log (Settings → Online & downloads) so you can see exactly what was sent. " +
                        "Lyrics, search suggestions, “For you” and cover/lyrics for downloads are optional and explained next to their switches.",
                    color = t.text.copy(alpha = 0.68f), style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        SectionLabel("Under the hood")
        RowCard {
            Column(Modifier.weight(1f)) {
                Text(
                    "Kotlin • Jetpack Compose • Media3 • OkHttp • NewPipeExtractor. Online data from Deezer's public charts, Audius, archive.org, lrclib.net, a public radio directory and a bundled Quran index. " +
                        "Fonts are open-source (SIL OFL): Cairo, Tajawal, Almarai, Noto Kufi, Changa, Reem Kufi, El Messiri, Lemonada, Marhey, Amiri, Rubik, Poppins, Space Grotesk, JetBrains Mono.",
                    color = t.text.copy(alpha = 0.68f), style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        RowCard(onClick = { openUrl(ctx, "https://github.com/sudosoc/SUDOPLAY") }) {
            Icon(I.Extension, null, tint = t.primary)
            Spacer(Modifier.width(12.dp))
            TitleSub("Source code", "github.com/sudosoc/SUDOPLAY", Modifier.weight(1f))
        }

        // about the developer — last thing on the page
        SectionLabel("The developer")
        RowCard(onClick = { dev = true }) {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(com.sudosoc.sudoplay.R.drawable.dev_photo), null,
                Modifier.size(46.dp).clip(CircleShape), contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
            TitleSub("About the developer", "SUDOSOC • tap to meet him", Modifier.weight(1f))
            Icon(I.ChevronRight, null, tint = t.text.copy(alpha = 0.5f))
        }
        Spacer(Modifier.height(28.dp))
    }
    if (dev) DevCard { dev = false }
}

private fun openUrl(ctx: android.content.Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** The developer card: photo, the SUDOSOC wordmark, name in small gray, and three links. */
@Composable
private fun DevCard(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val shape = RoundedCornerShape(28.dp)
    val ink = Color(0xFFEDEFF7)
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xFF14142B), Color(0xFF07070F))))
                .border(1.dp, Brush.linearGradient(listOf(Color(0xFF38BDF8).copy(alpha = 0.7f), Color(0xFFB44CFF).copy(alpha = 0.7f))), shape)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(132.dp).clip(CircleShape).border(3.dp, Logos.brand, CircleShape).padding(5.dp).clip(CircleShape),
            ) {
                androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(com.sudosoc.sudoplay.R.drawable.dev_photo), null,
                    Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            }
            Spacer(Modifier.height(16.dp))
            Wordmark("SUDO", "SOC", 34.sp, ink)
            Text("Seif Alsayed Ali", color = Color(0xFF8A8FA3), fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.height(18.dp))
            DevLink(androidx.compose.material.icons.Icons.Rounded.Code, "GitHub", "github.com/sudosoc") { openUrl(ctx, "https://github.com/sudosoc") }
            DevLink(androidx.compose.material.icons.Icons.Rounded.Language, "Portfolio", "seif.sudosoc.com") { openUrl(ctx, "https://seif.sudosoc.com") }
            DevLink(androidx.compose.material.icons.Icons.Rounded.Email, "Email", "ceo@sudosoc.com") {
                runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:ceo@sudosoc.com")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            TextButton(onDismiss, Modifier.padding(top = 6.dp)) { Text("Close", color = Color(0xFF8A8FA3)) }
        }
    }
}

@Composable
private fun DevLink(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(shape).background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Logos.brand), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = Color(0xFF8A8FA3), fontSize = 11.sp)
            Text(value, color = Color(0xFFEDEFF7), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}
