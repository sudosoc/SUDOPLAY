package com.sudosoc.sudoplay.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
@OptIn(ExperimentalFoundationApi::class)
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
                        val rowId = remember(tr) { vm.previewId(tr) }
                        val picked = vm.sel.has(rowId)
                        val selecting = vm.sel.active
                        fun tick() { val all = vm.previewSongs(p); all.firstOrNull { it.id == rowId }?.let { vm.haptics.click(); vm.sel.toggle(it, all) } }
                        Row(
                            Modifier.fillMaxWidth()
                                .background(if (picked) t.primary.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
                                .combinedClickable(onClick = { if (selecting) tick() else vm.playList(vm.previewSongs(p), i) }, onLongClick = { tick() })
                                .padding(horizontal = 16.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (selecting) { SelectDot(picked); Spacer(Modifier.width(12.dp)) }
                            ArtImage(thumb, Modifier.size(48.dp), 10.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(tr.title, color = t.text, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(artistLabel(tr.artist), tr.durationSec.takeIf { it > 0 }?.let { fmt(it * 1000) }).joinToString("  •  "),
                                    color = t.text.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (!selecting) IconButton({ menu = true }) { Icon(I.MoreVert, "More options", tint = t.text.copy(alpha = 0.7f)) }
                            if (menu) SudoSheet(
                                { menu = false }, title = tr.title, subtitle = artistLabel(tr.artist),
                                header = { ArtImage(thumb, Modifier.size(52.dp), 12.dp) },
                            ) { close ->
                                SheetAction(I.SkipNext, "Play next") { close(); vm.previewSongs(p).getOrNull(i)?.let { s -> vm.playNext(s); toastText(ctx, "Plays next") } }
                                SheetAction(I.QueueMusic, "Add to queue") { close(); vm.previewSongs(p).getOrNull(i)?.let { s -> vm.addToQueue(s); toastText(ctx, "Added to queue") } }
                                SheetAction(I.Download, "Download") { close(); vm.previewSongs(p).getOrNull(i)?.let { s -> vm.downloadSongs(listOf(s)) } }
                                SheetAction(I.PlaylistAdd, "Add to playlist…") { close(); vm.previewSongs(p).getOrNull(i)?.let { s -> vm.playlistTarget = s } }
                            }
                        }
                    }
                }
            }
        }
    }
}
