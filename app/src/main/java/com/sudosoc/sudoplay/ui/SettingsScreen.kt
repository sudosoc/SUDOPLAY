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
import com.sudosoc.sudoplay.PlayerViewModel
import com.sudosoc.sudoplay.data.CoverStore

@Composable
fun SettingsTab(vm: PlayerViewModel) {
    var page by rememberSaveable { mutableStateOf("home") }

    BackHandler(enabled = page != "home") {
        page = when (page) {
            "app_themes", "player_themes", "seek_styles", "icon_styles", "player_buttons", "nav_bar" -> "appearance"
            "arrange" -> "player_buttons"
            "stats", "dupes" -> "features"
            "gesture_editor" -> "props"
            "hidden_folders", "netlog" -> "extras"
            else -> "home"
        }
    }

    when (page) {
        "appearance" -> AppearancePage(vm, onOpen = { page = it }, onBack = { page = "home" })
        "app_themes" -> AppThemesPage(vm) { page = "appearance" }
        "player_themes" -> PlayerThemesPage(vm) { page = "appearance" }
        "seek_styles" -> SeekStylesPage(vm) { page = "appearance" }
        "icon_styles" -> IconStylesPage(vm) { page = "appearance" }
        "player_buttons" -> PlayerButtonsPage(vm, onOpen = { page = it }) { page = "appearance" }
        "arrange" -> ArrangePage(vm) { page = "player_buttons" }
        "gesture_editor" -> GestureEditorPage(vm) { page = "props" }
        "nav_bar" -> NavBarPage(vm) { page = "appearance" }
        "stats" -> StatsPage(vm) { page = "features" }
        "dupes" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { SubHeader("Duplicate finder", { page = "features" }); DuplicatesBody(vm) }
        "extras" -> ExtrasPage(vm, onOpen = { page = it }) { page = "home" }
        "hidden_folders" -> HiddenFoldersPage(vm) { page = "extras" }
        "netlog" -> NetLogPage { page = "extras" }
        "props" -> PropertiesPage(vm, onOpen = { page = it }) { page = "home" }
        "features" -> FeaturesPage(vm, onOpen = { page = it }) { page = "home" }
        else -> HomePage(vm) { page = it }
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Settings")
        BigSection(I.Palette, "Appearance", "المظهر", "App themes and player-page themes") { onOpen("appearance") }
        BigSection(I.Extension, "Extras", "الإضافات", "Library filters, hidden folders, .lrc lyrics, covers") { onOpen("extras") }
        BigSection(I.Tune, "Properties", "الخصائص", "Gestures, seek sensitivity, vibration") { onOpen("props") }
        BigSection(I.AutoAwesome, "Features", "المميزات", "Sound, lyrics, play history, backup") { onOpen("features") }
        BigSection(I.Explore, "Car mode", "وضع السيارة", "Big landscape screen for the head unit") { vm.carMode = true }
        Text("SUDOPLAY 0.9.0 • by SUDOSOC", color = t.text.copy(alpha = 0.4f), style = MaterialTheme.typography.labelSmall,
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
        NavRow(I.Apps, "App themes", "${Themes.byId(s.theme.value).name} • ${Themes.all.size} themes") { onOpen("app_themes") }
        NavRow(I.PlayCircle, "Player themes", "${PlayerThemes.byId(s.playerTheme.value).name} • ${PlayerThemes.all.size} looks, live previews") { onOpen("player_themes") }
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
        if (th.style == Style.Glass || th.style == Style.Gradient || th.style == Style.Art) {
            Box(Modifier.size(90.dp).align(Alignment.TopStart).background(Brush.radialGradient(listOf(th.primary.copy(alpha = 0.45f), Color.Transparent))))
            Box(Modifier.size(90.dp).align(Alignment.BottomEnd).background(Brush.radialGradient(listOf(th.secondary.copy(alpha = 0.45f), Color.Transparent))))
        }
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            val panelShape = RoundedCornerShape(14.dp)
            val panel = when (th.style) {
                Style.Solid -> Modifier.background(th.surface, panelShape)
                Style.Glass -> Modifier.background(if (th.dark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.55f), panelShape)
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
                Text(th.style.label + if (th.dynamic) " • wallpaper colors" else "", color = th.text.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
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
        SwitchRow("Lyrics", null, s.btnLyrics.value) { s.btnLyrics.set(it) }
        SwitchRow("Queue", null, s.btnQueue.value) { s.btnQueue.set(it) }
        SectionLabel("Next to the title")
        SwitchRow("Favorite", null, s.btnFav.value) { s.btnFav.set(it) }
        SwitchRow("Add to playlist", null, s.btnPlaylist.value) { s.btnPlaylist.set(it) }
        SectionLabel("Transport")
        SwitchRow("Shuffle", null, s.btnShuffle.value) { s.btnShuffle.set(it) }
        SwitchRow("Previous", null, s.btnPrev.value) { s.btnPrev.set(it) }
        SwitchRow("Next", null, s.btnNext.value) { s.btnNext.set(it) }
        SwitchRow("Repeat", null, s.btnRepeat.value) { s.btnRepeat.set(it) }
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
        SubHeader("Extras", onBack)

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

        SectionLabel("Lyrics")
        val canLrc = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()
        NavRow(I.Lyrics, "Lyrics from .lrc files", if (canLrc) "Enabled" else "Tap to allow all-files access") {
            if (!canLrc) ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            )
        }

        SwitchRow("Download missing lyrics", "Asks lrclib.net (free, no account) when a song has none. Sends only title, artist, album and length", s.autoLyrics.value) { s.autoLyrics.set(it) }
        NavRow(I.Search, "Network log", "${com.sudosoc.sudoplay.data.NetLog.entries.size} requests since the app started • see exactly what leaves your phone") { onOpen("netlog") }

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
        SwitchRow("Songs", null, s.navSongs.value) { s.navSongs.set(it) }
        SwitchRow("Albums", null, s.navAlbums.value) { s.navAlbums.set(it) }
        SwitchRow("Browse", "Artists and folders", s.navBrowse.value) { s.navBrowse.set(it) }
        SwitchRow("Playlists", null, s.navPlaylists.value) { s.navPlaylists.set(it) }
        SwitchRow("Online", "YouTube, SoundCloud, radio and links — no account or API key", s.navOnline.value) { s.navOnline.set(it) }
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
private fun FeaturesPage(vm: PlayerViewModel, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val s = vm.settings
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
        SubHeader("Features", onBack)
        SectionLabel("Playback")
        SwitchRow("Skip silence", "Jumps over silent gaps inside tracks", s.skipSilence.value) { s.skipSilence.set(it); vm.applyMisc() }
        SwitchRow("Fade out when the sleep timer ends", "Volume eases down over the last 15 seconds", s.sleepFade.value) { s.sleepFade.set(it) }
        SwitchRow("Shake to skip", "Shake the phone to jump to the next track (only while playing)", s.shake.value) { s.shake.set(it) }
        SectionLabel("Smart listening")
        SwitchRow("Lecture mode", "Recordings longer than ${s.lectureMinutes.value} min remember where you stopped and your speed", s.lectureMode.value) { s.lectureMode.set(it) }
        SwitchRow("Sound per output", "Remembers your EQ for each headphones / speaker (now: ${vm.outputName})", s.deviceEq.value) { s.deviceEq.set(it) }
        SwitchRow("Resume when headphones connect", "Starts playing again when Bluetooth or wired audio connects", s.resumeOnConnect.value) { s.resumeOnConnect.set(it) }
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
        SectionLabel("Car")
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
        SectionLabel("Library")
        NavRow(I.Whatshot, "Your stats", "Listening time, top tracks, artists and albums") { onOpen("stats") }
        SwitchRow("Count plays", "Feeds Recently played / Most played / Never played / Mix", s.countPlays.value) { s.countPlays.set(it) }
        RowCard(onClick = { vm.clearHistory() }) {
            Icon(I.DeleteSweep, null, tint = t.primary)
            Spacer(Modifier.width(12.dp))
            TitleSub("Clear play history", "Resets play counts and the recent list", Modifier.weight(1f))
        }
        SectionLabel("Backup")
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
