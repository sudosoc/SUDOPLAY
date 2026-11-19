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

// ---------------------------------------------------------------- extras
@Composable
internal fun ExtrasPage(vm: PlayerViewModel, onOpen: (String) -> Unit, onBack: () -> Unit) {
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
        var askClear by remember { mutableStateOf(false) }
        if (askClear) AlertDialog(
            onDismissRequest = { askClear = false }, title = { Text("Clear play history?") },
            text = { Text("Play counts, Most played and the Recent list start from zero. Your songs and playlists are not touched.") },
            confirmButton = { TextButton({ vm.clearHistory(); askClear = false }) { Text("Clear") } },
            dismissButton = { TextButton({ askClear = false }) { Text("Cancel") } },
        )
        RowCard(onClick = { askClear = true }) {
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
internal fun HiddenFoldersPage(vm: PlayerViewModel, onBack: () -> Unit) {
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
internal fun PropertiesPage(vm: PlayerViewModel, onOpen: (String) -> Unit, onBack: () -> Unit) {
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
internal fun NavBarPage(vm: PlayerViewModel, onBack: () -> Unit) {
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
internal fun StatsPage(vm: PlayerViewModel, onBack: () -> Unit) {
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

