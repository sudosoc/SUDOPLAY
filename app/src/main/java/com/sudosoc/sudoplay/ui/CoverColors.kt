package com.sudosoc.sudoplay.ui

import android.graphics.Bitmap
import android.graphics.Color as AColor
import androidx.compose.ui.graphics.Color
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Re-colours [base] with the dominant hue of [bmp]: dark tinted backgrounds, bright accents, readable text.
 * (Always a dark scheme — it is the one that keeps every cover legible.)
 */
fun coverTheme(base: AppTheme, bmp: Bitmap): AppTheme {
    val sm = Bitmap.createScaledBitmap(bmp, 16, 16, true)
    val hsv = FloatArray(3)
    var x = 0.0; var y = 0.0; var wSum = 0.0; var sSum = 0.0
    for (i in 0 until 16) for (j in 0 until 16) {
        AColor.colorToHSV(sm.getPixel(i, j), hsv)
        val w = (hsv[1] * hsv[2]).toDouble() + 0.001
        val a = Math.toRadians(hsv[0].toDouble())
        x += cos(a) * w; y += sin(a) * w; wSum += w; sSum += hsv[1] * w
    }
    val hue = ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
    val sat = (sSum / wSum).toFloat().coerceIn(0.25f, 0.9f)
    fun hsv(h: Float, s: Float, v: Float) = Color(AColor.HSVToColor(floatArrayOf((h + 360f) % 360f, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))))
    return base.copy(
        dark = true,
        bgTop = hsv(hue, sat * 0.85f, 0.13f),
        bgBottom = hsv(hue + 22f, sat * 0.9f, 0.27f),
        surface = hsv(hue, sat * 0.7f, 0.20f),
        primary = hsv(hue, sat * 0.55f + 0.1f, 1f),
        secondary = hsv(hue + 42f, sat * 0.5f + 0.1f, 1f),
        text = hsv(hue, 0.05f, 0.98f),
        dynamic = false,
    )
}
