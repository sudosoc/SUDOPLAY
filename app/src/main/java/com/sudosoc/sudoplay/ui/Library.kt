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
    val fg = if (filled) Color.Black.copy(alpha = 0.85f) else t.text
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
    val sortNames = listOf("Title", "Artist", "Recently added")
    val list = remember(vm.songs, query, sort) {
        val f = if (query.isBlank()) vm.songs else vm.songs.filter {
            it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true)
        }
        when (sort) {
            1 -> f.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
            2 -> f.sortedByDescending { it.dateAdded }
            else -> f.sortedBy { it.title.lowercase() }
        }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Songs")
        SearchField(query) { query = it }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton("Shuffle", Icons.Rounded.Shuffle, filled = true) { vm.shufflePlay(list) }
            Box {
                PillButton(sortNames[sort], Icons.Rounded.SwapVert) { sortMenu = true }
                DropdownMenu(sortMenu, { sortMenu = false }) {
                    sortNames.forEachIndexed { i, n -> DropdownMenuItem(text = { Text(n) }, onClick = { sort = i; sortMenu = false }) }
                }
            }
            Spacer(Modifier.weight(1f))
            Text("${list.size}", color = t.text.copy(alpha = 0.6f))
        }
        if (vm.loading) Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = t.primary) }
        else if (list.isEmpty()) EmptyState("No songs found")
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
        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = t.text.copy(alpha = 0.7f)) },
        trailingIcon = { if (value.isNotEmpty()) IconButton({ onChange("") }) { Icon(Icons.Rounded.Close, null, tint = t.text.copy(alpha = 0.7f)) } },
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
fun EmptyState(text: String) {
    val t = LocalSudo.current
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(text, color = t.text.copy(alpha = 0.6f))
    }
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
            PillButton("Artists", Icons.Rounded.Person, filled = mode == 0) { mode = 0 }
            PillButton("Folders", Icons.Rounded.Folder, filled = mode == 1) { mode = 1 }
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
                    Icon(Icons.Rounded.Folder, null, tint = Color.Black.copy(alpha = 0.75f))
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
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Playlists")
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { PillButton("New playlist", Icons.Rounded.Add, filled = true) { creating = true } }
        LazyColumn(Modifier.padding(top = 6.dp)) {
            items(Smart.entries.toList(), key = { "smart_" + it.name }) { k ->
                val icon = when (k) {
                    Smart.Recent -> Icons.Rounded.History
                    Smart.Most -> Icons.Rounded.Whatshot
                    Smart.Added -> Icons.Rounded.NewReleases
                    Smart.Unplayed -> Icons.Rounded.Explore
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
                    val first = remember(p.songIds, vm.songs) { vm.songs.firstOrNull { it.id in p.songIds } }
                    if (p.id == FAV_ID) Box(Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(t.primary, t.secondary))), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Favorite, null, tint = Color.Black.copy(alpha = 0.8f))
                    } else Artwork(first, Modifier.size(54.dp), 128, 14.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = t.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${p.songIds.size} songs", color = t.text.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                    }
                    if (p.id != FAV_ID) Box {
                        IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, null, tint = t.text.copy(alpha = 0.7f)) }
                        DropdownMenu(menu, { menu = false }) { DropdownMenuItem(text = { Text("Delete") }, onClick = { vm.store.delete(p.id); menu = false }) }
                    }
                }
            }
        }
    }
    if (creating) NameDialog("New playlist", onDismiss = { creating = false }) { vm.store.create(it); creating = false }
}

@Composable
fun NameDialog(title: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton({ onOk(name) }) { Text("Create") } },
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
                        if (has) Icon(Icons.Rounded.Check, null)
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
            val p = vm.store.get(detail.id)
            (p?.name ?: "Playlist") to (p?.songIds?.mapNotNull { id -> vm.songs.firstOrNull { it.id == id } } ?: emptyList())
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(top = 6.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = t.text) }
            Text(title, color = t.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Play", Icons.Rounded.PlayArrow, filled = true) { vm.play(list, 0, false) }
            PillButton("Shuffle", Icons.Rounded.Shuffle) { vm.shufflePlay(list) }
        }
        if (list.isEmpty()) EmptyState("Nothing here yet")
        LazyColumn {
            itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
                SongRow(s, vm, onClick = { vm.play(list, i) }, extraMenu = if (detail is Detail.Pl) { dismiss ->
                    DropdownMenuItem(text = { Text("Remove from playlist") }, onClick = { vm.store.removeSong(detail.id, s.id); dismiss() })
                } else null)
            }
        }
    }
}

// ---------------- Style / Themes ----------------
@Composable
fun StyleTab(vm: PlayerViewModel) {
    val t = LocalSudo.current
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Style")
        Text("Pick a look. Glass, solid and artwork-blur themes change how every surface is drawn.",
            color = t.text.copy(alpha = 0.65f), modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp), style = MaterialTheme.typography.bodySmall)
        val ctx = androidx.compose.ui.platform.LocalContext.current
        if (android.os.Build.VERSION.SDK_INT >= 30 && !android.os.Environment.isExternalStorageManager()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                PillButton("Enable .lrc lyrics files", Icons.Rounded.Lyrics) {
                    ctx.startActivity(
                        android.content.Intent(
                            android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            android.net.Uri.parse("package:${ctx.packageName}"),
                        )
                    )
                }
            }
        }
        LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(Themes.all, key = { it.id }) { th -> ThemeCard(th, selected = vm.themeId == th.id) { vm.setTheme(th.id) } }
        }
    }
}

@Composable
private fun ThemeCard(th: AppTheme, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier.fillMaxWidth().aspectRatio(0.85f).clip(shape)
            .background(Brush.verticalGradient(listOf(th.bgTop, th.bgBottom)))
            .border(if (selected) 3.dp else 1.dp, if (selected) th.primary else Color.White.copy(alpha = 0.15f), shape)
            .clickable(onClick = onClick),
    ) {
        // blobs for glass / art preview
        if (th.style != Style.Solid) {
            Box(Modifier.size(90.dp).align(Alignment.TopStart).background(Brush.radialGradient(listOf(th.primary.copy(alpha = 0.45f), Color.Transparent))))
            Box(Modifier.size(90.dp).align(Alignment.BottomEnd).background(Brush.radialGradient(listOf(th.secondary.copy(alpha = 0.45f), Color.Transparent))))
        }
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            // fake mini-player
            val panelBg = when (th.style) {
                Style.Solid -> th.surface
                Style.Glass -> if (th.dark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.55f)
                Style.Art -> if (th.dark) Color.Black.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.6f)
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(panelBg).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(Brush.linearGradient(listOf(th.primary, th.secondary))))
                Spacer(Modifier.width(8.dp))
                Column {
                    Box(Modifier.width(44.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(th.text.copy(alpha = 0.8f)))
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.width(28.dp).height(4.dp).clip(RoundedCornerShape(3.dp)).background(th.text.copy(alpha = 0.4f)))
                }
            }
            Column {
                Text(th.name, color = th.text, fontWeight = FontWeight.Bold)
                Text(th.style.label + if (th.dynamic) " • wallpaper colors" else "", color = th.text.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
            }
        }
        if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = th.primary, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
    }
}
