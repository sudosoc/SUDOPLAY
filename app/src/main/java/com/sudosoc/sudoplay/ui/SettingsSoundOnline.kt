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

// ---------------------------------------------------------------- features
@Composable
internal fun SoundPage(vm: PlayerViewModel, onBack: () -> Unit) {
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
internal fun CarPage(vm: PlayerViewModel, onBack: () -> Unit) {
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
        SectionLabel("Player page")
        SwitchRow("Keep screen on", "While the player page is open", s.keepScreenOn.value) { s.keepScreenOn.set(it) }
        SwitchRow("Open on lyrics", "Show lyrics first instead of the cover", s.lyricsDefault.value) { s.lyricsDefault.set(it) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun OnlineSettingsPage(vm: PlayerViewModel, onOpen: (String) -> Unit, onBack: () -> Unit) {
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
        SectionLabel("Streaming")
        ChoiceRow("Streaming quality", "Auto uses the best on Wi-Fi and a lighter file on mobile data", listOf(0 to "Auto", 1 to "High", 2 to "Data saver"), s.streamQuality.value) { s.streamQuality.set(it) }
        ChoiceRow("Replay cache", "Songs you stream are kept so they replay instantly and survive short network drops", listOf(0 to "Off", 256 to "256 MB", 512 to "512 MB", 1024 to "1 GB", 2048 to "2 GB"), s.streamCacheMb.value) { s.streamCacheMb.set(it) }
        var cached by remember { mutableStateOf(com.sudosoc.sudoplay.playback.StreamCache.usedBytes(ctx) / 1_048_576) }
        RowCard(onClick = { com.sudosoc.sudoplay.playback.StreamCache.clear(ctx); cached = 0; toastText(ctx, "Cache cleared") }) {
            Icon(I.DeleteSweep, null, tint = t.primary)
            Spacer(Modifier.width(12.dp))
            TitleSub("Clear replay cache", "$cached MB used", Modifier.weight(1f))
        }
        SwitchRow("Offline mode", "Shows only what is on this phone, so nothing you tap needs internet", s.offlineMode.value) { s.offlineMode.set(it) }
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
internal fun BackupPage(vm: PlayerViewModel, onBack: () -> Unit) {
    val t = LocalSudo.current
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<String?>(null) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(vm.backupJson().toByteArray()) }
            vm.settings.lastBackup.set(java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date()))
            Toast.makeText(ctx, Tr.t("Backup saved"), Toast.LENGTH_SHORT).show()
        }.onFailure { Toast.makeText(ctx, Tr.t("Could not save the backup"), Toast.LENGTH_SHORT).show() }
    }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            if (text != null && runCatching { org.json.JSONObject(text).optString("app") == "SUDOPLAY" }.getOrDefault(false)) pending = text
            else Toast.makeText(ctx, Tr.t("That is not a SUDOPLAY backup"), Toast.LENGTH_SHORT).show()
        }
    }
    pending?.let { text ->
        AlertDialog(
            onDismissRequest = { pending = null }, title = { Text("Restore this backup?") },
            text = { Text("This replaces your current settings, playlists and play counts. A safety copy of how things are now is kept first, so you can undo it.") },
            confirmButton = {
                TextButton({
                    pending = null
                    // safety copy of the current state, in the app's own storage
                    runCatching { java.io.File(ctx.filesDir, "before-restore.json").writeText(vm.backupJson()) }
                    if (vm.restoreJson(text)) {
                        Toast.makeText(ctx, Tr.t("Restored — restarting…"), Toast.LENGTH_SHORT).show()
                        ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.component?.let { c -> ctx.startActivity(Intent.makeRestartActivityTask(c)) }
                        Runtime.getRuntime().exit(0)
                    } else Toast.makeText(ctx, Tr.t("That is not a SUDOPLAY backup"), Toast.LENGTH_SHORT).show()
                }) { Text("Restore") }
            },
            dismissButton = { TextButton({ pending = null }) { Text("Cancel") } },
        )
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubHeader("Backup & restore", onBack)
        SectionLabel("Backup")
        HintLine("One file with your settings, EQ scenes, playlists and play counts. Restoring restarts the app.")
        RowCard(onClick = { saver.launch("sudoplay-backup.json") }) {
            Icon(I.Collections, null, tint = t.primary)
            Spacer(Modifier.width(12.dp))
            TitleSub("Back up", if (vm.settings.lastBackup.value.isBlank()) "Settings, scenes, play counts and playlists in one file" else (if (Tr.ar) "آخر نسخة: " else "Last backup: ") + vm.settings.lastBackup.value, Modifier.weight(1f))
        }
        RowCard(onClick = { opener.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }) {
            Icon(I.RestartAlt, null, tint = t.primary)
            Spacer(Modifier.width(12.dp))
            TitleSub("Restore", "Pick a backup file; the app restarts", Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
    }
}

