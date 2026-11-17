package com.sudosoc.sudoplay.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sudosoc.sudoplay.PlayerViewModel

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Songs", Icons.Rounded.MusicNote),
    Tab("Albums", Icons.Rounded.Album),
    Tab("Browse", Icons.Rounded.Folder),
    Tab("Playlists", Icons.Rounded.LibraryMusic),
    Tab("Style", Icons.Rounded.Palette),
)

@Composable
fun SudoApp(vm: PlayerViewModel, granted: Boolean, onRequest: () -> Unit) {
    SudoPlayTheme(vm.themeId) {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var detail by remember { mutableStateOf<Detail?>(null) }
        var showNow by rememberSaveable { mutableStateOf(false) }

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
                            else -> StyleTab(vm)
                        }
                    }
                }
                if (vm.current != null) MiniPlayer(vm) { showNow = true }
                BottomBar(tab) { tab = it; detail = null }
            }

            // Later-registered BackHandlers win, so Now Playing is declared last.
            BackHandler(enabled = detail != null) { detail = null }
            AnimatedVisibility(showNow, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                NowPlayingScreen(vm) { showNow = false }
            }
            BackHandler(enabled = showNow) { showNow = false }
        }

        vm.playlistTarget?.let { PlaylistPickerDialog(vm, it) { vm.playlistTarget = null } }
    }
}

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val t = LocalSudo.current
    Row(
        Modifier.padding(horizontal = 10.dp, vertical = 8.dp).fillMaxWidth().sudoPanel(RoundedCornerShape(26.dp)).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        tabs.forEachIndexed { i, tb ->
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
        Icon(Icons.Rounded.LibraryMusic, null, tint = t.primary, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("SUDOPLAY needs access to your music", color = t.text, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        PillButton("Grant access", Icons.Rounded.Check, filled = true, onClick = onRequest)
    }
}
