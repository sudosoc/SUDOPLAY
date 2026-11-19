package com.sudosoc.sudoplay.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sudosoc.sudoplay.PlayerViewModel
import com.sudosoc.sudoplay.data.Smart
import com.sudosoc.sudoplay.data.Song
import com.sudosoc.sudoplay.data.FAV_ID

/** Every Home section: id → title. The order and which ones show are the user's (Edit on the Home screen). */
private val HOME_SECTIONS = linkedMapOf(
    "continue" to "Continue listening",
    "mix" to "Made for you",
    "most" to "Most played",
    "favs" to "Favorites",
    "added" to "Recently added",
    "downloads" to "Downloaded",
    "unplayed" to "Haven't heard yet",
    "stats" to "Your numbers",
)

fun homeOrder(saved: String): List<String> {
    val known = saved.split(",").filter { it in HOME_SECTIONS }.distinct()
    return known + HOME_SECTIONS.keys.filter { it !in known }   // sections added later still show up
}

/** Greeting + sections of covers. Edit mode: move sections up/down or hide them. */
@Composable
fun HomeTab(vm: PlayerViewModel, onTab: (Int) -> Unit) {
    val t = LocalSudo.current
    val st = vm.settings
    var editing by remember { mutableStateOf(false) }
    val order = homeOrder(st.homeOrder.value)
    val hidden = st.homeHidden.value.split(",").filter { it.isNotBlank() }.toSet()

    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..21 -> "Good evening"; else -> "Late night" }

    val data = remember(vm.songs, vm.plays, vm.recent, vm.store.items.toList(), vm.rawSongs, vm.olib.version) {
        mapOf(
            "continue" to vm.smart(Smart.Recent).take(15),
            "mix" to vm.smart(Smart.Mix).take(15),
            "most" to vm.smart(Smart.Most).take(15),
            "favs" to (vm.store.get(FAV_ID)?.songIds.orEmpty().mapNotNull { id -> vm.songs.firstOrNull { it.id == id } }).take(30),
            "added" to vm.smart(Smart.Added).take(15),
            "downloads" to vm.downloadedSongs().take(30),
            "unplayed" to vm.smart(Smart.Unplayed).shuffled(kotlin.random.Random(hour)).take(15),
        )
    }

    fun save(newOrder: List<String>, newHidden: Set<String>) { st.homeOrder.set(newOrder.joinToString(",")); st.homeHidden.set(newHidden.joinToString(",")) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item("head") {
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(greeting.let { g -> Tr.t(g) + st.userName.value.trim().takeIf { it.isNotEmpty() }?.let { n -> if (Tr.ar) "، $n" else ", $n" }.orEmpty() }, color = t.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("${vm.songs.size} songs on your phone", color = t.text.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                }
                IconButton({ editing = !editing }) { Icon(if (editing) I.Check else I.Edit, if (editing) "done" else "edit home", tint = if (editing) t.primary else t.text.copy(alpha = 0.75f)) }
            }
        }
        item("quick") {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.smart(Smart.Recent).firstOrNull()?.let { last -> item { PillButton("Resume", I.PlayArrow, filled = true) { vm.play(listOf(last), 0) } } }
                item { PillButton("Shuffle all", I.Shuffle, filled = vm.smart(Smart.Recent).isEmpty()) { vm.shufflePlay(vm.songs) } }
                item { PillButton("Discover", I.Explore) { onTab(5) } }
                item { PillButton("Playlists", I.LibraryMusic) { onTab(3) } }
                item { PillButton("Downloads", I.Download) { onTab(6) } }
            }
        }
        if (vm.songs.isEmpty() && !vm.loading) item("none") { EmptyState("Your library is empty. Add music to your phone, or open Discover to find songs online.") }
        order.forEachIndexed { i, id ->
            val list = data[id].orEmpty()
            val off = id in hidden
            if (editing) item("e_$id") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ save(order, if (off) hidden - id else hidden + id) }) { Icon(if (off) I.VisibilityOff else I.Visibility, null, tint = if (off) t.text.copy(alpha = 0.4f) else t.primary) }
                    Text(HOME_SECTIONS[id].orEmpty(), color = if (off) t.text.copy(alpha = 0.4f) else t.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconButton({ if (i > 0) save(order.toMutableList().also { it.add(i - 1, it.removeAt(i)) }, hidden) }, enabled = i > 0) { Icon(I.KeyboardArrowUp, "up", tint = t.text.copy(alpha = if (i > 0) 0.8f else 0.25f)) }
                    IconButton({ if (i < order.lastIndex) save(order.toMutableList().also { it.add(i + 1, it.removeAt(i)) }, hidden) }, enabled = i < order.lastIndex) { Icon(I.KeyboardArrowDown, "down", tint = t.text.copy(alpha = if (i < order.lastIndex) 0.8f else 0.25f)) }
                }
            } else if (!off && id == "stats" && vm.songs.isNotEmpty()) {
                item("h_stats") {
                    val hours = vm.songs.sumOf { it.durationMs } / 3_600_000
                    val plays = vm.plays.values.sum()
                    val topArtist = vm.songs.filter { (vm.plays[it.id] ?: 0) > 0 }.groupBy { artistLabel(it.artist) }
                        .maxByOrNull { e -> e.value.sumOf { vm.plays[it.id] ?: 0 } }?.key
                    Text("Your numbers", color = t.text, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 6.dp))
                    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("${vm.songs.size}", "songs", Modifier.weight(1f))
                        StatTile("$hours h", "of music", Modifier.weight(1f))
                        StatTile("$plays", "plays", Modifier.weight(1f))
                    }
                    if (topArtist != null) Text("Most played artist: $topArtist", color = t.text.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                }
            } else if (!off && list.isNotEmpty()) {
                item("h_$id") {
                    Text(HOME_SECTIONS[id].orEmpty(), color = t.text, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 6.dp))
                }
                item("r_$id") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(list, key = { _, s -> id + s.id }) { idx, s -> HomeCard(s, vm, vm.current?.id == s.id) { vm.play(list, idx) } }
                    }
                }
            }
        }
        if (editing) item("hint") { Text("Use the arrows to reorder and the eye to hide a section.", color = t.text.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(18.dp)) }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HomeCard(song: Song, vm: PlayerViewModel, current: Boolean, onClick: () -> Unit) {
    val t = LocalSudo.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.width(128.dp).combinedClickable(onClick = onClick, onLongClick = { vm.haptics.click(); menu = true })) {
        Artwork(song, Modifier.size(128.dp), 256, 16.dp)
        Spacer(Modifier.height(6.dp))
        Text(song.title, color = if (current) t.primary else t.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(artistLabel(song.artist), color = t.text.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        SudoMenu(menu, { menu = false }) {
            DropdownMenuItem(text = { Text("Play next") }, onClick = { vm.playNext(song); menu = false; android.widget.Toast.makeText(ctx, Tr.t("Plays next"), android.widget.Toast.LENGTH_SHORT).show() })
            DropdownMenuItem(text = { Text("Add to queue") }, onClick = { vm.addToQueue(song); menu = false; android.widget.Toast.makeText(ctx, Tr.t("Added to queue"), android.widget.Toast.LENGTH_SHORT).show() })
            DropdownMenuItem(text = { Text(if (vm.store.isFav(song.id)) "Remove from favorites" else "Add to favorites") }, onClick = { vm.store.toggleFav(song.id); menu = false })
            DropdownMenuItem(text = { Text("Add to playlist…") }, onClick = { vm.playlistTarget = song; menu = false })
        }
    }
}

@Composable
private fun StatTile(big: String, small: String, modifier: Modifier) {
    val t = LocalSudo.current
    Column(modifier.sudoPanel(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(big, color = t.primary, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1)
        Text(small, color = t.text.copy(alpha = 0.6f), fontSize = 11.sp)
    }
}
