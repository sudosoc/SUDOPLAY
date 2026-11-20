package com.sudosoc.sudoplay.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Every dialog in the app goes through this. It shadows material3's AlertDialog (same package beats the
 * star import), so all existing dialogs pick up the theme surface, rounded corners, hairline accent border
 * and theme text colours without each call site changing.
 */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    val t = LocalSudo.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismissRequest, confirmButton = confirmButton, modifier = modifier, dismissButton = dismissButton,
        title = title?.let { { androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalTextStyle provides androidx.compose.material3.MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) { it() } } },
        text = text,
        shape = RoundedCornerShape(28.dp), containerColor = t.surface, tonalElevation = 0.dp,
        iconContentColor = t.primary, titleContentColor = t.text, textContentColor = t.text.copy(alpha = 0.85f),
        properties = androidx.compose.ui.window.DialogProperties(),
    )
}
