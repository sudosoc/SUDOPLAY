package com.sudosoc.sudoplay.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** "Removed — Undo": destructive actions happen at once and can be taken back for a few seconds, instead of asking "are you sure?". */
class UndoState {
    var message by mutableStateOf<String?>(null); private set
    var token by mutableIntStateOf(0); private set
    private var action: (() -> Unit)? = null

    fun show(msg: String, undo: () -> Unit) { message = msg; action = undo; token++ }
    fun run() { val a = action; dismiss(); a?.invoke() }
    fun dismiss() { message = null; action = null }
}

@Composable
fun UndoHost(state: UndoState) {
    val msg = state.message ?: return
    val t = LocalSudo.current
    LaunchedEffect(state.token) { delay(5500); state.dismiss() }
    Row(
        Modifier.padding(horizontal = 14.dp, vertical = 4.dp).fillMaxWidth().sudoPanel(RoundedCornerShape(18.dp)).padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(msg, color = t.text, modifier = Modifier.weight(1f), maxLines = 2)
        TextButton({ state.run() }) { Icon(I.Undo, null, tint = t.primary, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Undo", color = t.primary) }
    }
}
