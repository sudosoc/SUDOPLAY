package com.sudosoc.sudoplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sudosoc.sudoplay.PlayerViewModel
import com.sudosoc.sudoplay.data.FAV_ID
import com.sudosoc.sudoplay.data.Smart
import com.sudosoc.sudoplay.data.Song

sealed interface Detail {
    data class Album(val name: String) : Detail
    data class Artist(val name: String) : Detail
    data class Pl(val id: String) : Detail
    data class Folder(val path: String) : Detail
    data class SmartList(val kind: Smart) : Detail
}

@Composable
fun ScreenTitle(text: String) {
    val t = LocalSudo.current
    Text(text, color = t.text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 6.dp))
}

@Composable
fun PillButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, filled: Boolean = false, onClick: () -> Unit) {
    val t = LocalSudo.current
    val shape = RoundedCornerShape(50)
    val base = Modifier.clip(shape)
    val m = if (filled) base.background(Brush.linearGradient(listOf(t.primary, t.secondary))) else base.sudoPanel(shape)
    val fg = if (filled) t.onAccent else t.text
    Row(m.clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = fg, fontWeight = FontWeight.Medium)
    }
}

// ---------------- Songs ----------------
@Composable
fun SongsTab(vm: PlayerViewModel) {
    val t = LocalSudo.current
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableIntStateOf(0) }
    var sortMenu by remember { mutableStateOf(false) }
    val sortNames = listOf("Title", "Artist", "Recently added", "Most played", "Longest")
    val list = remember(vm.songs, query, sort) {
        val q = foldForSearch(query)
        val f = if (q.isBlank()) vm.songs else vm.songs.filter {
            foldForSearch(it.title).contains(q) || foldForSearch(it.artist).contains(q) || foldForSearch(it.album).contains(q)
        }
        when (sort) {
            1 -> f.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
            2 -> f.sortedByDescending { it.dateAdded }
            3 -> f.sortedByDescending { vm.plays[it.id] ?: 0 }
            4 -> f.sortedByDescending { it.durationMs }
            else -> f.sortedBy { it.title.lowercase() }
        }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Songs")
        SearchField(query) { query = it }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton("Shuffle", I.Shuffle, filled = true) { vm.shufflePlay(list) }
            Box {
                PillButton(sortNames[sort], I.SwapVert) { sortMenu = true }
                SudoMenu(sortMenu, { sortMenu = false }) {
                    sortNames.forEachIndexed { i, n -> DropdownMenuItem(text = { Text(n) }, onClick = { sort = i; sortMenu = false }) }
                }
            }
            Spacer(Modifier.weight(1f))
            Text("${list.size}", color = t.text.copy(alpha = 0.6f))
        }
        if (vm.loading) Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = t.primary) }
        else if (list.isEmpty()) EmptyState(if (query.isBlank()) "No songs found" else "Nothing matches “$query”", if (query.isBlank()) null else "Clear search") { query = "" }
        else LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(list, key = { _, s -> s.id }) { i, s -> SongRow(s, vm, onClick = { vm.play(list, i) }) }
        }
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit) {
    val t = LocalSudo.current
    val shape = RoundedCornerShape(50)
    TextField(
        value = value, onValueChange = onChange, singleLine = true,
        placeholder = { Text("Search songs, artists, albums", color = t.text.copy(alpha = 0.5f)) },
        leadingIcon = { Icon(I.Search, null, tint = t.text.copy(alpha = 0.7f)) },
        trailingIcon = { if (value.isNotEmpty()) IconButton({ onChange("") }) { Icon(I.Close, null, tint = t.text.copy(alpha = 0.7f)) } },
        shape = shape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = t.text, unfocusedTextColor = t.text, cursorColor = t.primary,
        ),
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().sudoPanel(shape),
    )
}

@Composable
fun EmptyState(text: String, action: String? = null, onAction: () -> Unit = {}) {
    val t = LocalSudo.current
    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(text, color = t.text.copy(alpha = 0.6f))
        if (action != null) PillButton(action, I.Close, filled = true, onClick = onAction)
    }
}

/** Search-friendly form of a string: lower-case, no Arabic diacritics/tatweel, and alef/yaa/taa-marbuta variants unified. */
fun foldForSearch(s: String): String {
    val sb = StringBuilder(s.length)
    for (ch in s.lowercase()) {
        when {
            ch in '\u064B'..'\u065F' || ch == '\u0670' || ch == '\u0640' -> {}
            ch == '\u0623' || ch == '\u0625' || ch == '\u0622' || ch == '\u0671' -> sb.append('\u0627')
            ch == '\u0649' -> sb.append('\u064A')
            ch == '\u0629' -> sb.append('\u0647')
            else -> sb.append(ch)
        }
    }
    return sb.toString()
}

