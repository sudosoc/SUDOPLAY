package com.sudosoc.sudoplay.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sudosoc.sudoplay.PlayerViewModel

private data class Tab(val label: String, val icon: ImageVector)

@Composable
private fun tabsList() = listOf(
    Tab("Songs", I.MusicNote),
    Tab("Albums", I.Album),
    Tab("Browse", I.Folder),
    Tab("Playlists", I.LibraryMusic),
    Tab("Settings", I.Settings),
)

/** Position of the Now Playing sheet: 0 = hidden, 1 = fully open. Follows the finger while dragging, springs when released. */
@Stable
class SheetState(private val scope: CoroutineScope) {
    var frac by mutableFloatStateOf(0f); private set
    var heightPx = 1f
    private var job: Job? = null
    private var from = 0f

    val isOpen: Boolean get() = frac > 0.001f

    fun dragStart() { job?.cancel(); from = frac }
    /** [dyDown] is how far the finger moved down since the last event (negative = up). */
    fun dragBy(dyDown: Float) { frac = (frac - dyDown / heightPx).coerceIn(0f, 1f) }

    fun settle(vyDown: Float) {
        val target = if (from > 0.5f) { if (vyDown > 1400f || frac < 0.78f) 0f else 1f } else { if (vyDown < -900f || frac > 0.25f) 1f else 0f }
        animateTo(target, vyDown)
    }

    fun animateTo(target: Float, vyDown: Float = 0f) {
        job?.cancel()
        job = scope.launch {
            animate(frac, target, -vyDown / heightPx, spring(dampingRatio = 0.9f, stiffness = 380f)) { v, _ -> frac = v.coerceIn(0f, 1f) }
        }
    }

    fun open() = animateTo(1f)
    fun close() = animateTo(0f)
}

@Composable
fun SudoApp(vm: PlayerViewModel, granted: Boolean, onRequest: () -> Unit) {
    val iconStyle = runCatching { IconStyle.valueOf(vm.settings.iconStyle.value) }.getOrDefault(IconStyle.Rounded)
    CompositionLocalProvider(LocalIconStyle provides iconStyle) {
    SudoPlayTheme(vm.themeId) {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var detail by remember { mutableStateOf<Detail?>(null) }
        val scope = rememberCoroutineScope()
        val sheet = remember { SheetState(scope) }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            sheet.heightPx = with(LocalDensity.current) { maxHeight.toPx() }

            SudoBackground(vm.current) {
                Column(Modifier.fillMaxSize().systemBarsPadding()) {
                    Box(Modifier.weight(1f)) {
                        when {
                            !granted -> PermissionGate(onRequest)
                            detail != null -> DetailScreen(vm, detail!!) { detail = null }
                            else -> when (tab) {
                                0 -> SongsTab(vm)
                                1 -> AlbumsTab(vm) { detail = it }
                                2 -> BrowseTab(vm) { detail = it }
                                3 -> PlaylistsTab(vm) { detail = it }
                                else -> SettingsTab(vm)
                            }
                        }
                    }
                    if (vm.current != null) MiniPlayer(vm, sheet)
                    BottomBar(tab) { tab = it; detail = null }
                }
                // Later-registered BackHandlers win, so Now Playing is declared last.
                BackHandler(enabled = detail != null) { detail = null }

                if (sheet.isOpen) {
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = sheet.frac * 0.55f }.background(Color.Black))
                    Box(Modifier.fillMaxSize().graphicsLayer { translationY = (1f - sheet.frac) * size.height }) {
                        NowPlayingScreen(vm, sheet)
                    }
                }
                BackHandler(enabled = sheet.isOpen) { sheet.close() }
            }
            vm.playlistTarget?.let { PlaylistPickerDialog(vm, it) { vm.playlistTarget = null } }
        }
    }
    }
}

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val t = LocalSudo.current
    Row(
        Modifier.padding(horizontal = 10.dp, vertical = 8.dp).fillMaxWidth().sudoPanel(RoundedCornerShape(26.dp)).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        tabsList().forEachIndexed { i, tb ->
            val on = i == selected
            Column(
                Modifier.clickable { onSelect(i) }.padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(tb.icon, null, tint = if (on) t.primary else t.text.copy(alpha = 0.6f))
                Text(tb.label, color = if (on) t.primary else t.text.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun PermissionGate(onRequest: () -> Unit) {
    val t = LocalSudo.current
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(I.LibraryMusic, null, tint = t.primary, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("SUDOPLAY needs access to your music", color = t.text, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        PillButton("Grant access", I.Check, filled = true, onClick = onRequest)
    }
}
