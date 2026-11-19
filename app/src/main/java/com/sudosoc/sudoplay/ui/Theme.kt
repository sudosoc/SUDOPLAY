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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.remember
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
enum class Style(val label: String) {
    Live("Live"), Glass("Glass"), Gradient("Gradient"), Art("Artwork blur"), Solid("Solid"), Soft("Soft"), Outline("Outline")
}

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
    val live: PBg? = null,
)

private fun c(hex: Long) = Color(hex or 0xFF000000)

object Themes {
    private fun t(
        id: String, name: String, dark: Boolean, style: Style,
        top: Long, bottom: Long, surface: Long, primary: Long, secondary: Long, text: Long,
        dynamic: Boolean = false, live: PBg? = null,
    ) = AppTheme(id, name, dark, style, c(top), c(bottom), c(surface), c(primary), c(secondary), c(text), dynamic, live)

    val all: List<AppTheme> = listOf(
        // ---- Live (animated backgrounds) ----
        t("live-aurora", "Aurora Live", true, Style.Live, 0x040A18, 0x0B1530, 0x16224A, 0x7CF3E0, 0xB07CFF, 0xF2F8FF, live = PBg.Aurora),
        t("live-rain", "Rainy Night", true, Style.Live, 0x080D17, 0x131C2E, 0x1B2740, 0x9CC4F5, 0x6C8CC0, 0xEAF2FF, live = PBg.Rain),
        t("live-fireflies", "Fireflies", true, Style.Live, 0x03100A, 0x0A2418, 0x10301F, 0xE8F56B, 0x7CF0A0, 0xF1FFE8, live = PBg.Fireflies),
        t("live-pulse", "Neon Pulse", true, Style.Live, 0x07030F, 0x14052B, 0x1D0B3D, 0xFF2BD6, 0x00F0FF, 0xFAF0FF, live = PBg.Pulse),
        t("live-gold-pulse", "Golden Pulse", true, Style.Live, 0x0B0905, 0x1B1508, 0x2A200C, 0xE6C158, 0xFFE29A, 0xFFF8E1, live = PBg.Pulse),
        t("live-lava", "Lava Lamp", true, Style.Live, 0x1A0510, 0x2E0A1C, 0x3E1228, 0xFF8FB1, 0xFFB36B, 0xFFF1F4, live = PBg.Lava),
        t("live-snow", "Snowfall", true, Style.Live, 0x0B1A2E, 0x162F4D, 0x1B3A5C, 0xBFE3FF, 0x8EC5FF, 0xF2F8FF, live = PBg.Snow),
        t("live-matrix", "Code Rain", true, Style.Live, 0x000A02, 0x021A07, 0x042A0E, 0x00FF41, 0x9BFFB8, 0xCFFFD8, live = PBg.Matrix),
        t("live-bokeh", "Bokeh Gold", true, Style.Live, 0x120C06, 0x2A1B0A, 0x3A280F, 0xFFD27A, 0xFF9A5C, 0xFFF4E0, live = PBg.Bokeh),
        t("live-ripples", "Ripples", true, Style.Live, 0x031A24, 0x06303F, 0x0A3E50, 0x5EEAD4, 0x38BDF8, 0xE8FBFF, live = PBg.Ripples),
        t("live-embers", "Embers", true, Style.Live, 0x120504, 0x2A0C06, 0x3A1209, 0xFF9F43, 0xFF4D6D, 0xFFF1E6, live = PBg.Embers),
        t("live-nebula", "Nebula", true, Style.Live, 0x05030F, 0x120A2E, 0x1B1145, 0xD8B4FE, 0xF9A8D4, 0xF7F0FF, live = PBg.Nebula),
        t("live-sakeenah", "Sakeenah Lights", true, Style.Live, 0x06201A, 0x0F3D31, 0x12382E, 0xE9D8A6, 0x7FD1AE, 0xF3FBF6, live = PBg.Fireflies),
        t("live-northern", "Northern Lights", true, Style.Live, 0x02101A, 0x072A33, 0x0C3A44, 0x6EE7B7, 0x67E8F9, 0xEFFFFB, live = PBg.Aurora),
        // ---- Glass ----
        t("aurora", "Aurora", true, Style.Glass, 0x0B1026, 0x1B0F3B, 0x1E2140, 0x7CF3E0, 0xB07CFF, 0xF2F4FF),
        t("frost", "Frost", false, Style.Glass, 0xCFE8FF, 0xEAD7FF, 0xFFFFFF, 0x3A6BFF, 0xFF7AC8, 0x14213D),
        t("sunset", "Sunset Glass", true, Style.Glass, 0x2A0F2E, 0x7A2E3A, 0x3A1B3F, 0xFFB36B, 0xFF6B9A, 0xFFF3EA),
        t("ocean", "Ocean Glass", true, Style.Glass, 0x04202E, 0x0B4A5E, 0x0E3A4A, 0x5EEAD4, 0x38BDF8, 0xE8FBFF),
        t("lavender", "Lavender Mist", false, Style.Glass, 0xE9E1FF, 0xFFE1F0, 0xFFFFFF, 0x7C4DFF, 0xFF5C9E, 0x261B3F),
        t("emerald-glass", "Emerald Glass", true, Style.Glass, 0x03150F, 0x0A3B2C, 0x0E3B2C, 0x6EE7B7, 0x34D399, 0xE8FFF5),
        t("rose-glass", "Rosé Glass", true, Style.Glass, 0x2B0A1C, 0x5A1B3C, 0x3A1328, 0xFF9EC7, 0xFFC27A, 0xFFEFF6),
        t("midnight-glass", "Midnight Glass", true, Style.Glass, 0x0A0F24, 0x1B2A5A, 0x16204A, 0x8EA8FF, 0xA78BFA, 0xEEF1FF),
        t("sakeenah", "Sakeenah", true, Style.Glass, 0x06201A, 0x0F3D31, 0x12382E, 0xE9D8A6, 0x7FD1AE, 0xF3FBF6),
        t("madinah", "Madinah Dawn", false, Style.Glass, 0xEFF7EC, 0xFBF1D8, 0xFFFFFF, 0x1E7F5C, 0xC8963E, 0x14301F),
        t("desert", "Desert Night", true, Style.Glass, 0x1D1209, 0x4A2E17, 0x33200F, 0xFFC27A, 0xE07A5F, 0xFFF1E0),
        t("nile", "Nile", true, Style.Glass, 0x04171C, 0x0B3B3A, 0x0C2E30, 0x6FE3C1, 0xF2C94C, 0xE9FBF6),
        t("gold-glass", "Gold Glass", true, Style.Glass, 0x1A1408, 0x3D2C0C, 0x2A200C, 0xFFD166, 0xF4A261, 0xFFF6E0),
        t("crimson-glass", "Crimson Glass", true, Style.Glass, 0x230509, 0x5A0F1C, 0x3A0A14, 0xFF5C74, 0xFFA06B, 0xFFEAEE),
        t("smoke-glass", "Smoke Glass", true, Style.Glass, 0x15171C, 0x2A2D36, 0x20232B, 0xE0E3EB, 0x9AA3B5, 0xF4F6FA),
        t("candy", "Candy Glass", false, Style.Glass, 0xFFD6F5, 0xD6E9FF, 0xFFFFFF, 0xFF4FA3, 0x4F8BFF, 0x2A1B3F),
        t("mint-glass", "Mint Glass", false, Style.Glass, 0xD5F5E3, 0xD0F0FF, 0xFFFFFF, 0x0FA37F, 0x2E8BFF, 0x0F2A22),
        t("you", "Material You", true, Style.Glass, 0x101014, 0x1B1B22, 0x24242C, 0x9FC2FF, 0xCFBCFF, 0xE6E6EE, dynamic = true),
        // ---- Gradient ----
        t("grad-purple", "Purple Haze", true, Style.Gradient, 0x120A2E, 0x2A0F5C, 0x1E1245, 0xC084FC, 0x60A5FA, 0xF4EEFF),
        t("grad-fire", "Fire", true, Style.Gradient, 0x1E0A05, 0x4A1405, 0x2E1008, 0xFF9F43, 0xFF4D6D, 0xFFF1E6),
        t("grad-ice", "Ice Blue", true, Style.Gradient, 0x061826, 0x0B3552, 0x0A2740, 0x7DD3FC, 0xA5B4FC, 0xEAF6FF),
        t("grad-lime", "Lime Fizz", true, Style.Gradient, 0x0A1A0A, 0x16381A, 0x102A12, 0xBEF264, 0x34D399, 0xF0FFE6),
        t("grad-peach", "Peach", false, Style.Gradient, 0xFFE8DC, 0xFFD6E8, 0xFFFFFF, 0xFF7A59, 0xB04CFF, 0x3A1D1A),
        t("grad-sky", "Sky", false, Style.Gradient, 0xD9EEFF, 0xE6DEFF, 0xFFFFFF, 0x2D7CFF, 0x9B5CFF, 0x10203D),
        t("grad-twilight", "Twilight", true, Style.Gradient, 0x1B1535, 0x4B2A6B, 0x2B1D4D, 0xFF9AD5, 0x8EC5FF, 0xFBF0FF),
        // ---- Artwork blur ----
        t("vinyl", "Vinyl", true, Style.Art, 0x0A0A0A, 0x000000, 0x1A1A1A, 0xFFFFFF, 0xC7C7C7, 0xFFFFFF),
        t("haze", "Haze", true, Style.Art, 0x120A1F, 0x05030A, 0x1A1226, 0xFFB86B, 0xFF6B8B, 0xFFF6EC),
        t("daydream", "Daydream", false, Style.Art, 0xF2EEE8, 0xE2DAD0, 0xFFFFFF, 0x1F1F24, 0x6B6B75, 0x1B1B1F),
        t("art-smoke", "Smoke", true, Style.Art, 0x101114, 0x000000, 0x1A1B20, 0xE8E8EE, 0xA0A0AA, 0xF2F2F6),
        t("art-warm", "Warm Haze", true, Style.Art, 0x1D0F0A, 0x080403, 0x2A1810, 0xFFC48A, 0xFF8A65, 0xFFF3E8),
        t("art-cool", "Cool Haze", true, Style.Art, 0x07121F, 0x02060C, 0x0E1D30, 0x8AD7FF, 0x8AA2FF, 0xEAF4FF),
        t("art-emerald", "Green Haze", true, Style.Art, 0x06140D, 0x020805, 0x0C2418, 0x7CF0B2, 0xC8F7A0, 0xEAFFF3),
        t("art-pearl", "Pearl", false, Style.Art, 0xF5F3F0, 0xE7E3DC, 0xFFFFFF, 0x222226, 0x6A6A74, 0x1B1B1F),
        // ---- Solid ----
        t("midnight", "Midnight", true, Style.Solid, 0x0E0F14, 0x161821, 0x1E2029, 0x8AB4FF, 0xB794F6, 0xECEFF4),
        t("amoled", "AMOLED Black", true, Style.Solid, 0x000000, 0x000000, 0x111113, 0x00E5A8, 0x00B8FF, 0xFFFFFF),
        t("paper", "Paper", false, Style.Solid, 0xF7F5F0, 0xEDE9E0, 0xFFFFFF, 0x2B5CFF, 0xFF8A3D, 0x1B1B1F),
        t("forest", "Forest", true, Style.Solid, 0x0F1A14, 0x14261C, 0x1B3326, 0x7BE495, 0xC8E6A0, 0xEAF6EC),
        t("rose", "Rosé", true, Style.Solid, 0x1F0F16, 0x2E1520, 0x3A1B29, 0xFF8FB1, 0xFFC2A1, 0xFFEFF4),
        t("cyber", "Cyber Neon", true, Style.Solid, 0x05010F, 0x0D0221, 0x170535, 0xFF2BD6, 0x00F0FF, 0xF4EAFF),
        t("mocha", "Mocha", true, Style.Solid, 0x1B1411, 0x2A1F1A, 0x35271F, 0xE0B58A, 0xC98B5E, 0xF5EBE2),
        t("sand", "Desert Sand", false, Style.Solid, 0xF4E9D8, 0xE8D5B5, 0xFFF8EC, 0xC2561F, 0x8A6D3B, 0x2B2118),
        t("nord", "Nord", true, Style.Solid, 0x2E3440, 0x3B4252, 0x434C5E, 0x88C0D0, 0x81A1C1, 0xECEFF4),
        t("dracula", "Dracula", true, Style.Solid, 0x282A36, 0x1E1F29, 0x343746, 0xBD93F9, 0xFF79C6, 0xF8F8F2),
        t("solar-dark", "Solarized Dark", true, Style.Solid, 0x002B36, 0x073642, 0x0B4452, 0x2AA198, 0xB58900, 0xEEE8D5),
        t("solar-light", "Solarized Light", false, Style.Solid, 0xFDF6E3, 0xEEE8D5, 0xFFFBEF, 0x268BD2, 0xCB4B16, 0x073642),
        t("gruvbox", "Gruvbox", true, Style.Solid, 0x282828, 0x32302F, 0x3C3836, 0xFABD2F, 0xB8BB26, 0xEBDBB2),
        t("tokyo", "Tokyo Night", true, Style.Solid, 0x1A1B26, 0x16161E, 0x24283B, 0x7AA2F7, 0xBB9AF7, 0xC0CAF5),
        t("mocha-cat", "Catppuccin Mocha", true, Style.Solid, 0x1E1E2E, 0x181825, 0x313244, 0xCBA6F7, 0xF5C2E7, 0xCDD6F4),
        t("latte-cat", "Catppuccin Latte", false, Style.Solid, 0xEFF1F5, 0xE6E9EF, 0xFFFFFF, 0x8839EF, 0xEA76CB, 0x4C4F69),
        t("matrix", "Matrix", true, Style.Solid, 0x000900, 0x001A05, 0x022B0A, 0x00FF41, 0x00B33C, 0xCFFFD8),
        t("blood", "Blood Moon", true, Style.Solid, 0x120304, 0x240608, 0x34090D, 0xFF3B47, 0xFF8A5C, 0xFFE9E9),
        t("ice", "Ice", false, Style.Solid, 0xEAF4FF, 0xD8E8F8, 0xFFFFFF, 0x0B6BCB, 0x00B4D8, 0x0B2239),
        t("slate", "Slate", true, Style.Solid, 0x1F2933, 0x2D3A47, 0x364656, 0x9FB3C8, 0x7BDFF2, 0xF0F4F8),
        t("graphite", "Graphite", true, Style.Solid, 0x121212, 0x1C1C1C, 0x262626, 0xFFFFFF, 0x9E9E9E, 0xF5F5F5),
        t("royal", "Royal", true, Style.Solid, 0x120B2E, 0x1E1145, 0x2B1A5C, 0xFFD369, 0xB794F6, 0xF3EEFF),
        t("emerald", "Emerald", true, Style.Solid, 0x04130E, 0x082A1F, 0x0E3B2C, 0x34D399, 0x6EE7B7, 0xE6FFF5),
        t("ruby", "Ruby", true, Style.Solid, 0x1A0509, 0x2C0A12, 0x3D0F1B, 0xFF4D6D, 0xFF8FA3, 0xFFEAF0),
        t("lemon", "Lemon", false, Style.Solid, 0xFFFBE0, 0xFFF3B0, 0xFFFFFF, 0xE6A700, 0xF57C00, 0x2B2400),
        t("coffee-cream", "Coffee Cream", false, Style.Solid, 0xF3E9DD, 0xE4D4C0, 0xFFF8EE, 0x6F4E37, 0xC08552, 0x2E1F14),
        t("sakura", "Sakura", false, Style.Solid, 0xFFEDF3, 0xFFDCE8, 0xFFFFFF, 0xE91E63, 0xFF80AB, 0x3A1A25),
        t("sky-day", "Sky Day", false, Style.Solid, 0xE3F2FF, 0xCFE5FF, 0xFFFFFF, 0x1E6FE0, 0x00A3FF, 0x0D2240),
        // ---- Soft (raised cards) ----
        t("soft-dark", "Soft Dark", true, Style.Soft, 0x23262E, 0x23262E, 0x2B2F38, 0x7CC4FF, 0xA78BFA, 0xE6E9F0),
        t("soft-light", "Soft Light", false, Style.Soft, 0xE6EAF0, 0xE6EAF0, 0xEEF1F6, 0x4C6FFF, 0xFF6B9A, 0x2A3140),
        t("soft-mint", "Soft Mint", false, Style.Soft, 0xDCEFE7, 0xDCEFE7, 0xE8F6F0, 0x0E9F78, 0x3B82F6, 0x143328),
        t("soft-night", "Soft Night", true, Style.Soft, 0x15171F, 0x15171F, 0x1D2029, 0xFFB86B, 0xFF6B9A, 0xF1F3F8),
        t("soft-sand", "Soft Sand", false, Style.Soft, 0xEFE6D6, 0xEFE6D6, 0xF7F0E3, 0x9A6B2F, 0x2E8B6E, 0x33281A),
        t("soft-slate", "Soft Slate", true, Style.Soft, 0x1A1F26, 0x1A1F26, 0x232A33, 0x7DD3FC, 0xA7F3D0, 0xE8EEF5),
        t("soft-rose", "Soft Rosé", false, Style.Soft, 0xF1E3E7, 0xF1E3E7, 0xF8ECEF, 0xD6336C, 0x7C4DFF, 0x3A1F29),
        // ---- Outline ----
        t("line-black", "Line Black", true, Style.Outline, 0x000000, 0x000000, 0x000000, 0x00E5FF, 0xFF4081, 0xFFFFFF),
        t("line-paper", "Line Paper", false, Style.Outline, 0xFFFFFF, 0xF2F2F2, 0xFFFFFF, 0x111111, 0xFF5722, 0x111111),
        t("line-neon", "Neon Line", true, Style.Outline, 0x07030F, 0x0E0620, 0x07030F, 0x39FF14, 0xFF00E5, 0xF4FFF0),
        t("line-gold", "Gold Line", true, Style.Outline, 0x0B0B0B, 0x141414, 0x0B0B0B, 0xE6C158, 0xFFFFFF, 0xF7F2E4),
        t("line-emerald", "Emerald Line", true, Style.Outline, 0x03100B, 0x061A12, 0x03100B, 0x34D399, 0xE9D8A6, 0xE8FFF4),
        t("line-red", "Red Line", true, Style.Outline, 0x0A0203, 0x140507, 0x0A0203, 0xFF3B52, 0xFFB3BD, 0xFFECEF),
        t("line-blue", "Blue Line", true, Style.Outline, 0x061327, 0x0A1E3D, 0x061327, 0x4DA3FF, 0x9AD1FF, 0xEAF3FF),
    )