// ---------------- Albums ----------------
@Composable
fun AlbumsTab(vm: PlayerViewModel, onOpen: (Detail) -> Unit) {
    val t = LocalSudo.current
    val albums = remember(vm.songs) { vm.songs.groupBy { it.album }.toList().sortedBy { it.first.lowercase() } }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Albums")
        if (albums.isEmpty()) EmptyState("No albums yet")
        LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(albums, key = { it.first }) { (name, songs) ->
                Column(Modifier.clickable { onOpen(Detail.Album(name)) }) {
                    Artwork(songs.first(), Modifier.fillMaxWidth().aspectRatio(1f), 384, 20.dp)
                    Text(name, color = t.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    Text("${songs.first().artist} • ${songs.size}", color = t.text.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ---------------- Artists ----------------
@Composable
fun BrowseTab(vm: PlayerViewModel, onOpen: (Detail) -> Unit) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Browse")
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Artists", I.Person, filled = mode == 0) { mode = 0 }
            PillButton("Folders", I.Folder, filled = mode == 1) { mode = 1 }
        }
        if (mode == 0) ArtistList(vm, onOpen) else FolderList(vm, onOpen)
    }
}

@Composable
private fun FolderList(vm: PlayerViewModel, onOpen: (Detail) -> Unit) {
    val t = LocalSudo.current
    val folders = remember(vm.songs) { vm.songs.groupBy { it.folder }.toList().sortedBy { it.first.lowercase() } }
    if (folders.isEmpty()) EmptyState("No folders yet")
    LazyColumn {
        items(folders, key = { it.first }) { (path, songs) ->
            Row(Modifier.fillMaxWidth().clickable { onOpen(Detail.Folder(path)) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(t.primary.copy(alpha = 0.8f), t.secondary.copy(alpha = 0.8f)))), contentAlignment = Alignment.Center) {
                    Icon(I.Folder, null, tint = t.onAccent)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(path.substringAfterLast('/').ifEmpty { path }, color = t.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${songs.size} songs • $path", color = t.text.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ArtistList(vm: PlayerViewModel, onOpen: (Detail) -> Unit) {
    val t = LocalSudo.current
    val artists = remember(vm.songs) { vm.songs.groupBy { it.artist }.toList().sortedBy { it.first.lowercase() } }
    Column(Modifier.fillMaxSize()) {
        if (artists.isEmpty()) EmptyState("No artists yet")
        LazyColumn {
            items(artists, key = { it.first }) { (name, songs) ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(Detail.Artist(name)) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(songs.first(), Modifier.size(54.dp), 128, 27.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(name, color = t.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${songs.size} songs", color = t.text.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

// ---------------- Playlists ----------------
@Composable
fun PlaylistsTab(vm: PlayerViewModel, onOpen: (Detail) -> Unit) {
    val t = LocalSudo.current
    var creating by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var importMsg by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<com.sudosoc.sudoplay.data.Playlist?>(null) }
    var deleting by remember { mutableStateOf<com.sudosoc.sudoplay.data.Playlist?>(null) }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Playlists")
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("New playlist", I.Add, filled = true) { creating = true }
            PillButton("Import", I.PlaylistAdd) { importing = true }
        }
        vm.importBusy?.let { Text(it, color = t.secondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)) }
        importMsg?.let { Text(it, color = t.text.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)) }
        LazyColumn(Modifier.padding(top = 6.dp)) {
            items(Smart.entries.toList(), key = { "smart_" + it.name }) { k ->
                val icon = when (k) {
                    Smart.Recent -> I.History
                    Smart.Most -> I.Whatshot
                    Smart.Added -> I.NewReleases
                    Smart.Unplayed -> I.Explore
                    Smart.Mix -> I.AutoAwesome
                }
                Row(Modifier.fillMaxWidth().clickable { onOpen(Detail.SmartList(k)) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).sudoPanel(RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = t.primary)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(k.title, color = t.text, fontWeight = FontWeight.SemiBold)
                        Text("Smart list", color = t.text.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            items(vm.store.items.toList(), key = { it.id }) { p ->
                var menu by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().clickable { onOpen(Detail.Pl(p.id)) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val first = remember(p.songIds, vm.songs, vm.olib.version) { p.songIds.firstNotNullOfOrNull { vm.entrySong(it) } }
                    if (p.id == FAV_ID) Box(Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(t.primary, t.secondary))), contentAlignment = Alignment.Center) {
                        Icon(I.Favorite, null, tint = t.onAccent)
                    } else Artwork(first, Modifier.size(54.dp), 128, 14.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = t.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${p.songIds.size} songs", color = t.text.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                    }
                    if (p.id != FAV_ID) Box {
                        IconButton({ menu = true }) { Icon(I.MoreVert, null, tint = t.text.copy(alpha = 0.7f)) }
                        SudoMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { renaming = p; menu = false })
                            DropdownMenuItem(text = { Text("Delete") }, onClick = { deleting = p; menu = false })
                        }
                    }
                }
            }
        }
    }
    if (creating) NameDialog("New playlist", onDismiss = { creating = false }) { vm.store.create(it); creating = false }
    renaming?.let { p -> NameDialog("Rename playlist", onDismiss = { renaming = null }, confirm = "Save", initial = p.name) { vm.store.rename(p.id, it); renaming = null } }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null }, title = { Text("Delete “${p.name}”?") },
            text = { Text("The playlist is removed. Your songs stay on your phone.") },
            confirmButton = { TextButton({ vm.store.delete(p.id); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton({ deleting = null }) { Text("Cancel") } },
        )
    }
    if (importing) ImportPlaylistDialog(vm, onDismiss = { importing = false }) { importMsg = it }
}

/** Import from a link (YouTube, SoundCloud, Spotify, Deezer, Audius), pasted song names, or a .m3u / .csv file. */
@Composable
fun ImportPlaylistDialog(vm: PlayerViewModel, onDismiss: () -> Unit, onResult: (String) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var text by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching {
            val body = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }.orEmpty()
            text = body
            if (name.isBlank()) name = (uri.lastPathSegment ?: "").substringAfterLast('/').substringBeforeLast('.').take(40)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Import playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Paste a playlist link (YouTube, SoundCloud, Spotify, Deezer, Audius), or a list of songs — one “Artist - Title” per line. " +
                        "Songs stay online until you download them; the whole playlist can be downloaded in one tap.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(text, { text = it }, label = { Text("Link or song list") }, minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Playlist name (optional)") }, modifier = Modifier.fillMaxWidth())
                TextButton({ picker.launch("*/*") }) { Icon(I.FileOpen, null); Spacer(Modifier.width(6.dp)); Text("From a file (.m3u, .csv, .txt)") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (text.isNotBlank()) { vm.importPlaylist(text, name.trim(), onResult); onDismiss() }
            }, enabled = text.isNotBlank()) { Text("Import") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun NameDialog(title: String, onDismiss: () -> Unit, confirm: String = "Create", initial: String = "", onOk: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton({ onOk(name) }, enabled = name.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun PlaylistPickerDialog(vm: PlayerViewModel, song: Song, onDismiss: () -> Unit) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        NameDialog("New playlist", onDismiss = { creating = false }) { n -> vm.store.addSong(vm.store.create(n).id, song.id); onDismiss() }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Add to playlist") },
        text = {
            LazyColumn {
                items(vm.store.items.toList(), key = { it.id }) { p ->
                    val has = song.id in p.songIds
                    Row(Modifier.fillMaxWidth().clickable { vm.store.addSong(p.id, song.id); onDismiss() }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, Modifier.weight(1f))
                        if (has) Icon(I.Check, null)
                    }
                }
            }
        },
        confirmButton = { TextButton({ creating = true }) { Text("New playlist") } },
        dismissButton = { TextButton(onDismiss) { Text("Close") } },
    )
}

// ---------------- Detail (album / artist / playlist) ----------------
@Composable
fun DetailScreen(vm: PlayerViewModel, detail: Detail, onBack: () -> Unit) {
    val t = LocalSudo.current
    val (title, list) = when (detail) {
        is Detail.Album -> detail.name to vm.songs.filter { it.album == detail.name }.sortedBy { it.track }
        is Detail.Artist -> detail.name to vm.songs.filter { it.artist == detail.name }.sortedBy { it.title.lowercase() }
        is Detail.Folder -> detail.path.substringAfterLast('/').ifEmpty { detail.path } to vm.songs.filter { it.folder == detail.path }.sortedBy { it.title.lowercase() }
        is Detail.SmartList -> detail.kind.title to vm.smart(detail.kind)
        is Detail.Pl -> {
            vm.olib.version
            (vm.store.get(detail.id)?.name ?: "Playlist") to vm.playlistSongs(detail.id)
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(top = 6.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(I.ArrowBack, null, tint = t.text) }
            Text(title, color = t.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val isPl = detail is Detail.Pl
        val onlineCount = if (isPl) list.count { it.online } else 0
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Play", I.PlayArrow, filled = true) { vm.playList(list, 0) }
            PillButton("Shuffle", I.Shuffle) { vm.playList(list, 0, true) }
            if (onlineCount > 0) PillButton("Download $onlineCount", I.Download) { vm.downloadSongs(list) }
        }
        if (isPl && list.isNotEmpty()) Text(
            "${list.size - onlineCount} on your phone • $onlineCount online", color = t.text.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 18.dp),
        )
        vm.onlineError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)) }
        vm.onlineNote?.let { Text(it, color = t.secondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)) }
        if (list.isEmpty()) EmptyState(if (isPl) "Nothing here yet. Add songs from anywhere, or import a playlist." else "Nothing here yet")
        LazyColumn {
            itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
                SongRow(s, vm, onClick = { vm.playList(list, i) }, badge = isPl, extraMenu = if (detail is Detail.Pl) { dismiss ->
                    DropdownMenuItem(text = { Text("Remove from playlist") }, onClick = { vm.store.removeSong(detail.id, s.id); dismiss() })
                } else null)
            }
        }
    }
}
