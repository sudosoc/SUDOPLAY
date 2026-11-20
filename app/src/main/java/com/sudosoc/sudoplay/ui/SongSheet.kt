package com.sudosoc.sudoplay.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sudosoc.sudoplay.PlayerViewModel
import com.sudosoc.sudoplay.data.Song

fun toastText(ctx: Context, msg: String, long: Boolean = false) =
    Toast.makeText(ctx, Tr.t(msg), if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()

/** Everything you can do with one song, as a bottom sheet. [extra] adds screen-specific rows (e.g. remove from this playlist). */
@Composable
fun SongSheet(vm: PlayerViewModel, song: Song, onSelect: () -> Unit, onDismiss: () -> Unit, extra: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null) {
    val ctx = LocalContext.current
    val fav = vm.store.isFav(song.id)
    SudoSheet(
        onDismiss, title = song.title, subtitle = artistLabel(song.artist),
        header = { Artwork(song, Modifier.size(52.dp), 128, 12.dp) },
    ) { close ->
        SheetAction(I.SkipNext, "Play next") { vm.playNext(song); toastText(ctx, "Plays next"); close() }
        SheetAction(I.QueueMusic, "Add to queue") { vm.addToQueue(song); toastText(ctx, "Added to queue"); close() }
        SheetAction(if (fav) I.Favorite else I.FavoriteBorder, if (fav) "Remove from favorites" else "Add to favorites", selected = fav) {
            vm.store.toggleFav(song.id); toastText(ctx, if (fav) "Removed from favorites" else "Added to favorites"); close()
        }
        SheetAction(I.PlaylistAdd, "Add to playlist…") { close(); vm.playlistTarget = song }
        if (song.online) SheetAction(I.Download, "Download") { vm.downloadSongs(listOf(song)); close() }
        SheetAction(I.SelectAll, "Select") { close(); onSelect() }
        if (!song.online) {
            SheetAction(I.Share, "Share") { shareSong(ctx, song); close() }
            SheetAction(I.Info, "Song info") { close(); vm.infoSong = song }
            SheetAction(I.Movie, "Make video clip") { close(); vm.clipSong = song }
            SheetAction(I.Search, "Find online") { vm.findOnline(song); close() }
        }
        extra?.invoke(this, close)
    }
}

/** Pick a playlist (or make one) for one or many songs. */
@Composable
fun PlaylistPickerSheet(vm: PlayerViewModel, songs: List<Song>, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var creating by remember { mutableStateOf(false) }
    fun addTo(pid: String) {
        var name = ""; var added = 0
        songs.forEach { s -> val (n, already) = vm.addToPlaylist(pid, s); name = n; if (!already) added++ }
        toastText(ctx, when {
            songs.size == 1 && added == 0 -> "Already in “$name”"
            songs.size == 1 -> "Added to “$name”"
            added == 0 -> "All of them are already in “$name”"
            else -> "Added $added to “$name”"
        })
    }
    if (creating) {
        NameDialog("New playlist", onDismiss = { creating = false }) { n -> addTo(vm.store.create(n).id); creating = false; onDone() }
        return
    }
    SudoSheet(onDone, title = if (songs.size == 1) "Add to playlist" else "Add ${songs.size} songs to playlist") { close ->
        SheetAction(I.Add, "New playlist…") { creating = true }
        vm.store.items.toList().forEach { p ->
            val has = songs.size == 1 && songs[0].id in p.songIds
            SheetAction(if (p.id == com.sudosoc.sudoplay.data.FAV_ID) I.Favorite else I.QueueMusic, "${p.name}  •  ${p.songIds.size}", selected = has) { addTo(p.id); close() }
        }
    }
}
