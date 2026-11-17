package com.sudosoc.sudoplay.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** How surfaces (cards, mini player, bars) are drawn. */
enum class Style(val label: String) { Solid("Solid"), Glass("Glass"), Art("Artwork blur") }

@Immutable
data class AppTheme(
    val id: String,
    val name: String,
    val dark: Boolean,
    val style: Style,
    val bgTop: Color,
    val bgBottom: Color,
    val surface: Color,
    val primary: Color,
    val secondary: Color,
    val text: Color,
    val dynamic: Boolean = false,
)

private fun c(hex: Long) = Color(hex or 0xFF000000)

object Themes {
    val all: List<AppTheme> = listOf(
        // ---- Glass ----
        AppTheme("aurora", "Aurora", true, Style.Glass, c(0x0B1026), c(0x1B0F3B), c(0x1E2140), c(0x7CF3E0), c(0xB07CFF), c(0xF2F4FF)),
        AppTheme("frost", "Frost", false, Style.Glass, c(0xCFE8FF), c(0xEAD7FF), c(0xFFFFFF), c(0x3A6BFF), c(0xFF7AC8), c(0x14213D)),
        AppTheme("sunset", "Sunset Glass", true, Style.Glass, c(0x2A0F2E), c(0x7A2E3A), c(0x3A1B3F), c(0xFFB36B), c(0xFF6B9A), c(0xFFF3EA)),
        AppTheme("ocean", "Ocean Glass", true, Style.Glass, c(0x04202E), c(0x0B4A5E), c(0x0E3A4A), c(0x5EEAD4), c(0x38BDF8), c(0xE8FBFF)),
        AppTheme("lavender", "Lavender Mist", false, Style.Glass, c(0xE9E1FF), c(0xFFE1F0), c(0xFFFFFF), c(0x7C4DFF), c(0xFF5C9E), c(0x261B3F)),
        // ---- Artwork blur ----
        AppTheme("vinyl", "Vinyl", true, Style.Art, c(0x0A0A0A), c(0x000000), c(0x1A1A1A), c(0xFFFFFF), c(0xC7C7C7), c(0xFFFFFF)),
        AppTheme("haze", "Haze", true, Style.Art, c(0x120A1F), c(0x05030A), c(0x1A1226), c(0xFFB86B), c(0xFF6B8B), c(0xFFF6EC)),
        AppTheme("daydream", "Daydream", false, Style.Art, c(0xF2EEE8), c(0xE2DAD0), c(0xFFFFFF), c(0x1F1F24), c(0x6B6B75), c(0x1B1B1F)),
        // ---- Solid ----
        AppTheme("midnight", "Midnight", true, Style.Solid, c(0x0E0F14), c(0x161821), c(0x1E2029), c(0x8AB4FF), c(0xB794F6), c(0xECEFF4)),
        AppTheme("amoled", "AMOLED Black", true, Style.Solid, c(0x000000), c(0x000000), c(0x111113), c(0x00E5A8), c(0x00B8FF), c(0xFFFFFF)),
        AppTheme("paper", "Paper", false, Style.Solid, c(0xF7F5F0), c(0xEDE9E0), c(0xFFFFFF), c(0x2B5CFF), c(0xFF8A3D), c(0x1B1B1F)),
        AppTheme("forest", "Forest", true, Style.Solid, c(0x0F1A14), c(0x14261C), c(0x1B3326), c(0x7BE495), c(0xC8E6A0), c(0xEAF6EC)),
        AppTheme("rose", "Rosé", true, Style.Solid, c(0x1F0F16), c(0x2E1520), c(0x3A1B29), c(0xFF8FB1), c(0xFFC2A1), c(0xFFEFF4)),
        AppTheme("cyber", "Cyber Neon", true, Style.Solid, c(0x05010F), c(0x0D0221), c(0x170535), c(0xFF2BD6), c(0x00F0FF), c(0xF4EAFF)),
        AppTheme("mocha", "Mocha", true, Style.Solid, c(0x1B1411), c(0x2A1F1A), c(0x35271F), c(0xE0B58A), c(0xC98B5E), c(0xF5EBE2)),
        AppTheme("sand", "Desert Sand", false, Style.Solid, c(0xF4E9D8), c(0xE8D5B5), c(0xFFF8EC), c(0xC2561F), c(0x8A6D3B), c(0x2B2118)),
        // ---- Wallpaper-colour (Android 12+) ----
        AppTheme("you", "Material You", true, Style.Glass, c(0x101014), c(0x1B1B22), c(0x24242C), c(0x9FC2FF), c(0xCFBCFF), c(0xE6E6EE), dynamic = true),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all[0]
}

val LocalSudo = staticCompositionLocalOf<AppTheme> { Themes.all[0] }

@Composable
fun SudoPlayTheme(themeId: String, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    var t = Themes.byId(themeId)
    if (t.dynamic && Build.VERSION.SDK_INT >= 31) {
        val dark = isSystemInDarkTheme()
        val cs = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        t = t.copy(
            dark = dark, bgTop = cs.background, bgBottom = cs.surfaceVariant, surface = cs.surfaceContainer,
            primary = cs.primary, secondary = cs.tertiary, text = cs.onBackground,
        )
    }
    val onPrimary = if (t.primary.luminance() > 0.5f) Color.Black else Color.White
    val scheme = (if (t.dark) darkColorScheme() else lightColorScheme()).copy(
        primary = t.primary, onPrimary = onPrimary,
        secondary = t.secondary, background = t.bgTop, onBackground = t.text,
        surface = t.surface, onSurface = t.text,
        surfaceVariant = t.surface, onSurfaceVariant = t.text.copy(alpha = 0.7f),
        surfaceContainer = t.surface, surfaceContainerHigh = t.surface, surfaceContainerHighest = t.surface,
        outline = t.text.copy(alpha = 0.3f),
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            ctx.findActivity()?.window?.let { w ->
                WindowCompat.getInsetsController(w, view).apply {
                    isAppearanceLightStatusBars = !t.dark
                    isAppearanceLightNavigationBars = !t.dark
                }
            }
        }
    }
    CompositionLocalProvider(LocalSudo provides t) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
