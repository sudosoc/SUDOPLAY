package com.sudosoc.sudoplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sudosoc.sudoplay.PlayerViewModel
import com.sudosoc.sudoplay.online.Online
import com.sudosoc.sudoplay.online.OnlineItem
import com.sudosoc.sudoplay.online.OnlineKind
import com.sudosoc.sudoplay.playback.ArtLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Search YouTube / SoundCloud, browse internet radio, or paste a link. No account and no API key. */
@Composable
fun OnlineTab(vm: PlayerViewModel) {
    val t = LocalSudo.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var kindIdx by rememberSaveable { mutableIntStateOf(0) }
    val kind = OnlineKind.entries[kindIdx]
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<OnlineItem>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searched by remember { mutableStateOf(false) }

    fun run() {
        if (kind == OnlineKind.Link) { if (query.isNotBlank()) vm.playOnlineLink(query); return }
        if (kind != OnlineKind.Radio && query.isBlank()) return
        busy = true; error = null; searched = true
        scope.launch {
            try { results = Online.search(kind, query.trim()) }
            catch (e: Throwable) { results = emptyList(); error = (e.message ?: e.javaClass.simpleName).take(160) }
            finally { busy = false }
        }
    }
    // radio opens on the most popular stations
    LaunchedEffect(kind) { results = emptyList(); error = null; searched = false; if (kind == OnlineKind.Radio) run() }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Online")
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("YouTube", "SoundCloud", "Radio", "Link").forEachIndexed { i, n -> OnlinePill(n, kindIdx == i) { kindIdx = i } }
        }
        OutlinedTextField(
            query, { query = it }, singleLine = true,
            placeholder = { Text(when (kind) { OnlineKind.Radio -> "Station name or genre (empty = most popular)"; OnlineKind.Link -> "Paste a YouTube / SoundCloud / stream link"; else -> "Search songs" }, color = t.text.copy(alpha = 0.5f)) },
            trailingIcon = { IconButton(::run) { Icon(if (kind == OnlineKind.Link) I.PlayArrow else I.Search, null, tint = t.primary) } },
            keyboardOptions = KeyboardOptions(imeAction = if (kind == OnlineKind.Link) ImeAction.Go else ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { run() }, onGo = { run() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = t.text, unfocusedTextColor = t.text, focusedBorderColor = t.primary, unfocusedBorderColor = t.text.copy(alpha = 0.3f), cursorColor = t.primary,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        )
        vm.onlineError?.let { Text("Could not play: $it", color = t.text, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        when {
            busy -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = t.primary) }
            error != null -> EmptyState("Search failed: $error", "Try again") { run() }
            kind == OnlineKind.Link -> Text(
                "Plays YouTube and SoundCloud pages, or any direct audio stream (mp3, aac, m3u8…).",
                color = t.text.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(16.dp),
            )
            searched && results.isEmpty() -> EmptyState("Nothing found")
            results.isEmpty() -> Text(
                "Streams come straight from the source. No account, no API key. YouTube changes often, so if a search stops working the extractor library needs an update.",
                color = t.text.copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.padding(16.dp),
            )
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                itemsIndexed(results, key = { i, it -> "${it.url}#$i" }) { _, item -> OnlineRow(item, vm) }
            }
        }
    }
}

@Composable
private fun OnlineRow(item: OnlineItem, vm: PlayerViewModel) {
    val t = LocalSudo.current
    val ctx = LocalContext.current
    val thumb by produceState<android.graphics.Bitmap?>(null, item.thumb) {
        value = item.thumb?.let { u -> withContext(Dispatchers.IO) { ArtLoader.load(ctx, android.net.Uri.parse(u), 160) } }
    }
    var menu by remember { mutableStateOf(false) }
    val loading = vm.onlineBusy == item.url
    Row(
        Modifier.fillMaxWidth().clickable { vm.playOnline(item, 0) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtImage(thumb, Modifier.size(52.dp), 10.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = t.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(item.subtitle.takeIf { it.isNotBlank() }, item.durationSec.takeIf { it > 0 }?.let { fmt(it * 1000) } ?: if (item.kind == OnlineKind.Radio) "LIVE" else null).joinToString("  •  "),
                color = t.text.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = t.primary, strokeWidth = 2.dp)
        else Box {
            IconButton({ menu = true }) { Icon(I.MoreVert, null, tint = t.text.copy(alpha = 0.7f)) }
            SudoMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text("Play next") }, onClick = { vm.playOnline(item, 1); menu = false })
                DropdownMenuItem(text = { Text("Add to queue") }, onClick = { vm.playOnline(item, 2); menu = false })
            }
        }
    }
}

@Composable
private fun OnlinePill(text: String, selected: Boolean, onClick: () -> Unit) {
    val t = LocalSudo.current
    val shape = RoundedCornerShape(50)
    Text(
        text, color = if (selected) t.onAccent else t.text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp, maxLines = 1,
        modifier = Modifier.clip(shape)
            .then(if (selected) Modifier.background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(t.primary, t.secondary)), shape) else Modifier.sudoPanel(shape))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
    )
}
