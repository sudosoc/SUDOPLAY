package com.sudosoc.sudoplay.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sudosoc.sudoplay.PlayerViewModel
import com.sudosoc.sudoplay.online.DlOption
import com.sudosoc.sudoplay.online.DownloadCenter
import com.sudosoc.sudoplay.online.Online
import com.sudosoc.sudoplay.online.OnlineItem
import com.sudosoc.sudoplay.online.OnlineKind
import com.sudosoc.sudoplay.playback.ArtLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class SourceState(val items: List<OnlineItem> = emptyList(), val error: String? = null, val loading: Boolean = false)

private val ALL_SOURCES = listOf(OnlineKind.Quran, OnlineKind.SoundCloud, OnlineKind.YouTube, OnlineKind.Archive, OnlineKind.Radio)

/** One search box for everything: type a song, a sheikh, a surah — or paste a link. */
@Composable
fun OnlineTab(vm: PlayerViewModel) {
    val t = LocalSudo.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val kb = LocalSoftwareKeyboardController.current
    val prefs = remember { ctx.getSharedPreferences("sudoplay", android.content.Context.MODE_PRIVATE) }

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }                 // 0 = all sources
    val results = remember { mutableStateMapOf<OnlineKind, SourceState>() }
    var searched by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf((prefs.getString("online_hist", "") ?: "").split("\n").filter { it.isNotBlank() }) }
    val stack = remember { mutableStateListOf<OnlineItem>() }               // opened collections (reciter → mushaf …)
    var dlTarget by remember { mutableStateOf<Pair<List<OnlineItem>, String>?>(null) }
    var showDownloads by remember { mutableStateOf(false) }
    var searchJobs by remember { mutableStateOf<List<kotlinx.coroutines.Job>>(emptyList()) }

    BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }

    fun runSearch(q0: String = query) {
        val q = q0.trim(); if (q.isEmpty() && filter != 5) return
        query = q; kb?.hide()
        if (q.isNotEmpty()) {
            history = (listOf(q) + history.filter { it != q }).take(12)
            prefs.edit().putString("online_hist", history.joinToString("\n")).apply()
        }
        if (Online.isLink(q)) {
            scope.launch {
                try {
                    val item = Online.fromLink(q)
                    if (item.collection != null) stack.add(item) else vm.playOnline(item, 0)
                } catch (e: Throwable) { vm.onlineError = (e.message ?: "Bad link").take(160) }
            }
            return
        }
        vm.onlineError = null
        searchJobs.forEach { it.cancel() }
        results.clear(); searched = true
        val kinds = if (filter == 0) ALL_SOURCES else listOf(ALL_SOURCES[filter - 1])
        kinds.forEach { results[it] = SourceState(loading = true) }
        searchJobs = kinds.map { k ->
            scope.launch {
                results[k] = try { SourceState(Online.search(k, q)) } catch (e: Throwable) { SourceState(error = (e.message ?: e.javaClass.simpleName).take(120)) }
            }
        }
    }
    // switching the source chip re-runs the current search (radio opens on the most popular stations)
    LaunchedEffect(filter) { if (searched || filter == 5) runSearch() }

    val detail = stack.lastOrNull()
    if (detail != null) {
        CollectionScreen(vm, detail, onBack = { stack.removeAt(stack.lastIndex) }, onOpen = { stack.add(it) }, onDownload = { items, name -> dlTarget = items to name })
    } else Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { ScreenTitle("Online") }
            val n = DownloadCenter.activeCount
            OnlinePill(if (n > 0) "Downloads ($n)" else "Downloads", n > 0) { showDownloads = true }
        }
        OutlinedTextField(
            query, { query = it }, singleLine = true,
            placeholder = { Text("Song, sheikh, surah — or paste a link", color = t.text.copy(alpha = 0.5f)) },
            leadingIcon = { Icon(I.Search, null, tint = t.text.copy(alpha = 0.6f)) },
            trailingIcon = {
                Row {
                    if (query.isNotEmpty()) IconButton({ query = ""; results.clear(); searched = false }) { Icon(I.Close, null, tint = t.text.copy(alpha = 0.7f)) }
                    IconButton({ runSearch() }) { Icon(I.PlayArrow, null, tint = t.primary) }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { runSearch() }),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = t.text, unfocusedTextColor = t.text, focusedBorderColor = t.primary, unfocusedBorderColor = t.text.copy(alpha = 0.3f), cursorColor = t.primary),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OnlinePill("All", filter == 0) { filter = 0 }
            ALL_SOURCES.forEachIndexed { i, k -> OnlinePill(k.label, filter == i + 1) { filter = i + 1 } }
        }
        vm.onlineError?.let { Text(it, color = t.text, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }

        if (!searched) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (history.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent", color = t.text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("Clear", color = t.primary, fontSize = 13.sp, modifier = Modifier.clickable { history = emptyList(); prefs.edit().remove("online_hist").apply() }.padding(6.dp))
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { history.forEach { h -> OnlinePill(h, false) { runSearch(h) } } }
                }
                Text("Try", color = t.text, fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("العفاسي", "سورة الكهف", "عبد الباسط", "ماهر المعيقلي سورة البقرة", "lofi").forEach { s -> OnlinePill(s, false) { runSearch(s) } }
                }
                Text(
                    "One search over the Quran library (every sheikh and surah), SoundCloud, YouTube, archive.org and radio. Paste a YouTube / SoundCloud / archive.org page or any direct audio link in the same box. No account, no API key.",
                    color = t.text.copy(alpha = 0.55f), fontSize = 12.sp,
                )
            }
        } else {
            val shown = ALL_SOURCES.filter { results[it] != null }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                shown.forEach { k ->
                    val st = results[k]!!
                    item(key = "h_$k") {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(k.label, color = t.text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (st.loading) CircularProgressIndicator(Modifier.size(16.dp), color = t.primary, strokeWidth = 2.dp)
                            else if (filter == 0 && st.items.size > 5) Text("See all ${st.items.size}", color = t.primary, fontSize = 13.sp, modifier = Modifier.clickable { filter = ALL_SOURCES.indexOf(k) + 1 }.padding(4.dp))
                        }
                    }
                    if (st.error != null) item(key = "e_$k") {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Could not load: ${st.error}", color = t.text.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text("Retry", color = t.primary, modifier = Modifier.clickable {
                                results[k] = SourceState(loading = true)
                                scope.launch { results[k] = try { SourceState(Online.search(k, query)) } catch (e: Throwable) { SourceState(error = (e.message ?: "failed").take(120)) } }
                            }.padding(8.dp))
                        }
                    } else if (!st.loading && st.items.isEmpty()) item(key = "n_$k") {
                        Text("Nothing found", color = t.text.copy(alpha = 0.45f), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
                    }
                    val list = if (filter == 0) st.items.take(if (k == OnlineKind.Quran) 8 else 5) else st.items
                    items(list, key = { "${k}_${it.key}" }) { item ->
                        OnlineRow(item, vm, onOpen = { stack.add(it) }, onDownload = { dlTarget = listOf(it) to "" })
                    }
                }
            }
        }
    }

    dlTarget?.let { (items, name) -> DownloadDialog(vm, items, name) { dlTarget = null } }
    if (showDownloads) DownloadsDialog { showDownloads = false }
}

