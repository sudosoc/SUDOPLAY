package com.sudosoc.sudoplay.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Where the Now Playing page gets its background from. */
enum class PBg { App, ArtBlur, ArtGradient, FullBleed, Gradient, Blobs, Spotlight, Waves, Spectrum, Stars, Mesh, Aurora, Rain, Fireflies, Pulse, Lava, Snow, Matrix, Bokeh, Ripples, Embers, Nebula }

/** How the cover is framed. */
enum class PShape { Rounded, Squircle, Circle, Poster, Polaroid, Stack, Square, Hexagon, Diamond, Wide, Frame }

enum class PControls { Gradient, Outline, Plain, Pill, Glass, Neon }

/**
 * A theme for the player page only. Anything left null falls back to the active app theme.
 * `colors` feed the Gradient / Blobs backgrounds.
 */
@Immutable
data class PlayerTheme(
    val id: String,
    val name: String,
    val bg: PBg = PBg.App,
    val shape: PShape = PShape.Rounded,
    val spin: Boolean = false,
    val ring: Boolean = false,
    val glow: Boolean = false,
    val controls: PControls = PControls.Gradient,
    val colors: List<Color> = emptyList(),
    val text: Color? = null,
    val accent: Color? = null,
    val accent2: Color? = null,
    val dark: Boolean? = null,
)

private fun c(hex: Long) = Color(hex or 0xFF000000)

