package com.sudosoc.sudoplay.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    val all = remember(vm.rawSongs, vm.olib.version) { vm.downloadedSongs() }
    var query by rememberSaveable { mutableStateOf("") }
    val list = remember(all, query) {
        val q = foldForSearch(query)
        if (q.isBlank()) all else all.filter { foldForSearch(it.title).contains(q) || foldForSearch(it.artist).contains(q) }
    }
    var confirmDelete by remember { mutableStateOf<List<Song>?>(null) }
    var editing by remember { mutableStateOf<Song?>(null) }
    SelectionActions(vm, listOf(SelAction("Delete from phone", { I.Delete }, danger = true) { confirmDelete = it }))
    val totalMin = list.sumOf { it.durationMs } / 60_000
    val mb = remember(list) { list.sumOf { s -> runCatching { java.io.File(s.path).length() }.getOrDefault(0L) } / 1_048_576 }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Downloads")
        Text(
            if (list.isEmpty()) "Songs you download are saved on your phone and work offline"
            else "${list.size} songs • ${if (totalMin >= 60) "${totalMin / 60} h ${totalMin % 60} min" else "$totalMin min"} • works offline" + if (mb > 0) " • ${if (mb >= 1024) "%.1f GB".format(mb / 1024.0) else "$mb MB"}" else "",
            color = t.text.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 18.dp),
        )
        if (all.size > 8) SearchField(query) { query = it }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Play", I.PlayArrow, filled = true) { vm.play(list, 0, false) }
            PillButton("Shuffle", I.Shuffle) { vm.shufflePlay(list) }
        }
        if (list.isNotEmpty() && !vm.sel.active) Text("Tip: touch and hold a song to select several", color = t.text.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 18.dp))

        LazyColumn(Modifier.weight(1f)) {
            val jobs = DownloadCenter.jobs.filter { it.state != "Done" }
            if (jobs.isNotEmpty()) {
                item("jobs_head") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (jobs.any { it.active }) "In progress" else "Finished", color = t.text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (jobs.count { it.active } >= 2) TextButton({ jobs.filter { it.active }.forEach { DownloadCenter.cancel(it) } }) { Text("Cancel all") }
                        if (jobs.any { it.state == "Failed" }) TextButton({
                            jobs.filter { it.state == "Failed" }.forEach { j -> DownloadCenter.jobs.remove(j); DownloadCenter.enqueue(j.title, j.artist, j.option, j.playlistId, null, j.origIds) }
                        }) { Text("Retry failed") }
                        if (jobs.any { !it.active }) TextButton({ DownloadCenter.clearFinished() }) { Text("Clear") }
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
                        if (j.state == "Failed") IconButton({ DownloadCenter.jobs.remove(j); DownloadCenter.enqueue(j.title, j.artist, j.option, j.playlistId, null, j.origIds) }) { Icon(I.Refresh, "retry", tint = t.primary) }
                        if (j.active) IconButton({ DownloadCenter.cancel(j) }) { Icon(I.Close, null, tint = t.text.copy(alpha = 0.7f)) }
                    }
                }
            }
            if (list.isEmpty() && all.isNotEmpty()) item("nomatch") { EmptyState("Nothing matches “$query”", "Clear search") { query = "" } }
            if (all.isEmpty() && jobs.isEmpty()) item("empty") {
                EmptyState("Nothing downloaded yet. Tap Download on any online song, or open an imported playlist and download it all.")
            }
            items(list, key = { "dl_" + it.id }) { s ->
                SongRow(s, vm, onClick = { vm.play(list, list.indexOf(s)) }, pool = list) { close ->
                    SheetAction(I.Edit, "Fix name, cover & lyrics…") { close(); editing = s }
                    SheetAction(I.Delete, "Delete from phone", danger = true) { close(); confirmDelete = listOf(s) }
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
                        vm.sel.drop(del.map { it.id }.toSet())
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
}
