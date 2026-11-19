package com.sudosoc.sudoplay.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sudosoc.sudoplay.PlayerViewModel
import com.sudosoc.sudoplay.data.Song
import com.sudosoc.sudoplay.playback.ArtLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A public playlist from Discover, opened like any list. Nothing is added to the library until the user taps "Add to my playlists". */
@Composable
fun PlaylistPreviewScreen(vm: PlayerViewModel, p: PlayerViewModel.PlaylistPreview) {
    val t = LocalSudo.current
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(top = 6.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ vm.closePreview() }) { Icon(I.ArrowBack, "back", tint = t.text) }
            Text(p.title.ifBlank { "Playlist" }, color = t.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when {
            p.loading -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = t.primary) }
            p.error != null -> EmptyState("Couldn't open this playlist (${p.error})", "Back") { vm.closePreview() }
            p.tracks.isEmpty() -> EmptyState("This playlist is empty", "Back") { vm.closePreview() }
            else -> {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Play", I.PlayArrow, filled = true) { vm.playList(vm.previewSongs(p), 0) }
                    PillButton("Shuffle", I.Shuffle) { vm.playList(vm.previewSongs(p), 0, true) }
                }
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(if (p.added) "Added ✓" else "Add to my playlists", if (p.added) I.Check else I.PlaylistAdd) { vm.previewAdd(p) }
                    PillButton("Download all", I.Download) { vm.downloadSongs(vm.previewSongs(p)) }
                }
                Text("${p.tracks.size} songs • tap one to play • nothing is saved until you add it", color = t.text.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(p.tracks, key = { i, _ -> i }) { i, tr ->
                        var menu by remember { mutableStateOf(false) }
                        val thumb by produceState<android.graphics.Bitmap?>(null, tr.thumb) {
                            value = tr.thumb?.let { u -> withContext(Dispatchers.IO) { ArtLoader.load(ctx, android.net.Uri.parse(u), 128) } }
                        }
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.playList(vm.previewSongs(p), i) }.padding(horizontal = 16.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ArtImage(thumb, Modifier.size(48.dp), 10.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(tr.title, color = t.text, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(artistLabel(tr.artist), tr.durationSec.takeIf { it > 0 }?.let { fmt(it * 1000) }).joinToString("  •  "),
                                    color = t.text.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Box {
                                IconButton({ menu = true }) { Icon(I.MoreVert, null, tint = t.text.copy(alpha = 0.7f)) }
                                SudoMenu(menu, { menu = false }) {
                                    DropdownMenuItem(text = { Text("Play next") }, onClick = { menu = false; vm.previewSongs(p).getOrNull(i)?.let { s -> vm.playNext(s); android.widget.Toast.makeText(ctx, "Plays next", android.widget.Toast.LENGTH_SHORT).show() } })
                                    DropdownMenuItem(text = { Text("Add to queue") }, onClick = { menu = false; vm.previewSongs(p).getOrNull(i)?.let { s -> vm.addToQueue(s); android.widget.Toast.makeText(ctx, "Added to queue", android.widget.Toast.LENGTH_SHORT).show() } })
                                    DropdownMenuItem(text = { Text("Download") }, onClick = { menu = false; vm.previewSongs(p).getOrNull(i)?.let { s -> vm.downloadSongs(listOf(s)) } })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