@Composable
private fun CollectionScreen(vm: PlayerViewModel, item: OnlineItem, onBack: () -> Unit, onOpen: (OnlineItem) -> Unit, onDownload: (List<OnlineItem>, String) -> Unit) {
    val t = LocalSudo.current
    var reload by remember { mutableIntStateOf(0) }
    val state by produceState<SourceState>(SourceState(loading = true), item.key, reload) {
        value = SourceState(loading = true)
        value = try { SourceState(Online.children(item)) } catch (e: Throwable) { SourceState(error = (e.message ?: e.javaClass.simpleName).take(120)) }
    }
    val files = state.items.filter { it.collection == null }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(I.ArrowBack, null, tint = t.text) }
            Column(Modifier.weight(1f)) {
                Text(item.title, color = t.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (item.subtitle.isNotBlank()) Text(item.subtitle, color = t.text.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
        if (files.isNotEmpty()) Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OnlinePill("Play all (${files.size})", true) { vm.playOnlineAll(files, files.first()) }
            OnlinePill("Download all", false) { onDownload(files, item.title) }
        }
        vm.onlineError?.let { Text(it, color = t.text, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        when {
            state.loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = t.primary) }
            state.error != null -> EmptyState("Could not load: ${state.error}", "Try again") { reload++ }
            state.items.isEmpty() -> EmptyState("Nothing in here")
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                items(state.items, key = { it.key }) { c ->
                    OnlineRow(c, vm, onOpen = onOpen, onDownload = { onDownload(listOf(it), "") }, onPlay = { vm.playOnlineAll(files, it) })
                }
            }
        }
    }
}

