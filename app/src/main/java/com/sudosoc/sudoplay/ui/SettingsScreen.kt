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

const val APP_VERSION = "0.17.1"

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
internal fun SubHeader(title: String, onBack: () -> Unit) {
    val t = LocalSudo.current
    Row(Modifier.padding(top = 6.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack) { Icon(I.ArrowBack, null, tint = t.text) }
        Text(title, color = t.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun SectionLabel(text: String) {
    val t = LocalSudo.current
    Text(text.uppercase(), color = t.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp))
}

@Composable
internal fun RowCard(onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val m = Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().sudoPanel(shape)
    Row(
        (if (onClick != null) m.clickable(onClick = onClick) else m).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, content = content,
    )
}

/** A short grey line under a row, explaining what the option above it does. */
@Composable
internal fun HintLine(text: String) {
    Text(text, color = LocalSudo.current.text.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 22.dp, vertical = 2.dp))
}

@Composable
internal fun TitleSub(title: String, sub: String?, modifier: Modifier = Modifier) {
    val t = LocalSudo.current
    Column(modifier) {
        Text(title, color = t.text, fontWeight = FontWeight.SemiBold)
        if (sub != null) Text(sub, color = t.text.copy(alpha = 0.62f), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    RowCard(onClick = { onChange(!checked) }) {
        TitleSub(title, sub, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
internal fun SliderRow(title: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onDone: (() -> Unit)? = null, onChange: (Float) -> Unit) {
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
internal fun NavRow(icon: ImageVector, title: String, sub: String?, onClick: () -> Unit) {
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
internal fun HomePage(vm: PlayerViewModel, onOpen: (String) -> Unit) {
    val t = LocalSudo.current
    val ctx = LocalContext.current
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
        // search every settings page
        var q by rememberSaveable { mutableStateOf("") }
        OutlinedTextField(
            q, { q = it }, singleLine = true, placeholder = { Text("Search settings") },
            leadingIcon = { Icon(I.Search, null, tint = t.text.copy(alpha = 0.6f)) },
            trailingIcon = { if (q.isNotEmpty()) IconButton({ q = "" }) { Icon(I.Close, null, tint = t.text.copy(alpha = 0.6f)) } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        )
        if (q.isNotBlank()) {
            val hits = SETTINGS_INDEX.filter { (title, words, _) -> (title + " " + words + " " + Tr.t(title)).lowercase().contains(q.trim().lowercase()) }
            if (hits.isEmpty()) HintLine("Nothing matches")
            hits.forEach { (title, _, page) -> NavRow(I.Search, title, null) { q = ""; if (page == "report") reportProblem(ctx) else onOpen(page) } }
            Spacer(Modifier.height(24.dp)); return@Column
        }
        SectionLabel("Look & feel")
        RowCard {
            Column(Modifier.weight(1f)) {
                Text("Language", color = t.text, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("auto" to "Automatic", "en" to "English", "ar" to "العربية").forEach { (v, label) ->
                        val on = st.language.value == v
                        Text(
                            label, color = if (on) t.onAccent else t.text, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.clip(RoundedCornerShape(50))
                                .then(if (on) Modifier.background(Brush.horizontalGradient(listOf(t.primary, t.secondary))) else Modifier.border(1.dp, t.text.copy(alpha = 0.25f), RoundedCornerShape(50)))
                                .clickable { st.language.set(v) }.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
        RowCard {
            OutlinedTextField(
                st.userName.value, { st.userName.set(it.take(24)) }, singleLine = true, label = { Text("Your name") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
        NavRow(I.Extension, "Report a problem", "Send me the crash log and your phone model") { reportProblem(ctx) }
        BigSection(I.PlayCircle, "About SUDOPLAY", "عن التطبيق", "What it does, how it treats your data, and who made it") { onOpen("about") }
        Text("SUDOPLAY $APP_VERSION • by SUDOSOC", color = t.text.copy(alpha = 0.4f), style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(20.dp))
    }
}

@Composable
internal fun BigSection(icon: ImageVector, title: String, arabic: String, desc: String, onClick: () -> Unit) {
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



internal val SETTINGS_INDEX = listOf(
    Triple("App themes", "theme colors dark light amoled ثيم ألوان", "app_themes"),
    Triple("Player themes", "player look now playing شكل المشغل", "player_themes"),
    Triple("App logo & icon", "logo launcher icon شعار أيقونة", "logos"),
    Triple("Fonts & colors", "font text size accent color خط حجم لون", "fonts"),
    Triple("Seek bar style", "seek progress bar شريط التقدم", "seek_styles"),
    Triple("Icon style", "icons أيقونات", "icon_styles"),
    Triple("Player buttons", "buttons arrange أزرار", "player_buttons"),
    Triple("Navigation bar", "tabs bottom bar home تبويبات", "nav_bar"),
    Triple("Gestures & feedback", "swipe double tap gesture vibration haptic إيماءات اهتزاز", "props"),
    Triple("Gesture editor", "gesture actions", "gesture_editor"),
    Triple("Sound & playback", "equalizer crossfade replaygain skip silence bass صوت ايكوالايزر", "sound"),
    Triple("Car & launch", "car autoplay keep screen on سيارة", "car"),
    Triple("Library & files", "hidden folders lyrics covers short tracks مكتبة", "extras"),
    Triple("Duplicate finder", "duplicates مكرر", "dupes"),
    Triple("Your stats", "stats statistics listening إحصائيات", "stats"),
    Triple("Hidden folders", "hide folder مجلد", "hidden_folders"),
    Triple("Online & downloads", "download wifi suggestions discovery تحميل أونلاين", "online"),
    Triple("Network log", "network requests privacy شبكة خصوصية", "netlog"),
    Triple("Backup & restore", "backup restore export import نسخة احتياطية", "backup"),
    Triple("About SUDOPLAY", "about developer version عن", "about"),
    Triple("Report a problem", "crash bug log feedback مشكلة أعطال", "report"),
)

/** Shares the saved crash log plus the phone model, so a bug report is one tap. */
internal fun reportProblem(ctx: android.content.Context) {
    val log = com.sudosoc.sudoplay.data.CrashLog.read(ctx)
    val body = com.sudosoc.sudoplay.data.CrashLog.deviceLine(ctx) + "\n\n" + (log ?: "(no crashes recorded)")
    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_EMAIL, arrayOf("ceo@sudosoc.com"))
        .putExtra(Intent.EXTRA_SUBJECT, "SUDOPLAY problem report").putExtra(Intent.EXTRA_TEXT, body)
    runCatching { ctx.startActivity(Intent.createChooser(i, Tr.t("Report a problem")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
