package com.sudosoc.sudoplay.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** The one popup-menu look used everywhere: theme surface, rounded, hairline accent border. */
@Composable
fun SudoMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalSudo.current
    val shape = RoundedCornerShape(20.dp)
    DropdownMenu(
        expanded = expanded, onDismissRequest = onDismissRequest, modifier = modifier,
        shape = shape, containerColor = t.surface, tonalElevation = 0.dp, shadowElevation = 14.dp,
        border = BorderStroke(1.dp, t.primary.copy(alpha = 0.28f)),
        content = content,
    )
}

@Composable
fun SudoMenuItem(text: String, selected: Boolean = false, onClick: () -> Unit) {
    val t = LocalSudo.current
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(text, color = if (selected) t.primary else t.text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
        if (selected) Icon(I.Check, null, tint = t.primary, modifier = Modifier.size(20.dp))
    }
}