@Composable
private fun OnlineRow(item: OnlineItem, vm: PlayerViewModel, onOpen: (OnlineItem) -> Unit, onDownload: (OnlineItem) -> Unit, onPlay: ((OnlineItem) -> Unit)? = null) {
    val t = LocalSudo.current
    val ctx = LocalContext.current
    val thumb by produceState<android.graphics.Bitmap?>(null, item.thumb) {
        value = item.thumb?.let { u -> withContext(Dispatchers.IO) { ArtLoader.load(ctx, android.net.Uri.parse(u), 160) } }
    }
    var menu by remember { mutableStateOf(false) }
    val loading = vm.onlineBusy == item.url && item.url.isNotEmpty()
    val folder = item.collection != null
    Row(
        Modifier.fillMaxWidth().clickable { if (folder) onOpen(item) else (onPlay ?: { vm.playOnline(it, 0) })(item) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp)) {
            ArtImage(thumb, Modifier.fillMaxSize(), 10.dp)
            if (folder) Icon(I.Folder, null, tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f), modifier = Modifier.align(Alignment.Center).size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = t.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(item.subtitle.takeIf { it.isNotBlank() }, item.durationSec.takeIf { it > 0 }?.let { fmt(it * 1000) } ?: if (item.kind == OnlineKind.Radio) "LIVE" else null).joinToString("  •  "),
                color = t.text.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = t.primary, strokeWidth = 2.dp)
        else if (folder) Icon(I.KeyboardArrowDown, null, tint = t.text.copy(alpha = 0.5f), modifier = Modifier.size(22.dp).padding(end = 2.dp))
        else Box {
            IconButton({ menu = true }) { Icon(I.MoreVert, null, tint = t.text.copy(alpha = 0.7f)) }
            SudoMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text("Play next") }, onClick = { vm.playOnline(item, 1); menu = false })
                DropdownMenuItem(text = { Text("Add to queue") }, onClick = { vm.playOnline(item, 2); menu = false })
                if (item.kind != OnlineKind.Radio) DropdownMenuItem(text = { Text("Download…") }, onClick = { onDownload(item); menu = false })
            }
        }
    }
}

