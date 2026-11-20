package com.sudosoc.sudoplay.ui

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sudosoc.sudoplay.PlayerViewModel
import com.sudosoc.sudoplay.data.Song
import com.sudosoc.sudoplay.playback.Analyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Technical facts about a file + the bookmarks on it. */
@Composable
fun SongInfoDialog(vm: PlayerViewModel, song: Song, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val t = LocalSudo.current
    val facts by produceState<List<Pair<String, String>>>(emptyList(), song.id) {
        value = withContext(Dispatchers.IO) {
            buildList {
                add("Title" to song.title); add("Artist" to song.artist); add("Album" to song.album)
                add("Length" to fmt(song.durationMs))
                runCatching {
                    val ex = MediaExtractor(); ex.setDataSource(ctx, song.uri, null)
                    val f = ex.getTrackFormat(0)
                    add("Format" to (f.getString(MediaFormat.KEY_MIME) ?: "?").removePrefix("audio/"))
                    if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) add("Sample rate" to "${f.getInteger(MediaFormat.KEY_SAMPLE_RATE)} Hz")
                    if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) add("Channels" to f.getInteger(MediaFormat.KEY_CHANNEL_COUNT).toString())
                    if (f.containsKey(MediaFormat.KEY_BIT_RATE)) add("Bitrate" to "${f.getInteger(MediaFormat.KEY_BIT_RATE) / 1000} kbps")
                    ex.release()
                }
                Analyzer.cached(ctx, song.id)?.let { add("Loudness adjust" to "%+.1f dB".format(it.gainDb)) }
                add("Plays" to (vm.plays[song.id] ?: 0).toString())
                if (song.path.isNotBlank()) add("Path" to song.path)
            }
        }
    }
    val marks = vm.bookmarks[song.id].orEmpty()
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClose) { Text("Close", color = t.primary) } },
        title = { Text("Song info", color = t.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                facts.forEach { (k, v) ->
                    Row { Text(k, Modifier.width(110.dp), color = t.text.copy(alpha = 0.55f), fontSize = 13.sp); Text(v, color = t.text, fontSize = 13.sp) }
                }
                if (marks.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Bookmarks", color = t.text, fontWeight = FontWeight.Bold)
                    marks.forEach { m ->
                        Row(Modifier.fillMaxWidth().clickable {
                            if (vm.current?.id != song.id) vm.play(listOf(song), 0)
                            vm.seekTo(m.ms); onClose()
                        }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(fmt(m.ms), color = t.primary, fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp), fontSize = 13.sp)
                            Text(m.note, Modifier.weight(1f), color = t.text, fontSize = 13.sp, maxLines = 1)
                            Text("✕", color = t.text.copy(alpha = 0.5f), modifier = Modifier.clickable { vm.removeBookmark(song.id, m.ms) }.padding(8.dp))
                        }
                    }
                }
            }
        },
    )
}

/** Songs that look like the same recording (same title+artist, or same title and length within 2 s). */
fun findDuplicates(songs: List<Song>): List<List<Song>> {
    fun norm(s: String) = s.lowercase().replace(Regex("\\(.*?\\)|\\[.*?]"), "").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    return songs.groupBy { norm(it.title) + "|" + norm(it.artist) }.values
        .filter { it.size > 1 && it.map { s -> s.durationMs / 3000 }.let { d -> d.max() - d.min() <= 1 } }
        .sortedBy { it.first().title.lowercase() }
}

@Composable
fun DuplicatesBody(vm: PlayerViewModel) {
    val t = LocalSudo.current
    val groups = remember(vm.rawSongs) { findDuplicates(vm.rawSongs) }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            if (groups.isEmpty()) "No duplicates found." else "${groups.size} songs appear more than once. Tap a copy to play it — SUDOPLAY never deletes files for you.",
            color = t.text.copy(alpha = 0.7f), fontSize = 13.sp,
        )
        groups.forEach { g ->
            Column(Modifier.fillMaxWidth().sudoPanel(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).padding(12.dp)) {
                Text(g.first().title, color = t.text, fontWeight = FontWeight.Bold, maxLines = 1)
                g.forEach { s ->
                    Text(
                        "${s.path.ifBlank { s.folder }}  •  ${fmt(s.durationMs)}", color = t.text.copy(alpha = 0.6f), fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().clickable { vm.play(listOf(s), 0) }.padding(vertical = 4.dp), maxLines = 2,
                    )
                }
            }
        }
    }
}