    /** Themes grouped by style, in display order. */
    val groups: List<Pair<Style, List<AppTheme>>> = Style.entries.map { st -> st to all.filter { it.style == st } }

    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all[0]
}

/** Icon/text colour that stays readable on a primary→secondary gradient fill. */
val AppTheme.onAccent: Color
    get() = if ((primary.luminance() + secondary.luminance()) / 2f > 0.45f) Color.Black.copy(alpha = 0.85f) else Color.White

val LocalSudo = staticCompositionLocalOf<AppTheme> { Themes.all[0] }

/** 0 = animated backgrounds frozen, 1 = animated, 2 = animated and reacting to the music. */
val LocalLiveMode = staticCompositionLocalOf { 2 }

@Composable
fun SudoPlayTheme(
    themeId: String, cover: android.graphics.Bitmap? = null, liveMode: Int = 2,
    fontKey: String = "default", fontScalePct: Int = 100, textHex: String = "", accentHex: String = "",
    content: @Composable () -> Unit,
) {
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
    if (cover != null) {
        val ct = remember(cover, t.id) { coverTheme(t, cover) }
        val spec = tween<Color>(700)
        t = t.copy(
            dark = true,
            bgTop = animateColorAsState(ct.bgTop, spec, label = "c1").value, bgBottom = animateColorAsState(ct.bgBottom, spec, label = "c2").value,
            surface = animateColorAsState(ct.surface, spec, label = "c3").value, primary = animateColorAsState(ct.primary, spec, label = "c4").value,
            secondary = animateColorAsState(ct.secondary, spec, label = "c5").value, text = animateColorAsState(ct.text, spec, label = "c6").value,
        )
    }
    // the user's own text / accent colors win over the theme (and over cover colors)
    Fonts.parse(textHex)?.let { t = t.copy(text = it) }
    Fonts.parse(accentHex)?.let { t = t.copy(primary = it) }
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
    val family = Fonts.byKey(fontKey).family
    val base = androidx.compose.material3.Typography()
    fun androidx.compose.ui.text.TextStyle.f() = if (family == null) this else copy(fontFamily = family)
    val typo = if (family == null) base else base.copy(
        displayLarge = base.displayLarge.f(), displayMedium = base.displayMedium.f(), displaySmall = base.displaySmall.f(),
        headlineLarge = base.headlineLarge.f(), headlineMedium = base.headlineMedium.f(), headlineSmall = base.headlineSmall.f(),
        titleLarge = base.titleLarge.f(), titleMedium = base.titleMedium.f(), titleSmall = base.titleSmall.f(),
        bodyLarge = base.bodyLarge.f(), bodyMedium = base.bodyMedium.f(), bodySmall = base.bodySmall.f(),
        labelLarge = base.labelLarge.f(), labelMedium = base.labelMedium.f(), labelSmall = base.labelSmall.f(),
    )
    val d = androidx.compose.ui.platform.LocalDensity.current
    val dens = if (fontScalePct == 100) d else androidx.compose.ui.unit.Density(d.density, d.fontScale * fontScalePct / 100f)
    CompositionLocalProvider(LocalSudo provides t, LocalLiveMode provides liveMode, androidx.compose.ui.platform.LocalDensity provides dens) {
        MaterialTheme(colorScheme = scheme, typography = typo) {
            androidx.compose.material3.ProvideTextStyle(androidx.compose.material3.LocalTextStyle.current.f(), content)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
