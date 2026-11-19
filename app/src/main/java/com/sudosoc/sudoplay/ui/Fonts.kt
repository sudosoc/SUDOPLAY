package com.sudosoc.sudoplay.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.sudosoc.sudoplay.R

/** Fonts the user can pick for the whole app (Settings → Appearance → Fonts & colors). All are open-source (SIL OFL) and ship inside the app, so nothing is downloaded. */
object Fonts {
    class Def(val key: String, val label: String, val note: String, val family: FontFamily?)

    @OptIn(ExperimentalTextApi::class)
    private fun variable(res: Int) = FontFamily(listOf(400, 500, 600, 700).map {
        Font(res, FontWeight(it), variationSettings = FontVariation.Settings(FontVariation.weight(it)))
    })

    private fun pair(regular: Int, bold: Int) = FontFamily(Font(regular, FontWeight.Normal), Font(bold, FontWeight.Bold))

    val all: List<Def> by lazy {
        listOf(
            Def("default", "System default", "Your phone's own font", null),
            Def("sans", "Sans-serif", "Clean system sans", FontFamily.SansSerif),
            Def("serif", "Serif", "Classic system serif", FontFamily.Serif),
            Def("mono", "Monospace", "Code-style", FontFamily.Monospace),
            Def("cursive", "Handwritten", "System cursive", FontFamily.Cursive),
            Def("cairo", "Cairo", "Arabic + Latin • modern, very readable", variable(R.font.cairo)),
            Def("tajawal", "Tajawal", "Arabic + Latin • light and neat", pair(R.font.tajawal_regular, R.font.tajawal_bold)),
            Def("almarai", "Almarai", "Arabic + Latin • rounded UI", pair(R.font.almarai_regular, R.font.almarai_bold)),
            Def("noto_kufi", "Noto Kufi", "Arabic • geometric Kufi", variable(R.font.noto_kufi)),
            Def("changa", "Changa", "Arabic + Latin • bold and sporty", variable(R.font.changa)),
            Def("reem_kufi", "Reem Kufi", "Arabic + Latin • stylish Kufi", variable(R.font.reem_kufi)),
            Def("el_messiri", "El Messiri", "Arabic + Latin • elegant", variable(R.font.el_messiri)),
            Def("lemonada", "Lemonada", "Arabic + Latin • friendly and round", variable(R.font.lemonada)),
            Def("marhey", "Marhey", "Arabic + Latin • playful", variable(R.font.marhey)),
            Def("amiri", "Amiri", "Arabic + Latin • traditional Naskh", pair(R.font.amiri_regular, R.font.amiri_bold)),
            Def("rubik", "Rubik", "Latin (+ Arabic) • soft corners", variable(R.font.rubik)),
            Def("poppins", "Poppins", "Latin • geometric", pair(R.font.poppins_regular, R.font.poppins_bold)),
            Def("space_grotesk", "Space Grotesk", "Latin • techy", variable(R.font.space_grotesk)),
            Def("jetbrains_mono", "JetBrains Mono", "Latin • monospace for code lovers", variable(R.font.jetbrains_mono)),
        )
    }

    fun byKey(key: String): Def = all.firstOrNull { it.key == key } ?: all.first()

    /** Swatches for text and accent colors. */
    val swatches: List<Pair<String, Int>> = listOf(
        "White" to 0xFFFFFF, "Soft white" to 0xF2F2F2, "Cream" to 0xFFF3D6, "Silver" to 0xB0BEC5, "Charcoal" to 0x222222, "Black" to 0x000000,
        "Gold" to 0xFFD54F, "Amber" to 0xFFB300, "Orange" to 0xFF8A3D, "Peach" to 0xFFB4A2, "Red" to 0xFF5252, "Rose" to 0xF43F5E,
        "Pink" to 0xFF6FB5, "Magenta" to 0xE040FB, "Purple" to 0xB388FF, "Lavender" to 0xC4B5FD, "Indigo" to 0x7C8CFF, "Blue" to 0x4DA3FF,
        "Sky" to 0x5CE1FF, "Teal" to 0x2DD4BF, "Emerald" to 0x10B981, "Mint" to 0x69F0AE, "Green" to 0x76E04A, "Lime" to 0xD4F34A,
    )

    fun parse(hex: String): Color? {
        val h = hex.trim().removePrefix("#")
        if (h.length != 6 || !h.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return Color(0xFF000000 or h.toLong(16))
    }
    fun hexOf(c: Color): String = "#%06X".format(0xFFFFFF and (c.value shr 32).toInt())

    /** WCAG contrast ratio between two colors (1 = same, 21 = black on white). */
    fun contrast(a: Color, b: Color): Float {
        val la = a.luminance() + 0.05f; val lb = b.luminance() + 0.05f
        return if (la > lb) la / lb else lb / la
    }
}
