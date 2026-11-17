package com.sudosoc.sudoplay.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
    var showEq by remember { mutableStateOf(false) }

    BackHandler(enabled = page != "home") {
        page = when (page) { "app_themes", "player_themes" -> "appearance"; else -> "home" }
    }

    when (page) {
        "appearance" -> AppearancePage(vm, onOpen = { page = it }, onBack = { page = "home" })
        "app_themes" -> AppThemesPage(vm) { page = "appearance" }
        "player_themes" -> PlayerThemesPage(vm) { page = "appearance" }
        "extras" -> ExtrasPage(vm, onEq = { showEq = true }) { page = "home" }
        "props" -> PropertiesPage(vm) { page = "home" }
        "features" -> FeaturesPage(vm) { page = "home" }
        else -> HomePage(vm) { page = it }
    }
    if (showEq) EqSheet(vm) { showEq = false }
}

// ---------------------------------------------------------------- shared bits
@Composable
private fun SubHeader(title: String, onBack: () -> Unit) {
    val t = LocalSudo.current
    Row(Modifier.padding(top = 6.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = t.text) }
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
private fun SliderRow(title: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    val t = LocalSudo.current
    RowCard {
        Column(Modifier.weight(1f)) {
            Row {
                Text(title, color = t.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(valueText, color = t.primary, fontWeight = FontWeight.Bold)
            }
            Slider(value, onChange, valueRange = range, steps = steps,
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
        Icon(Icons.Rounded.ChevronRight, null, tint = t.text.copy(alpha = 0.5f))
    }
}

// ---------------------------------------------------------------- home
@Composable
private fun HomePage(vm: PlayerViewModel, onOpen: (String) -> Unit) {
    val t = LocalSudo.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Settings")
        BigSection(Icons.Rounded.Palette, "Appearance", "المظهر", "App themes and player-page themes") { onOpen("appearance") }
        BigSection(Icons.Rounded.Extension, "Extras", "الإضافات", "Equalizer, lyrics files, sleep timer, covers") { onOpen("extras") }
        BigSection(Icons.Rounded.Tune, "Properties", "الخصائص", "Gestures, seek sensitivity, haptics") { onOpen("props") }
        BigSection(Icons.Rounded.AutoAwesome, "Features", "المميزات", "Screen, lyrics, play history") { onOpen("features") }
        Text("SUDOPLAY 0.3.0 • by SUDOSOC", color = t.text.copy(alpha = 0.4f), style = MaterialTheme.typography.labelSmall,
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
        Icon(Icons.Rounded.ChevronRight, null, tint = t.text.copy(alpha = 0.5f))
    }
}

// ---------------------------------------------------------------- appearance
@Composable
private fun AppearancePage(vm: PlayerViewModel, onOpen: (String) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SubHeader("Appearance", onBack)
        NavRow(Icons.Rounded.Apps, "App themes", "${Themes.byId(vm.settings.theme.value).name} • ${Themes.all.size} themes") { onOpen("app_themes") }
        NavRow(Icons.Rounded.PlayCircle, "Player themes", "${PlayerThemes.byId(vm.settings.playerTheme.value).name} • ${PlayerThemes.all.size} looks") { onOpen("player_themes") }
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
        if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = th.primary, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
    }
}

@Composable
private fun PlayerThemesPage(vm: PlayerViewModel, onBack: () -> Unit) {
    val app = LocalSudo.current
    Column(Modifier.fillMaxSize()) {
        SubHeader("Player themes", onBack)
        Text("Only changes the Now Playing page. \"Classic\" follows your app theme.", color = app.text.copy(alpha = 0.62f),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
        LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(PlayerThemes.all, key = { it.id }) { pt -> PlayerThemeCard(pt, app, vm.settings.playerTheme.value == pt.id) { vm.settings.playerTheme.set(pt.id) } }
        }
    }
}

@Composable
private fun PlayerThemeCard(pt: PlayerTheme, app: AppTheme, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val accent = pt.accent ?: app.primary
    val accent2 = pt.accent2 ?: app.secondary
    val text = pt.text ?: app.text
    val bg = when (pt.bg) {
        PBg.App -> Brush.verticalGradient(listOf(app.bgTop, app.bgBottom))
        PBg.Gradient, PBg.Blobs -> Brush.verticalGradient(if (pt.colors.size >= 2) pt.colors else listOf(app.bgTop, app.bgBottom))
        PBg.ArtBlur, PBg.ArtGradient, PBg.FullBleed -> Brush.verticalGradient(listOf(Color(0xFF6B3FA0), Color(0xFF1B1030)))
    }
    Box(
        Modifier.fillMaxWidth().aspectRatio(0.7f).clip(shape).background(bg)
            .border(if (selected) 3.dp else 1.dp, if (selected) accent else Color.White.copy(alpha = 0.15f), shape)
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(6.dp))
            val artShape = when (pt.shape) {
                PShape.Circle -> CircleShape
                PShape.Squircle -> RoundedCornerShape(30)
                PShape.Poster -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 28.dp, bottomEnd = 28.dp)
                PShape.Polaroid -> RoundedCornerShape(3.dp)
                else -> RoundedCornerShape(14.dp)
            }
            val sample = Modifier.fillMaxWidth(if (pt.shape == PShape.Poster) 0.9f else 0.6f).aspectRatio(1f)
            Box(
                sample.then(if (pt.glow) Modifier.border(2.dp, accent, artShape) else Modifier)
                    .clip(artShape).background(Brush.linearGradient(listOf(accent, accent2))),
            ) {
                if (pt.spin) Box(Modifier.size(10.dp).align(Alignment.Center).clip(CircleShape).background(Color.Black.copy(alpha = 0.7f)))
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.width(54.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(text.copy(alpha = 0.85f)))
            Spacer(Modifier.height(4.dp))
            Box(Modifier.width(34.dp).height(4.dp).clip(RoundedCornerShape(3.dp)).background(text.copy(alpha = 0.45f)))
            Spacer(Modifier.weight(1f))
            val btn = Modifier.size(26.dp).clip(CircleShape)
            when (pt.controls) {
                PControls.Gradient -> Box(btn.background(Brush.linearGradient(listOf(accent, accent2))))
                PControls.Outline -> Box(btn.border(2.dp, accent, CircleShape))
                PControls.Plain -> Box(btn.background(text.copy(alpha = 0.85f)))
            }
            Spacer(Modifier.height(18.dp))
        }
        Text(pt.name, color = text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
        if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = accent, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
    }
}

// ---------------------------------------------------------------- extras
@Composable
private fun ExtrasPage(vm: PlayerViewModel, onEq: () -> Unit, onBack: () -> Unit) {
    val t = LocalSudo.current
    val ctx = LocalContext.current
    var covers by remember { mutableIntStateOf(CoverStore.count(ctx)) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubHeader("Extras", onBack)
        NavRow(Icons.Rounded.Equalizer, "Equalizer & effects", if (vm.eqEnabled) "On" else "Off") { onEq() }

        val canLrc = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()
        NavRow(Icons.Rounded.Lyrics, "Lyrics from .lrc files", if (canLrc) "Enabled" else "Tap to allow all-files access") {
            if (!canLrc) ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            )
        }

        SectionLabel("Sleep timer")
        RowCard {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 15, 30, 60).forEach { m ->
                    PillButton("${m}m", Icons.Rounded.Bedtime) { vm.setSleep(m) }
                }
            }
        }
        RowCard(onClick = { vm.setSleep(0) }) { TitleSub("Cancel sleep timer", null, Modifier.weight(1f)) }

        SectionLabel("Covers")
        RowCard {
            TitleSub("Custom covers", "$covers saved • long-press the cover in the player to change one", Modifier.weight(1f))
        }
        RowCard(onClick = { CoverStore.clearAll(ctx); covers = 0 }) {
            Icon(Icons.Rounded.Delete, null, tint = t.primary)
            Spacer(Modifier.width(12.dp))
            TitleSub("Reset all custom covers", null, Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------- properties
@Composable
private fun PropertiesPage(vm: PlayerViewModel, onBack: () -> Unit) {
    val s = vm.settings
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubHeader("Properties", onBack)
        SectionLabel("Player gestures")
        SwitchRow("Double-tap to play / pause", "Double-tap anywhere on the player page", s.doubleTap.value) { s.doubleTap.set(it) }
        SwitchRow("Swipe to change track", "Swipe right for next, left for previous", s.swipe.value) { s.swipe.set(it) }
        SwitchRow("Invert swipe direction", "Swipe left for next, right for previous", s.swipeInvert.value) { s.swipeInvert.set(it) }
        SwitchRow("Long-press and drag to seek", "Hold, then drag left or right", s.scrub.value) { s.scrub.set(it) }
        SliderRow(
            "Seek sensitivity", "${s.scrubSeconds.value}s per full swipe", s.scrubSeconds.value.toFloat(), 30f..300f, steps = 8,
        ) { s.scrubSeconds.set((it / 15).toInt() * 15) }
        SectionLabel("Feedback")
        SwitchRow("Haptic feedback", "Short vibration on long-press and gestures", s.haptics.value) { s.haptics.set(it) }
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------- features
@Composable
private fun FeaturesPage(vm: PlayerViewModel, onBack: () -> Unit) {
    val s = vm.settings
    val t = LocalSudo.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubHeader("Features", onBack)
        SectionLabel("Player")
        SwitchRow("Keep screen on", "While the player page is open", s.keepScreenOn.value) { s.keepScreenOn.set(it) }
        SwitchRow("Open on lyrics", "Show lyrics first instead of the cover", s.lyricsDefault.value) { s.lyricsDefault.set(it) }
        SectionLabel("Library")
        SwitchRow("Count plays", "Feeds Recently played / Most played / Never played", s.countPlays.value) { s.countPlays.set(it) }
        RowCard(onClick = { vm.clearHistory() }) {
            Icon(Icons.Rounded.DeleteSweep, null, tint = t.primary)
            Spacer(Modifier.width(12.dp))
            TitleSub("Clear play history", "Resets play counts and the recent list", Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
    }
}
