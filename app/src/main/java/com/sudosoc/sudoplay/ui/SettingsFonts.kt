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

// ---------------------------------------------------------------- network log
@Composable
internal fun NetLogPage(onBack: () -> Unit) {
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
internal fun FontsPage(vm: PlayerViewModel, onBack: () -> Unit) {
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
internal fun ColorPicker(current: String, hexText: String, onHexText: (String) -> Unit, onPick: (String) -> Unit) {
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
internal fun LogosPage(vm: PlayerViewModel, onBack: () -> Unit) {
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
                                Toast.makeText(ctx, com.sudosoc.sudoplay.ui.Tr.t("Icon changed — the launcher may take a moment to update"), Toast.LENGTH_SHORT).show()
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