object PlayerThemes {
    val all: List<PlayerTheme> = listOf(
        PlayerTheme("classic", "Classic (follow app)"),
        PlayerTheme("blur", "Blur", bg = PBg.ArtBlur, shape = PShape.Rounded, dark = true, text = c(0xFFFFFF)),
        PlayerTheme("color-match", "Color Match", bg = PBg.ArtGradient, shape = PShape.Squircle, dark = true, text = c(0xFFFFFF)),
        PlayerTheme("full-bleed", "Full Bleed", bg = PBg.FullBleed, shape = PShape.Rounded, controls = PControls.Plain, dark = true, text = c(0xFFFFFF)),
        PlayerTheme("vinyl-spin", "Vinyl Spin", bg = PBg.Gradient, colors = listOf(c(0x0B0B0F), c(0x1A1A22)), shape = PShape.Circle, spin = true,
            accent = c(0xFFC857), accent2 = c(0xFF7A59), dark = true, text = c(0xF5F5F5)),
        PlayerTheme("halo", "Halo Ring", bg = PBg.App, shape = PShape.Circle, ring = true),
        PlayerTheme("neon", "Neon Glow", bg = PBg.Gradient, colors = listOf(c(0x05010F), c(0x14002E)), shape = PShape.Rounded, glow = true,
            controls = PControls.Outline, accent = c(0xFF2BD6), accent2 = c(0x00F0FF), dark = true, text = c(0xF6EEFF)),
        PlayerTheme("poster", "Poster", bg = PBg.ArtBlur, shape = PShape.Poster, dark = true, text = c(0xFFFFFF)),
        PlayerTheme("polaroid", "Polaroid", bg = PBg.Gradient, colors = listOf(c(0x2B1E16), c(0x14100C)), shape = PShape.Polaroid,
            accent = c(0xFFD59E), accent2 = c(0xFF9F6B), dark = true, text = c(0xFFF3E6)),
        PlayerTheme("stack", "Card Stack", bg = PBg.ArtBlur, shape = PShape.Stack, dark = true, text = c(0xFFFFFF)),
        PlayerTheme("aurora-flow", "Aurora Flow", bg = PBg.Blobs, colors = listOf(c(0x0B1026), c(0x12B8A6), c(0x7C3AED), c(0xEC4899)),
            shape = PShape.Rounded, accent = c(0x7CF3E0), accent2 = c(0xB07CFF), dark = true, text = c(0xF2F4FF)),
        PlayerTheme("retro", "Retro Sunset", bg = PBg.Gradient, colors = listOf(c(0x2D0B45), c(0x8E2A6E), c(0xFF6B35)), shape = PShape.Squircle,
            controls = PControls.Plain, accent = c(0xFFD36B), accent2 = c(0xFF6B9A), dark = true, text = c(0xFFF2E6)),
        PlayerTheme("soft-light", "Soft Light", bg = PBg.Gradient, colors = listOf(c(0xF6F2EC), c(0xE9E2D8)), shape = PShape.Rounded,
            controls = PControls.Outline, accent = c(0x2B5CFF), accent2 = c(0xFF8A3D), dark = false, text = c(0x1B1B1F)),
        PlayerTheme("deep-sea", "Deep Sea", bg = PBg.Blobs, colors = listOf(c(0x021B2B), c(0x0B6E8A), c(0x0A3D91), c(0x00B4D8)),
            shape = PShape.Circle, ring = true, accent = c(0x5EEAD4), accent2 = c(0x38BDF8), dark = true, text = c(0xE8FBFF)),
        PlayerTheme("sakeenah-calm", "Sakeenah Calm", bg = PBg.Gradient, colors = listOf(c(0x06201A), c(0x0F3D31), c(0x1B5A47)), shape = PShape.Circle, ring = true,
            controls = PControls.Outline, accent = c(0xE9D8A6), accent2 = c(0x7FD1AE), dark = true, text = c(0xF3FBF6)),
        PlayerTheme("night-sky", "Night Sky", bg = PBg.Stars, colors = listOf(c(0x050816), c(0x0E1B3D)), shape = PShape.Squircle,
            controls = PControls.Glass, accent = c(0xBFD7FF), accent2 = c(0xFFE08A), dark = true, text = c(0xF1F5FF)),
        PlayerTheme("mesh-dream", "Mesh Dream", bg = PBg.Mesh, colors = listOf(c(0x1B1040), c(0x4C1D95), c(0xDB2777), c(0xF59E0B)), shape = PShape.Rounded,
            controls = PControls.Pill, accent = c(0xFFE4F1), accent2 = c(0xFFD166), dark = true, text = c(0xFFFFFF)),
        PlayerTheme("diamond-lux", "Diamond", bg = PBg.Spotlight, colors = listOf(c(0x0C0C10), c(0x23232E)), shape = PShape.Diamond, glow = true,
            controls = PControls.Neon, accent = c(0xA5F3FC), accent2 = c(0xF0ABFC), dark = true, text = c(0xF5F7FF)),
        PlayerTheme("hex-tech", "Hex Tech", bg = PBg.Spectrum, colors = listOf(c(0x020A0A), c(0x06201F)), shape = PShape.Hexagon,
            controls = PControls.Outline, accent = c(0x2DFFC4), accent2 = c(0x00B3FF), dark = true, text = c(0xE6FFFA)),
        PlayerTheme("mono", "Monochrome", bg = PBg.Gradient, colors = listOf(c(0x000000), c(0x1B1B1B)), shape = PShape.Squircle,
            controls = PControls.Plain, accent = c(0xFFFFFF), accent2 = c(0x9E9E9E), dark = true, text = c(0xF5F5F5)),
        PlayerTheme("ember", "Ember", bg = PBg.Gradient, colors = listOf(c(0x1A0505), c(0x4A1208)), shape = PShape.Rounded, glow = true,
            accent = c(0xFF9F43), accent2 = c(0xFF4D6D), dark = true, text = c(0xFFF1E6)),
        PlayerTheme("royal-gold", "Royal Gold", bg = PBg.Gradient, colors = listOf(c(0x0A0A0A), c(0x1C1608)), shape = PShape.Circle, spin = true, ring = true,
            controls = PControls.Outline, accent = c(0xE6C158), accent2 = c(0xFFE29A), dark = true, text = c(0xFFF8E1)),

        // ---- v0.4
        PlayerTheme("spotlight", "Spotlight", bg = PBg.Spotlight, colors = listOf(c(0x07070B), c(0xFFE8B0)), shape = PShape.Square,
            controls = PControls.Glass, accent = c(0xFFD27A), accent2 = c(0xFF9A5C), dark = true, text = c(0xFFF7E8)),
        PlayerTheme("waves", "Waves", bg = PBg.Waves, colors = listOf(c(0x04122B), c(0x1E6BFF), c(0x00D1FF)), shape = PShape.Rounded,
            accent = c(0x6FE3FF), accent2 = c(0x6C8CFF), dark = true, text = c(0xEAF6FF)),
        PlayerTheme("equalizer", "Equalizer", bg = PBg.Spectrum, colors = listOf(c(0x0A0612), c(0xFF3D81), c(0x7C5CFF)), shape = PShape.Squircle,
            controls = PControls.Pill, accent = c(0xFF3D81), accent2 = c(0x7C5CFF), dark = true, text = c(0xFDF1FF)),
        PlayerTheme("starry", "Starry Night", bg = PBg.Stars, colors = listOf(c(0x050816), c(0x1B1448)), shape = PShape.Circle, spin = true, ring = true,
            controls = PControls.Glass, accent = c(0xC7B8FF), accent2 = c(0x8AB4FF), dark = true, text = c(0xF1EEFF)),
        PlayerTheme("mesh", "Mesh Gradient", bg = PBg.Mesh, colors = listOf(c(0xFF6B9A), c(0x6C7BFF), c(0x2DE0C0), c(0xFFC857)), shape = PShape.Squircle,
            controls = PControls.Glass, accent = c(0xFFFFFF), accent2 = c(0xFFE3F0), dark = true, text = c(0xFFFFFF)),
        PlayerTheme("hexagon", "Hexagon", bg = PBg.Gradient, colors = listOf(c(0x0B1B2B), c(0x12334F)), shape = PShape.Hexagon, glow = true,
            accent = c(0x4DE1FF), accent2 = c(0x3D7BFF), dark = true, text = c(0xE9F7FF)),
        PlayerTheme("diamond", "Diamond", bg = PBg.Mesh, colors = listOf(c(0x1A0B2E), c(0x6A2BD9), c(0xE23DA0), c(0x1C1240)), shape = PShape.Diamond,
            controls = PControls.Neon, accent = c(0xFF7AD9), accent2 = c(0x9E7BFF), dark = true, text = c(0xFFF0FB)),
        PlayerTheme("cinema", "Cinema", bg = PBg.ArtBlur, shape = PShape.Wide, controls = PControls.Plain, dark = true, text = c(0xFFFFFF)),
        PlayerTheme("gallery", "Gallery Frame", bg = PBg.Gradient, colors = listOf(c(0x1E1A16), c(0x0F0D0B)), shape = PShape.Frame,
            controls = PControls.Outline, accent = c(0xD9B26B), accent2 = c(0xF2D9A0), dark = true, text = c(0xFBF3E4)),
        PlayerTheme("glass-art", "Frosted Glass", bg = PBg.ArtBlur, shape = PShape.Rounded, controls = PControls.Glass, dark = true, text = c(0xFFFFFF)),
        PlayerTheme("pop", "Pop Art", bg = PBg.Gradient, colors = listOf(c(0xFFE45C), c(0xFF9F1C)), shape = PShape.Square, controls = PControls.Pill,
            accent = c(0x111111), accent2 = c(0xE63946), dark = false, text = c(0x141414)),
        PlayerTheme("neon-city", "Neon City", bg = PBg.Spectrum, colors = listOf(c(0x03040F), c(0x00F0FF), c(0xFF2BD6)), shape = PShape.Rounded, glow = true,
            controls = PControls.Neon, accent = c(0x00F0FF), accent2 = c(0xFF2BD6), dark = true, text = c(0xEFFFFF)),
        PlayerTheme("sakura", "Sakura", bg = PBg.Mesh, colors = listOf(c(0xFFE4EE), c(0xFFC2D9), c(0xF3D6FF), c(0xFFF1F6)), shape = PShape.Circle, ring = true,
            controls = PControls.Pill, accent = c(0xE0457B), accent2 = c(0xB84DFF), dark = false, text = c(0x3A1727)),
        PlayerTheme("forest", "Forest", bg = PBg.Spotlight, colors = listOf(c(0x04120B), c(0x2E8B57)), shape = PShape.Polaroid,
            accent = c(0x8EF0B4), accent2 = c(0x4FD18B), dark = true, text = c(0xEAFFF3)),
        PlayerTheme("midnight", "Midnight Ocean", bg = PBg.Waves, colors = listOf(c(0x02060F), c(0x0B3D91), c(0x7C3AED)), shape = PShape.Circle, ring = true,
            controls = PControls.Glass, accent = c(0x93C5FD), accent2 = c(0xC4B5FD), dark = true, text = c(0xEEF4FF)),
        PlayerTheme("candy", "Candy", bg = PBg.Mesh, colors = listOf(c(0xFFD6F5), c(0xC7F0FF), c(0xFFF3B8), c(0xD9C7FF)), shape = PShape.Poster,
            controls = PControls.Pill, accent = c(0xFF5CAD), accent2 = c(0x5C9CFF), dark = false, text = c(0x2B1B3A)),
        PlayerTheme("paper", "Paper", bg = PBg.Gradient, colors = listOf(c(0xFAF7F2), c(0xEFE9DF)), shape = PShape.Frame,
            controls = PControls.Plain, accent = c(0x1B1B1F), accent2 = c(0x8A6F4D), dark = false, text = c(0x1B1B1F)),
        PlayerTheme("terminal", "Terminal", bg = PBg.Spectrum, colors = listOf(c(0x020B04), c(0x39FF6A), c(0x0F9D3A)), shape = PShape.Square,
            controls = PControls.Outline, accent = c(0x39FF6A), accent2 = c(0x9BFFB8), dark = true, text = c(0xC9FFD8)),
        PlayerTheme("arctic", "Arctic", bg = PBg.Waves, colors = listOf(c(0xEAF6FF), c(0x7CC4FF), c(0xBFE3FF)), shape = PShape.Squircle,
            controls = PControls.Pill, accent = c(0x1E78D6), accent2 = c(0x4FB3FF), dark = false, text = c(0x0F2A44)),
        // ---- v0.11: live backgrounds (they move; the Pulse / rain / code-rain layers also react to the music)
        PlayerTheme("aurora-live", "Aurora Live", bg = PBg.Aurora, colors = listOf(c(0x040A18), c(0x2DFFB3), c(0x6C5CFF), c(0xFF5CC8), c(0x38BDF8)), shape = PShape.Squircle,
            controls = PControls.Glass, accent = c(0x7CF3E0), accent2 = c(0xB07CFF), dark = true, text = c(0xF2F8FF)),
        PlayerTheme("rain-night", "Rainy Night", bg = PBg.Rain, colors = listOf(c(0x080D17), c(0x8FB4E3), c(0x3E5C85)), shape = PShape.Rounded,
            controls = PControls.Glass, accent = c(0x9CC4F5), accent2 = c(0x6C8CC0), dark = true, text = c(0xEAF2FF)),
        PlayerTheme("fireflies", "Fireflies", bg = PBg.Fireflies, colors = listOf(c(0x03100A), c(0xE8F56B), c(0x7CF0A0)), shape = PShape.Circle, ring = true,
            controls = PControls.Outline, accent = c(0xE8F56B), accent2 = c(0x7CF0A0), dark = true, text = c(0xF1FFE8)),
        PlayerTheme("pulse-neon", "Neon Pulse", bg = PBg.Pulse, colors = listOf(c(0x07030F), c(0xFF2BD6), c(0x00F0FF)), shape = PShape.Rounded, glow = true,
            controls = PControls.Neon, accent = c(0xFF2BD6), accent2 = c(0x00F0FF), dark = true, text = c(0xFAF0FF)),
        PlayerTheme("pulse-gold", "Golden Pulse", bg = PBg.Pulse, colors = listOf(c(0x0B0905), c(0xE6C158), c(0xFFF1B8)), shape = PShape.Circle, spin = true, ring = true,
            controls = PControls.Outline, accent = c(0xE6C158), accent2 = c(0xFFE29A), dark = true, text = c(0xFFF8E1)),
        PlayerTheme("lava-lamp", "Lava Lamp", bg = PBg.Lava, colors = listOf(c(0x1A0510), c(0xFF5C74), c(0xFFB36B), c(0xB07CFF)), shape = PShape.Squircle,
            controls = PControls.Pill, accent = c(0xFFD0A8), accent2 = c(0xFF8FB1), dark = true, text = c(0xFFF1F4)),
        PlayerTheme("snowfall", "Snowfall", bg = PBg.Snow, colors = listOf(c(0x0B1A2E), c(0x1B3A5C)), shape = PShape.Rounded,
            controls = PControls.Glass, accent = c(0xBFE3FF), accent2 = c(0x8EC5FF), dark = true, text = c(0xF2F8FF)),
        PlayerTheme("code-rain", "Code Rain", bg = PBg.Matrix, colors = listOf(c(0x000A02), c(0x00FF41)), shape = PShape.Square,
            controls = PControls.Outline, accent = c(0x00FF41), accent2 = c(0x9BFFB8), dark = true, text = c(0xCFFFD8)),
        PlayerTheme("bokeh-gold", "Bokeh Gold", bg = PBg.Bokeh, colors = listOf(c(0x120C06), c(0xFFC857), c(0xFF8A3D), c(0xFFE9A8)), shape = PShape.Circle, spin = true, ring = true,
            controls = PControls.Glass, accent = c(0xFFD27A), accent2 = c(0xFF9A5C), dark = true, text = c(0xFFF4E0)),
        PlayerTheme("ripples", "Ripples", bg = PBg.Ripples, colors = listOf(c(0x031A24), c(0x5EEAD4), c(0x38BDF8)), shape = PShape.Circle, ring = true,
            controls = PControls.Glass, accent = c(0x5EEAD4), accent2 = c(0x38BDF8), dark = true, text = c(0xE8FBFF)),
        PlayerTheme("embers", "Embers", bg = PBg.Embers, colors = listOf(c(0x120504), c(0xFF6A1F), c(0xFFC857)), shape = PShape.Rounded, glow = true,
            controls = PControls.Gradient, accent = c(0xFF9F43), accent2 = c(0xFF4D6D), dark = true, text = c(0xFFF1E6)),
        PlayerTheme("nebula", "Nebula", bg = PBg.Nebula, colors = listOf(c(0x05030F), c(0x7C3AED), c(0xEC4899), c(0x38BDF8)), shape = PShape.Diamond,
            controls = PControls.Glass, accent = c(0xD8B4FE), accent2 = c(0xF9A8D4), dark = true, text = c(0xF7F0FF)),
        PlayerTheme("sakeenah-lights", "Sakeenah Lights", bg = PBg.Fireflies, colors = listOf(c(0x06201A), c(0xE9D8A6), c(0x7FD1AE)), shape = PShape.Circle, ring = true,
            controls = PControls.Outline, accent = c(0xE9D8A6), accent2 = c(0x7FD1AE), dark = true, text = c(0xF3FBF6)),
        PlayerTheme("northern", "Northern Lights", bg = PBg.Aurora, colors = listOf(c(0x02101A), c(0x34D399), c(0x22D3EE), c(0xA78BFA)), shape = PShape.Circle, spin = true,
            controls = PControls.Pill, accent = c(0x6EE7B7), accent2 = c(0x67E8F9), dark = true, text = c(0xEFFFFB)),
        PlayerTheme("vapor", "Vaporwave", bg = PBg.Stars, colors = listOf(c(0x1B0B3B), c(0x5B1E8F)), shape = PShape.Diamond,
            controls = PControls.Neon, accent = c(0xFF71CE), accent2 = c(0x01CDFE), dark = true, text = c(0xFFE9FB)),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all[0]
}

/** The theme the player page really uses: the player theme laid over the app theme. */
fun effectiveTheme(app: AppTheme, pt: PlayerTheme): AppTheme {
    val custom = pt.colors.isNotEmpty() && pt.bg != PBg.App
    return app.copy(
        primary = pt.accent ?: app.primary, secondary = pt.accent2 ?: app.secondary,
        text = pt.text ?: app.text, dark = pt.dark ?: app.dark,
        bgTop = if (custom) pt.colors.first() else app.bgTop,
        bgBottom = if (custom) pt.colors.last() else app.bgBottom,
    )
}
