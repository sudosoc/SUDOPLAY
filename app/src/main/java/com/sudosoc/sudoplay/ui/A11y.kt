package com.sudosoc.sudoplay.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon as M3Icon
import androidx.compose.material3.IconButton as M3IconButton

private val LocalInIconButton = compositionLocalOf { false }

/**
 * Icon that talks: when it is the only content of a button and nobody gave it a description,
 * screen readers get one made from the icon's own name ("Delete", "More options"…). Decorative icons stay silent.
 * Same package as the screens, so every existing `Icon(...)` and `IconButton(...)` picks this up.
 */
@Composable
fun Icon(imageVector: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    val desc = contentDescription ?: if (LocalInIconButton.current) describe(imageVector) else null
    M3Icon(imageVector, desc?.let { Tr.t(it) }, modifier, tint)
}

@Composable
fun IconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    M3IconButton(onClick, modifier, enabled) { CompositionLocalProvider(LocalInIconButton provides true) { content() } }
}

private val nice = mapOf(
    "MoreVert" to "More options", "ArrowBack" to "Back", "KeyboardArrowDown" to "Expand", "KeyboardArrowUp" to "Collapse",
    "PlaylistAdd" to "Add to playlist", "FavoriteBorder" to "Favorite", "Refresh" to "Retry", "SkipNext" to "Next", "SkipPrevious" to "Previous",
    "PlayArrow" to "Play", "SwapVert" to "Reorder", "DeleteSweep" to "Clear", "Visibility" to "Show", "VisibilityOff" to "Hide",
    "ChevronRight" to "Open", "FileOpen" to "Open file", "SelectAll" to "Select", "ContentPaste" to "Paste", "Tune" to "Options",
)

internal fun describe(icon: ImageVector): String? {
    val raw = icon.name.substringAfterLast('.').substringBefore('_')
    if (raw.isBlank() || raw == "Icon") return null
    nice[raw]?.let { return it }
    return raw.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").lowercase().replaceFirstChar { it.uppercase() }
}
