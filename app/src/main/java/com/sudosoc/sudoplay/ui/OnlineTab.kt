package com.sudosoc.sudoplay.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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

private val ALL_SOURCES = listOf(OnlineKind.Quran, OnlineKind.YouTube, OnlineKind.SoundCloud, OnlineKind.Audius, OnlineKind.Archive, OnlineKind.Radio)
private val DEFAULT_ON = setOf(OnlineKind.Quran, OnlineKind.YouTube, OnlineKind.SoundCloud, OnlineKind.Audius, OnlineKind.Archive)   // radio is noisy for song searches

/** One search box for everything: type a song, a sheikh, a surah — or paste a link. */
@Composable
fun OnlineTab(vm: PlayerViewModel) {
    val t = LocalSudo.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val kb = LocalSoftwareKeyboardController.current
    val prefs = remember { ctx.getSharedPreferences("sudoplay", android.content.Context.MODE_PRIVATE) }

    var query by rememberSaveable { mutableStateOf("") }
    var enabled by remember {
        mutableStateOf(
            prefs.getString("online_src", null)?.split(",")?.mapNotNull { n -> ALL_SOURCES.firstOrNull { it.name == n } }?.toSet()?.takeIf { it.isNotEmpty() } ?: DEFAULT_ON
        )
    }
    fun setEnabled(v: Set<OnlineKind>) { enabled = v; prefs.edit().putString("online_src", v.joinToString(",") { it.name }).apply() }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var suggestOn by remember { mutableStateOf(prefs.getBoolean("online_suggest", false)) }
    var showFilters by remember { mutableStateOf(false) }
    val results = remember { mutableStateMapOf<OnlineKind, SourceState>() }
    var searched by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf((prefs.getString("online_hist", "") ?: "").split("\n").filter { it.isNotBlank() }) }
    val stack = remember { mutableStateListOf<OnlineItem>() }               // opened collections (reciter → mushaf …)
    var showDownloads by remember { mutableStateOf(false) }
    var searchJobs by remember { mutableStateOf<List<kotlinx.coroutines.Job>>(emptyList()) }

    BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }

    fun runSearch(q0: String = query) {
        val q = q0.trim(); if (q.isEmpty() && enabled != setOf(OnlineKind.Radio)) return
        query = q; kb?.hide(); suggestions = emptyList()
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
        val kinds = ALL_SOURCES.filter { it in enabled }
        if (kinds.isEmpty()) { vm.onlineError = "Every source is switched off — pick at least one in Sources"; searched = false; return }
        kinds.forEach { results[it] = SourceState(loading = true) }
        searchJobs = kinds.map { k ->
            scope.launch {
                results[k] = try { SourceState(Online.search(k, q)) } catch (e: Throwable) { SourceState(error = (e.message ?: e.javaClass.simpleName).take(120)) }
            }
        }
    }
    // changing which sources are on re-runs the current search (radio alone opens on the most popular stations)
    LaunchedEffect(enabled) { if (searched || enabled == setOf(OnlineKind.Radio)) runSearch() }
    // "Find online" from a library song
    LaunchedEffect(vm.onlineQuery) { vm.onlineQuery?.let { q -> vm.onlineQuery = null; query = q; runSearch(q) } }
    // search-as-you-type (opt-in)
    LaunchedEffect(query, suggestOn) {
        suggestions = emptyList()
        if (!suggestOn || query.trim().length < 2 || Online.isLink(query)) return@LaunchedEffect
        kotlinx.coroutines.delay(300)
        suggestions = Online.suggest(query.trim())
    }

    val detail = stack.lastOrNull()
    if (detail != null) {
        CollectionScreen(vm, detail, onBack = { stack.removeAt(stack.lastIndex) }, onOpen = { stack.add(it) }, onDownload = { items, name -> vm.dlRequest = items to name })
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
        if (suggestions.isNotEmpty()) Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            suggestions.forEach { sg -> Text(sg, color = t.text, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().clickable { runSearch(sg) }.padding(vertical = 9.dp, horizontal = 6.dp)) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OnlinePill("Sources ${enabled.size}/${ALL_SOURCES.size} ▾", false) { showFilters = true }
            // tap = switch a source on/off, hold = search only that one
            ALL_SOURCES.forEach { k ->
                val on = k in enabled
                OnlinePill(k.label, on, onLong = { setEnabled(setOf(k)) }) { setEnabled(if (on && enabled.size > 1) enabled - k else enabled + k) }
            }
        }
        vm.onlineError?.let { Text(it, color = t.text, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        vm.onlineNote?.let { Text(it, color = t.primary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) }

        if (!searched) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (history.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent", color = t.text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("Clear", color = t.primary, fontSize = 13.sp, modifier = Modifier.clickable { history = emptyList(); prefs.edit().remove("online_hist").apply() }.padding(6.dp))
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { history.forEach { h -> OnlinePill(h, false) { runSearch(h) } } }
                }
                if (vm.favSheikhs.isNotEmpty()) {
                    Text("Favorite sheikhs", color = t.text, fontWeight = FontWeight.Bold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        vm.favSheikhs.forEach { (name, key) -> OnlinePill("★ $name", false) { stack.add(OnlineItem(OnlineKind.Quran, name, "", null, 0, "", key)) } }
                    }
                }
                Text("Try", color = t.text, fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("العفاسي", "سورة الكهف", "عبد الباسط", "ماهر المعيقلي سورة البقرة", "lofi").forEach { s -> OnlinePill(s, false) { runSearch(s) } }
                    OnlinePill("إذاعة القرآن", false) { setEnabled(setOf(OnlineKind.Radio)); runSearch("quran") }
                }
                Text(
                    "One search over the Quran library (every sheikh and surah), YouTube, SoundCloud, Audius, archive.org and radio — switch any of them off with Sources. If YouTube refuses a stream, the same song is looked up on SoundCloud / Audius automatically. Paste a YouTube / SoundCloud / archive.org page or any direct audio link in the same box. No account, no API key.",
                    color = t.text.copy(alpha = 0.55f), fontSize = 12.sp,
                )
            }
        } else {
            val shown = ALL_SOURCES.filter { results[it] != null }
            val multi = shown.size > 1
            // "Top matches": the best hit of each source side by side, so the song you meant is near the top whatever the source
            val top: List<OnlineItem> = if (!multi) emptyList() else {
                val seen = HashSet<String>(); val out = ArrayList<OnlineItem>()
                val lists = shown.mapNotNull { k -> results[k]?.items?.filter { it.collection == null } }
                var round = 0
                while (out.size < 8 && lists.any { round < it.size } && round < 3) {
                    lists.forEach { l -> l.getOrNull(round)?.let { if (out.size < 8 && seen.add(OnlineMatchKey.of(it))) out.add(it) } }
                    round++
                }
                out
            }
            val topKeys = top.map { it.key }.toSet()
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                if (top.isNotEmpty()) {
                    item(key = "h_top") { Text("Top matches", color = t.text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                    items(top, key = { "top_${it.key}" }) { item -> OnlineRow(item, vm, onOpen = { stack.add(it) }, onDownload = { vm.dlRequest = listOf(it) to "" }, showSource = true) }
                }
                shown.forEach { k ->
                    val st = results[k]!!
                    item(key = "h_$k") {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(k.label, color = t.text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (st.loading) CircularProgressIndicator(Modifier.size(16.dp), color = t.primary, strokeWidth = 2.dp)
                            else if (multi && st.items.size > 5) Text("Only ${k.label} (${st.items.size})", color = t.primary, fontSize = 13.sp, modifier = Modifier.clickable { setEnabled(setOf(k)) }.padding(4.dp))
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
                    val list = (if (multi) st.items.take(if (k == OnlineKind.Quran) 8 else 5) else st.items).filter { it.key !in topKeys || it.collection != null }
                    items(list, key = { "${k}_${it.key}" }) { item ->
                        OnlineRow(item, vm, onOpen = { stack.add(it) }, onDownload = { vm.dlRequest = listOf(it) to "" })
                    }
                }
            }
        }
    }

    if (showFilters) SourcesDialog(enabled, suggestOn, onSources = { setEnabled(it) }, onSuggest = { suggestOn = it; prefs.edit().putBoolean("online_suggest", it).apply() }) { showFilters = false }
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
        val reciterKey = item.collection?.takeIf { it.startsWith("q:") && it.count { c -> c == ':' } == 1 }
        if (reciterKey != null) Row(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
            val fav = vm.favSheikhs.any { it.second == reciterKey }
            OnlinePill(if (fav) "★ Favorite sheikh" else "☆ Add to favorite sheikhs", fav) { vm.toggleSheikh(item.title, reciterKey) }
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
private fun OnlineRow(item: OnlineItem, vm: PlayerViewModel, onOpen: (OnlineItem) -> Unit, onDownload: (OnlineItem) -> Unit, onPlay: ((OnlineItem) -> Unit)? = null, showSource: Boolean = false) {
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
                listOfNotNull(item.kind.label.takeIf { showSource }, item.subtitle.takeIf { it.isNotBlank() }, item.durationSec.takeIf { it > 0 }?.let { fmt(it * 1000) } ?: if (item.kind == OnlineKind.Radio) "LIVE" else null).joinToString("  •  "),
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
fun DownloadDialog(vm: PlayerViewModel, items: List<OnlineItem>, groupName: String, onClose: () -> Unit) {
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun OnlinePill(text: String, selected: Boolean, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    val t = LocalSudo.current
    val shape = RoundedCornerShape(50)
    Text(
        text, color = if (selected) t.onAccent else t.text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp, maxLines = 1,
        modifier = Modifier.clip(shape)
            .then(if (selected) Modifier.background(Brush.horizontalGradient(listOf(t.primary, t.secondary)), shape) else Modifier.sudoPanel(shape))
            .combinedClickable(onLongClick = onLong, onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

/** Same song from two sources (same title + artist words) shows once in Top matches. */
private object OnlineMatchKey { fun of(i: OnlineItem) = com.sudosoc.sudoplay.online.OnlineMatch.tokens(i.title + " " + i.subtitle).sorted().joinToString(" ").ifBlank { i.key } }

/** Which sources a search looks in. Every one can be switched off; "Only" jumps straight to a single source. */
@Composable
private fun SourcesDialog(enabled: Set<OnlineKind>, suggestOn: Boolean, onSources: (Set<OnlineKind>) -> Unit, onSuggest: (Boolean) -> Unit, onClose: () -> Unit) {
    val t = LocalSudo.current
    val about = mapOf(
        OnlineKind.Quran to "Every sheikh and surah (mp3quran.net)",
        OnlineKind.YouTube to "Huge catalogue — streams are often refused, then another source is tried",
        OnlineKind.SoundCloud to "Full tracks, remixes, covers",
        OnlineKind.Audius to "Independent artists, full tracks",
        OnlineKind.Archive to "Free audio library: talks, old recordings, lectures",
        OnlineKind.Radio to "Live stations (noisy for song searches, so off by default)",
    )
    AlertDialog(
        onDismissRequest = onClose, containerColor = t.bgTop,
        title = { Text("Search in", color = t.text) },
        confirmButton = { TextButton(onClose) { Text("Done", color = t.primary) } },
        dismissButton = {
            Row {
                TextButton({ onSources(ALL_SOURCES.toSet()) }) { Text("All", color = t.text.copy(alpha = 0.8f)) }
                TextButton({ onSources(DEFAULT_ON) }) { Text("Reset", color = t.text.copy(alpha = 0.8f)) }
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ALL_SOURCES.forEach { k ->
                    val on = k in enabled
                    Row(Modifier.fillMaxWidth().clickable { onSources(if (on && enabled.size > 1) enabled - k else enabled + k) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(on, { onSources(if (on && enabled.size > 1) enabled - k else enabled + k) }, colors = CheckboxDefaults.colors(checkedColor = t.primary))
                        Column(Modifier.weight(1f)) {
                            Text(k.label, color = t.text, fontWeight = FontWeight.SemiBold)
                            Text(about[k] ?: "", color = t.text.copy(alpha = 0.55f), fontSize = 11.sp)
                        }
                        Text("Only", color = t.primary, fontSize = 13.sp, modifier = Modifier.clickable { onSources(setOf(k)) }.padding(8.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().clickable { onSuggest(!suggestOn) }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Search suggestions", color = t.text, fontWeight = FontWeight.SemiBold)
                        Text("Sends what you type to Google's suggestion service as you type. Off by default.", color = t.text.copy(alpha = 0.55f), fontSize = 11.sp)
                    }
                    Switch(suggestOn, onSuggest, colors = SwitchDefaults.colors(checkedTrackColor = t.primary, checkedThumbColor = t.onAccent))
                }
            }
        },
    )
}
