package com.sudosoc.sudoplay.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sudosoc.sudoplay.PlayerViewModel
import com.sudosoc.sudoplay.data.Song
import com.sudosoc.sudoplay.online.DPlaylist
import com.sudosoc.sudoplay.online.DTrack
import com.sudosoc.sudoplay.online.Discovery
import com.sudosoc.sudoplay.online.OnlineItem

private sealed interface Load<out T> {
    data object Busy : Load<Nothing>
    class Ok<T>(val v: T) : Load<T>
    class Err(val msg: String) : Load<Nothing>
}

@Composable
private fun <T> loadState(key: Any?, reload: Int, f: suspend () -> T): State<Load<T>> =
    produceState<Load<T>>(Load.Busy, key, reload) {
        value = Load.Busy
        value = try { Load.Ok(f()) } catch (e: Throwable) { Load.Err((e.message ?: e.javaClass.simpleName).take(80)) }
    }

private fun artStub(url: String?) = Song(
    id = (url ?: "").hashCode().toLong(), title = "", artist = "", album = "", albumId = 0, durationMs = 0,
    uri = android.net.Uri.EMPTY, dateAdded = 0, track = 0, artUrl = url, online = true,
)

@Composable
private fun Card(title: String, sub: String, thumb: String?, badge: String? = null, onClick: () -> Unit) {
    val t = LocalSudo.current
    Column(Modifier.width(124.dp).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box {
            Artwork(artStub(thumb), Modifier.size(124.dp), 256, 14.dp)
            if (badge != null) Text(
                badge, color = t.onAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(t.primary, androidx.compose.foundation.shape.RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
        Text(title, color = t.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(sub, color = t.text.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun <T> Shelf(state: Load<T>, retry: () -> Unit, empty: String = "Nothing to show right now", content: @Composable (T) -> Unit) {
    val t = LocalSudo.current
    when (state) {
        is Load.Busy -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), color = t.primary, strokeWidth = 2.dp) }
        is Load.Err -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Couldn't load (${state.msg})", color = t.text.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("Retry", color = t.primary, modifier = Modifier.clickable(onClick = retry).padding(8.dp))
        }
        is Load.Ok -> content(state.v)
    }
}

/** The landing page of the Online tab: charts by genre, playlists to import, free full songs, and (opt-in) suggestions from what you play. */
@Composable
fun DiscoveryPanel(vm: PlayerViewModel) {
    val t = LocalSudo.current
    var genre by rememberSaveable { mutableIntStateOf(0) }
    var topic by rememberSaveable { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    var note by remember { mutableStateOf<String?>(null) }

    val chart by loadState(genre, reload) { Discovery.chart(genre) }
    val pls by loadState(topic, reload) { if (topic.isBlank()) Discovery.chartPlaylists() else Discovery.playlists(topic) }
    val audius by loadState("audius", reload) { Discovery.audiusTrending() }
    val topArtists = remember(vm.plays, vm.rawSongs) {
        val byId = vm.rawSongs.associateBy { it.id }
        vm.plays.entries.mapNotNull { (id, n) -> byId[id]?.let { it.artist to n } }
            .filter { !it.first.equals("<unknown>", true) && it.first.isNotBlank() }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }.entries.sortedByDescending { it.value }.map { it.key }.take(3)
    }
    val personal = vm.settings.discoverPersonal.value
    val forYou by loadState(if (personal) topArtists else null, reload) { if (personal && topArtists.isNotEmpty()) Discovery.becauseOf(topArtists) else emptyList() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Discover", color = t.text, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Text("Refresh", color = t.primary, fontSize = 13.sp, modifier = Modifier.clickable { reload++ }.padding(6.dp))
        }
        note?.let { Text(it, color = t.secondary, fontSize = 12.sp) }

        // ---- charts
        Text("Trending songs", color = t.text, fontWeight = FontWeight.SemiBold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Discovery.genres.forEach { (id, name) -> OnlinePill(name, genre == id) { genre = id } }
        }
        Shelf(chart, { reload++ }) { tracks ->
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                tracks.forEach { tr -> Card(tr.title, tr.artist, tr.thumb) { vm.playByName(tr.title, tr.artist, tr.durationSec, tr.thumb) } }
            }
            if (tracks.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val label = Discovery.genres.first { it.first == genre }.second
                OnlinePill("Save as playlist", false) { note = "Saved “" + vm.saveTracksAsPlaylist("Trending · $label", tracks) + "” to Playlists — songs play online until you download them" }
            }
        }

        // ---- playlists
        Text("Playlists to explore", color = t.text, fontWeight = FontWeight.SemiBold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OnlinePill("Charts", topic.isBlank()) { topic = "" }
            Discovery.playlistTopics.forEach { tp -> OnlinePill(tp, topic == tp) { topic = tp } }
        }
        Shelf(pls, { reload++ }) { list ->
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                list.forEach { p ->
                    Card(p.title, "${p.count} songs", p.thumb) { vm.openPreview(p.title, p.thumb, "https://www.deezer.com/playlist/${p.id}") }
                }
            }
        }
        vm.importBusy?.let { Text(it, color = t.secondary, fontSize = 12.sp) }

        // ---- free full songs
        Text("Free full songs · Audius trending", color = t.text, fontWeight = FontWeight.SemiBold)
        Shelf(audius, { reload++ }) { items: List<OnlineItem> ->
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items.forEach { it0 -> Card(it0.title, it0.subtitle, it0.thumb) { vm.playOnline(it0) } }
            }
        }

        // ---- personalised (opt-in)
        if (!personal) {
            Text(
                "Turn on “For you” to get suggestions based on the artists you play most. It sends only those artist names to Deezer.",
                color = t.text.copy(alpha = 0.6f), fontSize = 12.sp,
            )
            OnlinePill("Turn on For you", false) { vm.settings.discoverPersonal.set(true) }
        } else if (topArtists.isEmpty()) {
            Text("Play some music and “For you” will fill in.", color = t.text.copy(alpha = 0.6f), fontSize = 12.sp)
        } else Shelf(forYou, { reload++ }) { groups: List<Pair<String, List<DTrack>>> ->
            groups.forEach { (artist, tracks) ->
                Text("Because you listen to $artist", color = t.text, fontWeight = FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    tracks.forEach { tr -> Card(tr.title, tr.artist, tr.thumb) { vm.playByName(tr.title, tr.artist, tr.durationSec, tr.thumb) } }
                }
            }
        }
        Text("Charts and playlists come from Deezer's public API. Playing a suggested song looks it up on your enabled sources.", color = t.text.copy(alpha = 0.45f), fontSize = 11.sp)
    }
}
