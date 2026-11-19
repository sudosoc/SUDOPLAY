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

// ---------------------------------------------------------------- about
private data class FeatureGroup(val icon: ImageVector, val title: String, val lines: List<String>)

@Composable
internal fun AboutPage(vm: PlayerViewModel, onBack: () -> Unit) {
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

internal fun openUrl(ctx: android.content.Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** The developer card: photo, the SUDOSOC wordmark, name in small gray, and three links. */
@Composable
internal fun DevCard(onDismiss: () -> Unit) {
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
internal fun DevLink(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
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
