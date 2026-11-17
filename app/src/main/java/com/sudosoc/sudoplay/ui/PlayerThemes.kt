package com.sudosoc.sudoplay.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Where the Now Playing page gets its background from. */
enum class PBg { App, ArtBlur, ArtGradient, FullBleed, Gradient, Blobs }

/** How the cover is framed. */
enum class PShape { Rounded, Squircle, Circle, Poster, Polaroid, Stack }

enum class PControls { Gradient, Outline, Plain }

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
        PlayerTheme("mono", "Monochrome", bg = PBg.Gradient, colors = listOf(c(0x000000), c(0x1B1B1B)), shape = PShape.Squircle,
            controls = PControls.Plain, accent = c(0xFFFFFF), accent2 = c(0x9E9E9E), dark = true, text = c(0xF5F5F5)),
        PlayerTheme("ember", "Ember", bg = PBg.Gradient, colors = listOf(c(0x1A0505), c(0x4A1208)), shape = PShape.Rounded, glow = true,
            accent = c(0xFF9F43), accent2 = c(0xFF4D6D), dark = true, text = c(0xFFF1E6)),
        PlayerTheme("royal-gold", "Royal Gold", bg = PBg.Gradient, colors = listOf(c(0x0A0A0A), c(0x1C1608)), shape = PShape.Circle, spin = true, ring = true,
            controls = PControls.Outline, accent = c(0xE6C158), accent2 = c(0xFFE29A), dark = true, text = c(0xFFF8E1)),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all[0]
}
