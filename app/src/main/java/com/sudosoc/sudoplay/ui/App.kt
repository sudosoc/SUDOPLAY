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
import androidx.compose.ui.draw.clip
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

private data class Tab(val index: Int, val label: String, val icon: ImageVector)

@Composable
private fun tabsList(vm: PlayerViewModel): List<Tab> {
    val st = vm.settings
    return buildList {
        if (st.navSongs.value) add(Tab(0, "Songs", I.MusicNote))
        if (st.navAlbums.value) add(Tab(1, "Albums", I.Album))
        if (st.navBrowse.value) add(Tab(2, "Browse", I.Folder))
        if (st.navPlaylists.value) add(Tab(3, "Playlists", I.LibraryMusic))
        add(Tab(4, "Settings", I.Settings))
    }
}

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
    val coverArt by rememberArt(vm.current, 64)
    SudoPlayTheme(vm.themeId, cover = if (vm.settings.coverColors.value) coverArt else null) {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var detail by remember { mutableStateOf<Detail?>(null) }
        val scope = rememberCoroutineScope()
        val sheet = remember { SheetState(scope) }
        val cfg = androidx.compose.ui.platform.LocalConfiguration.current
        LaunchedEffect(Unit) {
            if (!vm.carChecked) {
                vm.carChecked = true
                val start = vm.settings.carStart.value
                val bigLandscape = cfg.smallestScreenWidthDp >= 560 && cfg.screenWidthDp > cfg.screenHeightDp
                val carUi = (cfg.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) == android.content.res.Configuration.UI_MODE_TYPE_CAR
                if (start == "always" || (start == "auto" && (bigLandscape || carUi))) vm.carMode = true
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            sheet.heightPx = with(LocalDensity.current) { maxHeight.toPx() }

            SudoBackground(vm.current) {
                val tabs = tabsList(vm)
                val cur = if (tabs.any { it.index == tab }) tab else tabs.first().index
                val pos = vm.settings.navPos.value
                val nav: @Composable () -> Unit = {
                    NavBar(tabs, cur, vertical = pos == "left" || pos == "right") { tab = it; detail = null; vm.haptics.tick() }
                }
                val body: @Composable ColumnScope.() -> Unit = {
                    Box(Modifier.weight(1f)) {
                        when {
                            !granted -> PermissionGate(onRequest)
                            detail != null -> DetailScreen(vm, detail!!) { detail = null }
                            else -> when (cur) {
                                0 -> SongsTab(vm)
                                1 -> AlbumsTab(vm) { detail = it }
                                2 -> BrowseTab(vm) { detail = it }
                                3 -> PlaylistsTab(vm) { detail = it }
                                else -> SettingsTab(vm)
                            }
                        }
                    }
                    if (vm.current != null) MiniPlayer(vm, sheet)
                }
                when (pos) {
                    "top" -> Column(Modifier.fillMaxSize().systemBarsPadding()) { nav(); body() }
                    "left" -> Row(Modifier.fillMaxSize().systemBarsPadding()) { nav(); Column(Modifier.weight(1f).fillMaxHeight(), content = body) }
                    "right" -> Row(Modifier.fillMaxSize().systemBarsPadding()) { Column(Modifier.weight(1f).fillMaxHeight(), content = body); nav() }
                    else -> Column(Modifier.fillMaxSize().systemBarsPadding()) { body(); nav() }
                }
                LaunchedEffect(vm.wantPlayer, vm.current) {
                    if (vm.wantPlayer && vm.current != null) { sheet.open(); vm.playerOpened() }
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
                if (vm.carMode) CarScreen(vm) { vm.carMode = false }
                vm.infoSong?.let { SongInfoDialog(vm, it) { vm.infoSong = null } }
                vm.clipSong?.let { ClipScreen(vm, it) { vm.clipSong = null } }
            }
            vm.playlistTarget?.let { PlaylistPickerDialog(vm, it) { vm.playlistTarget = null } }
        }
    }
    }
}

@Composable
private fun NavBar(tabs: List<Tab>, selected: Int, vertical: Boolean, onSelect: (Int) -> Unit) {
    val t = LocalSudo.current
    val items: @Composable (Tab) -> Unit = { tb ->
        val on = tb.index == selected
        Column(
            Modifier.clip(RoundedCornerShape(16.dp)).clickable { onSelect(tb.index) }.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(tb.icon, null, tint = if (on) t.primary else t.text.copy(alpha = 0.6f))
            Text(tb.label, color = if (on) t.primary else t.text.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
        }
    }
    if (vertical) {
        Column(
            Modifier.padding(horizontal = 6.dp, vertical = 8.dp).fillMaxHeight().sudoPanel(RoundedCornerShape(26.dp)).padding(horizontal = 4.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally,
        ) { tabs.forEach { items(it) } }
    } else {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp).fillMaxWidth().sudoPanel(RoundedCornerShape(26.dp)).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) { tabs.forEach { items(it) } }
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
