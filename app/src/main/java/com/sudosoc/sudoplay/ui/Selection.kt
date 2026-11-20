package com.sudosoc.sudoplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sudosoc.sudoplay.PlayerViewModel
import com.sudosoc.sudoplay.data.Song

/** An extra thing the current screen can do with the selected songs (remove from this playlist, delete from the phone…). */
class SelAction(val label: String, val icon: @Composable () -> ImageVector, val danger: Boolean = false, val run: (List<Song>) -> Unit)

/** Which songs are ticked, across every list in the app. A screen only has to hand its list to [SongRow]. */
class SelectionState {
    private val map = mutableStateMapOf<Long, Song>()
    var pool by mutableStateOf<List<Song>>(emptyList()); private set
    var extras by mutableStateOf<List<SelAction>>(emptyList())

    val active: Boolean get() = map.isNotEmpty()
    val count: Int get() = map.size
    fun has(id: Long) = map.containsKey(id)

    /** In list order when the list is known, so "play" / "add to queue" keep the order the user sees. */
    val songs: List<Song> get() {
        val ordered = pool.filter { map.containsKey(it.id) }
        val seen = ordered.mapTo(HashSet()) { it.id }
        return ordered + map.values.filter { it.id !in seen }
    }

    fun toggle(song: Song, from: List<Song>? = null) {
        if (from != null && (!active || pool.isEmpty())) pool = from
        if (map.containsKey(song.id)) map.remove(song.id) else map[song.id] = song
        if (!active) pool = emptyList()
    }
    fun allSelected() = pool.isNotEmpty() && pool.all { map.containsKey(it.id) }
    fun selectAll() { pool.forEach { map[it.id] = it } }
    fun clear() { map.clear(); pool = emptyList() }
    fun drop(ids: Set<Long>) { ids.forEach { map.remove(it) }; if (!active) pool = emptyList() }
}

/** Registers [actions] for as long as the calling screen is shown. */
@Composable
fun SelectionActions(vm: PlayerViewModel, actions: List<SelAction>) {
    SideEffect { vm.sel.extras = actions }
    DisposableEffect(Unit) { onDispose { vm.sel.extras = emptyList() } }
}

/** Round tick shown at the start of a row while selecting. */
@Composable
fun SelectDot(on: Boolean) {
    val t = LocalSudo.current
    Box(
        Modifier.size(24.dp).clip(CircleShape).background(if (on) t.primary else t.text.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) { if (on) Icon(I.Check, null, tint = t.onAccent, modifier = Modifier.size(16.dp)) }
}

/** The bar at the bottom while songs are ticked: count, select all, and the things you do to a pile of songs. */
@Composable
fun SelectionBar(vm: PlayerViewModel) {
    val t = LocalSudo.current
    val sel = vm.sel
    if (!sel.active) return
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val songs = sel.songs
    var picker by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    val anyOnline = songs.any { it.online }
    Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp).fillMaxWidth().sudoPanel(RoundedCornerShape(24.dp)).padding(vertical = 6.dp, horizontal = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ sel.clear() }) { Icon(I.Close, "Clear selection", tint = t.text) }
            Text("${sel.count} selected", color = t.text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
            if (sel.pool.size > 1) TextButton({ if (sel.allSelected()) sel.clear() else sel.selectAll() }) { Text(if (sel.allSelected()) "None" else "Select all") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PillButton("Play", I.PlayArrow, filled = true) { vm.playList(songs, 0); sel.clear() }
            PillButton("Playlist", I.PlaylistAdd) { picker = true }
            PillButton("Next", I.SkipNext) { vm.playNextAll(songs); toastText(ctx, "Plays next"); sel.clear() }
            PillButton("Queue", I.QueueMusic) { vm.addToQueueAll(songs); toastText(ctx, "Added to queue"); sel.clear() }
            PillButton("Favorite", I.Favorite) { val n = vm.favoriteAll(songs); toastText(ctx, if (n > 0) "Added to favorites" else "Already in favorites"); sel.clear() }
            if (anyOnline) PillButton("Download", I.Download) { vm.downloadSongs(songs); sel.clear() }
            PillButton("More", I.MoreVert) { more = true }
        }
    }
    if (picker) PlaylistPickerSheet(vm, songs) { picker = false; sel.clear() }
    if (more) SudoSheet({ more = false }, title = "${sel.count} selected") { close ->
        if (!sel.allSelected() && sel.pool.size > 1) SheetAction(I.SelectAll, "Select all") { sel.selectAll(); close() }
        sel.extras.forEach { a -> SheetAction(a.icon(), a.label, danger = a.danger) { val s = songs; close(); a.run(s) } }
        SheetAction(I.Close, "Clear selection") { sel.clear(); close() }
    }
}
