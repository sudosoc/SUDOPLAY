package com.sudosoc.sudoplay.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sudosoc.sudoplay.PlayerViewModel
import com.sudosoc.sudoplay.data.Song
import com.sudosoc.sudoplay.online.DownloadCenter

/** Everything downloaded through SUDOPLAY (Music/SUDOPLAY), with the running downloads on top. Select several to add them to a playlist or delete them. */
@Composable
fun DownloadsTab(vm: PlayerViewModel) {
    val t = LocalSudo.current
    val ctx = LocalContext.current
    val list = remember(vm.rawSongs, vm.olib.version) { vm.downloadedSongs() }
    var selecting by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(setOf<Long>()) }
    var confirmDelete by remember { mutableStateOf<List<Song>?>(null) }
    var toPlaylist by remember { mutableStateOf<List<Song>?>(null) }
    var editing by remember { mutableStateOf<Song?>(null) }
    LaunchedEffect(list) { picked = picked.filter { id -> list.any { it.id == id } }.toSet() }
    val totalMin = list.sumOf { it.durationMs } / 60_000

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Downloads")
        Text(
            if (list.isEmpty()) "Songs you download are saved on your phone and work offline"
            else "${list.size} songs • ${if (totalMin >= 60) "${totalMin / 60} h ${totalMin % 60} min" else "$totalMin min"} • works offline",
            color = t.text.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 18.dp),
        )
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!selecting) {
                PillButton("Play", I.PlayArrow, filled = true) { vm.play(list, 0, false) }
                PillButton("Shuffle", I.Shuffle) { vm.shufflePlay(list) }
                if (list.isNotEmpty()) PillButton("Select", I.SelectAll) { selecting = true }
            } else {
                PillButton(if (picked.size == list.size) "None" else "All", I.SelectAll, filled = true) { picked = if (picked.size == list.size) emptySet() else list.map { it.id }.toSet() }
                PillButton("Playlist", I.PlaylistAdd) { toPlaylist = list.filter { it.id in picked } }
                PillButton("Delete", I.Delete) { confirmDelete = list.filter { it.id in picked } }
                PillButton("Done", I.Check) { selecting = false; picked = emptySet() }
            }
        }
        if (selecting) Text("${picked.size} selected", color = t.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 18.dp))

        LazyColumn(Modifier.weight(1f)) {
            val jobs = DownloadCenter.jobs.filter { it.state != "Done" }
            if (jobs.isNotEmpty()) {
                item("jobs_head") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (jobs.any { it.active }) "In progress" else "Finished", color = t.text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (jobs.any { !it.active }) TextButton({ DownloadCenter.clearFinished() }) { Text("Clear finished") }
                    }
                }
                items(jobs, key = { "job_" + it.id }) { j ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(j.title, color = t.text, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            when {
                                j.active && j.progress > 0.01f -> LinearProgressIndicator(progress = { j.progress }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), color = t.primary)
                                j.active -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), color = t.primary)
                                else -> Text(
                                    if (j.state == "Failed") "Failed: ${j.error ?: "unknown error"}" else j.state,
                                    color = if (j.state == "Failed") MaterialTheme.colorScheme.error else t.text.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodySmall, maxLines = 2,
                                )
                            }
                        }
                        if (j.active) IconButton({ DownloadCenter.cancel(j) }) { Icon(I.Close, null, tint = t.text.copy(alpha = 0.7f)) }
                    }
                }
            }
            if (list.isEmpty() && jobs.isEmpty()) item("empty") {
                EmptyState("Nothing downloaded yet. Tap Download on any online song, or open an imported playlist and download it all.")
            }
            items(list, key = { "dl_" + it.id }) { s ->
                if (selecting) {
                    Row(
                        Modifier.fillMaxWidth().clickable { picked = if (s.id in picked) picked - s.id else picked + s.id }.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(s.id in picked, { picked = if (it) picked + s.id else picked - s.id })
                        Artwork(s, Modifier.size(44.dp), 128, 10.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.title, color = t.text, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            Text(s.artist, color = t.text.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else SongRow(s, vm, onClick = { vm.play(list, list.indexOf(s)) }) { dismiss ->
                    DropdownMenuItem(text = { Text("Fix name, cover & lyrics…") }, onClick = { editing = s; dismiss() })
                    DropdownMenuItem(text = { Text("Delete from phone") }, onClick = { confirmDelete = listOf(s); dismiss() })
                }
            }
        }
    }

    confirmDelete?.let { del ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(if (del.size == 1) "Delete this song?" else "Delete ${del.size} songs?") },
            text = { Text("The file${if (del.size == 1) "" else "s"} will be removed from your phone. Playlists keep ${if (del.size == 1) "it" else "them"} as online songs, so you can download again later.") },
            confirmButton = {
                TextButton({
                    confirmDelete = null
                    vm.deleteDownloads(del) { n ->
                        android.widget.Toast.makeText(ctx, if (n == del.size) "Deleted $n" else "Deleted $n of ${del.size} (Android only lets the app delete files it saved itself)", android.widget.Toast.LENGTH_LONG).show()
                        picked = emptySet(); selecting = false
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton({ confirmDelete = null }) { Text("Cancel") } },
        )
    }
    editing?.let { song ->
        var title by remember(song.id) { mutableStateOf(song.title) }
        var artist by remember(song.id) { mutableStateOf(song.artist.takeUnless { it.equals("<unknown>", true) }.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = null }, title = { Text("Fix details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, singleLine = true, label = { Text("Title") })
                    OutlinedTextField(artist, { artist = it }, singleLine = true, label = { Text("Artist") })
                    Text("Saves the name, then looks up the cover and lyrics by it.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton({
                    val s0 = song; editing = null
                    vm.fixDownload(s0, title.trim().ifBlank { s0.title }, artist.trim()) { android.widget.Toast.makeText(ctx, it, android.widget.Toast.LENGTH_LONG).show() }
                }) { Text("Save") }
            },
            dismissButton = { TextButton({ editing = null }) { Text("Cancel") } },
        )
    }
    toPlaylist?.let { sel -> MultiPlaylistPicker(vm, sel) { toPlaylist = null; selecting = false; picked = emptySet() } }
}

/** Add several songs to a playlist (or a new one) in one go. */
@Composable
fun MultiPlaylistPicker(vm: PlayerViewModel, songs: List<Song>, onDismiss: () -> Unit) {
    var creating by remember { mutableStateOf(false) }
    val ids = songs.map { it.id }
    if (creating) {
        NameDialog("New playlist", onDismiss = { creating = false }) { n -> vm.store.addSongs(vm.store.create(n).id, ids); onDismiss() }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Add ${songs.size} to playlist") },
        text = {
            LazyColumn {
                item("new") {
                    Text("＋ New playlist…", Modifier.fillMaxWidth().clickable { creating = true }.padding(vertical = 12.dp), fontWeight = FontWeight.SemiBold)
                }
                items(vm.store.items.toList(), key = { it.id }) { p ->
                    Text(p.name, Modifier.fillMaxWidth().clickable { vm.store.addSongs(p.id, ids); onDismiss() }.padding(vertical = 12.dp))
                }
            }
        },
        confirmButton = {}, dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
