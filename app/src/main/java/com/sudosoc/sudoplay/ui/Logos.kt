package com.sudosoc.sudoplay.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.sudosoc.sudoplay.R

/** The logo pack: 1..20 are the new logos (1 = the main SUDOPLAY mark), 0 = the original icon. The chosen one is also the launcher icon. */
object Logos {
    val names = listOf(
        "Classic", "SUDOPLAY mark", "Equalizer tile", "Neon ring", "Nebula", "Sound wave", "Note play", "Vinyl", "Spectrum", "Headphones", "White wave",
        "Crystal", "Vortex", "Big note", "S play", "Hexagon", "Bars circle", "Play bars", "Orb", "Triangle note", "Headphone bars",
    )
    private val res = listOf(
        0, R.drawable.logo_01, R.drawable.logo_02, R.drawable.logo_03, R.drawable.logo_04, R.drawable.logo_05, R.drawable.logo_06, R.drawable.logo_07,
        R.drawable.logo_08, R.drawable.logo_09, R.drawable.logo_10, R.drawable.logo_11, R.drawable.logo_12, R.drawable.logo_13, R.drawable.logo_14,
        R.drawable.logo_15, R.drawable.logo_16, R.drawable.logo_17, R.drawable.logo_18, R.drawable.logo_19, R.drawable.logo_20,
    )
    val count get() = res.size - 1
    fun drawable(n: Int) = res.getOrElse(n) { R.drawable.logo_01 }.takeIf { it != 0 } ?: R.drawable.logo_01

    private fun alias(n: Int) = if (n == 0) "Classic" else "Logo%02d".format(n)

    /** Enables the launcher entry of logo [n] and disables the others (the launcher refreshes the icon after a moment). */
    fun applyLauncher(ctx: Context, n: Int) {
        val pm = ctx.packageManager
        val want = alias(n.coerceIn(0, count))
        for (i in 0..count) {
            val cn = ComponentName(ctx.packageName, "com.sudosoc.sudoplay." + alias(i))
            val state = if (alias(i) == want) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            runCatching { pm.setComponentEnabledSetting(cn, state, PackageManager.DONT_KILL_APP) }
        }
    }

    val brand = Brush.horizontalGradient(listOf(Color(0xFF38BDF8), Color(0xFF6C63FF), Color(0xFFB44CFF)))
}

/** The chosen logo as a rounded picture. */
@Composable
fun LogoImage(n: Int, size: Dp, modifier: Modifier = Modifier) {
    if (n == 0) {
        androidx.compose.foundation.layout.Box(
            modifier.size(size).clip(RoundedCornerShape(size / 4)).androidxBg(),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) { androidx.compose.material3.Icon(I.LibraryMusic, null, tint = LocalSudo.current.onAccent, modifier = Modifier.size(size * 0.55f)) }
    } else Image(painterResource(Logos.drawable(n)), null, modifier.size(size).clip(RoundedCornerShape(size / 4)))
}

@Composable
private fun Modifier.androidxBg(): Modifier {
    val t = LocalSudo.current
    return this.then(Modifier.background(Brush.linearGradient(listOf(t.primary, t.secondary))))
}

/** "SUDO" in white + the second word in the brand gradient — the same look as the logo sheet. */
@Composable
fun Wordmark(first: String, second: String, size: TextUnit = 28.sp, firstColor: Color = Color(0xFFEDEFF7)) {
    val fam = Fonts.byKey("poppins").family
    Row {
        Text(first, style = TextStyle(fontFamily = fam, fontWeight = FontWeight.Bold, fontSize = size, color = firstColor))
        Text(second, style = TextStyle(fontFamily = fam, fontWeight = FontWeight.Bold, fontSize = size, brush = Logos.brand))
    }
}