/** Pick the quality (when the source offers several) and optionally a playlist to drop the finished file into. */
@Composable
private fun DownloadDialog(vm: PlayerViewModel, items: List<OnlineItem>, groupName: String, onClose: () -> Unit) {
    val t = LocalSudo.current
    val ctx = LocalContext.current
    val bulk = items.size > 1
    var opts by remember { mutableStateOf<List<DlOption>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var pick by remember { mutableIntStateOf(0) }
    var plChoice by remember { mutableStateOf("none") }                   // none | new | <playlist id>
    var newName by remember { mutableStateOf(groupName) }
    LaunchedEffect(items) {
        try { opts = DownloadCenter.options(items.first()) } catch (e: Throwable) { err = (e.message ?: "failed").take(140) }
    }
    AlertDialog(
        onDismissRequest = onClose, containerColor = t.bgTop,
        title = { Text(if (bulk) "Download ${items.size} files" else "Download", color = t.text) },
        confirmButton = {
            TextButton(onClick = {
                val o = opts ?: return@TextButton
                val plId = when (plChoice) {
                    "none" -> null
                    "new" -> vm.store.create(newName.ifBlank { items.first().title }).id
                    else -> plChoice
                }
                // not tied to this dialog's lifetime: it closes right after, and the queue must survive that
                kotlinx.coroutines.MainScope().launch {
                    items.forEach { it0 ->
                        val option = if (bulk) runCatching { DownloadCenter.options(it0).firstOrNull() }.getOrNull() else o.getOrNull(pick)
                        if (option != null) DownloadCenter.enqueue(it0.title, it0.subtitle.substringBefore(" • "), option, plId)
                    }
                }
                Toast.makeText(ctx, "Downloading — see Downloads", Toast.LENGTH_SHORT).show()
                onClose()
            }, enabled = opts?.isNotEmpty() == true) { Text("Download", color = t.primary) }
        },
        dismissButton = { TextButton(onClose) { Text("Cancel", color = t.text.copy(alpha = 0.7f)) } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (bulk) groupName.ifBlank { items.first().title } else items.first().title, color = t.text, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Text("Quality", color = t.text.copy(alpha = 0.6f), fontSize = 12.sp)
                when {
                    err != null -> Text(err!!, color = t.text, fontSize = 13.sp)
                    opts == null -> CircularProgressIndicator(Modifier.size(22.dp), color = t.primary, strokeWidth = 2.dp)
                    opts!!.isEmpty() -> Text("This can't be downloaded.", color = t.text)
                    bulk -> Text(opts!!.first().label + " — each file in its original quality", color = t.text, fontSize = 13.sp)
                    else -> opts!!.forEachIndexed { i, o ->
                        Row(Modifier.fillMaxWidth().clickable { pick = i }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(pick == i, { pick = i }, colors = RadioButtonDefaults.colors(selectedColor = t.primary))
                            Text(o.label + if (o.hls) " (HLS)" else "", color = t.text, fontSize = 14.sp)
                        }
                    }
                }
                Text("Add to playlist", color = t.text.copy(alpha = 0.6f), fontSize = 12.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OnlinePill("None", plChoice == "none") { plChoice = "none" }
                    vm.store.items.forEach { p -> OnlinePill(p.name, plChoice == p.id) { plChoice = p.id } }
                    OnlinePill("+ New", plChoice == "new") { plChoice = "new" }
                }
                if (plChoice == "new") OutlinedTextField(
                    newName, { newName = it }, singleLine = true, label = { Text("Playlist name") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = t.text, unfocusedTextColor = t.text, focusedBorderColor = t.primary, unfocusedBorderColor = t.text.copy(alpha = 0.3f), focusedLabelColor = t.primary, unfocusedLabelColor = t.text.copy(alpha = 0.6f), cursorColor = t.primary),
                )
                Text("Saved in Music/SUDOPLAY, so it also shows in your library. Only download what you have the right to keep.", color = t.text.copy(alpha = 0.45f), fontSize = 11.sp)
            }
        },
    )
}

@Composable
private fun DownloadsDialog(onClose: () -> Unit) {
    val t = LocalSudo.current
    AlertDialog(
        onDismissRequest = onClose, containerColor = t.bgTop,
        title = { Text("Downloads", color = t.text) },
        confirmButton = { TextButton(onClose) { Text("Close", color = t.primary) } },
        dismissButton = { if (DownloadCenter.jobs.any { !it.active }) TextButton({ DownloadCenter.clearFinished() }) { Text("Clear finished", color = t.text.copy(alpha = 0.7f)) } },
        text = {
            if (DownloadCenter.jobs.isEmpty()) Text("Nothing downloaded yet.", color = t.text.copy(alpha = 0.7f))
            else Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DownloadCenter.jobs.forEach { j ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(j.title, color = t.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                                Text(j.error?.let { "Failed: $it" } ?: (j.state + " • " + j.option.label), color = t.text.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 2)
                            }
                            if (j.active) Text("✕", color = t.text.copy(alpha = 0.6f), modifier = Modifier.clickable { DownloadCenter.cancel(j) }.padding(8.dp))
                        }
                        if (j.active) LinearProgressIndicator(progress = { j.progress }, Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = t.primary, trackColor = t.text.copy(alpha = 0.15f))
                    }
                }
            }
        },
    )
}

@Composable
private fun OnlinePill(text: String, selected: Boolean, onClick: () -> Unit) {
    val t = LocalSudo.current
    val shape = RoundedCornerShape(50)
    Text(
        text, color = if (selected) t.onAccent else t.text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp, maxLines = 1,
        modifier = Modifier.clip(shape)
            .then(if (selected) Modifier.background(Brush.horizontalGradient(listOf(t.primary, t.secondary)), shape) else Modifier.sudoPanel(shape))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
    )
}
