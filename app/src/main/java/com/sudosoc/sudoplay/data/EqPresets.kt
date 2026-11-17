package com.sudosoc.sudoplay.data

/** Preset curves defined over 5 points (low → high, -1..1) and interpolated to the device's band count. */
object EqPresets {
    val curves: Map<String, List<Float>> = linkedMapOf(
        "Flat" to listOf(0f, 0f, 0f, 0f, 0f),
        "Bass boost" to listOf(1f, .7f, .2f, 0f, 0f),
        "Bass + treble" to listOf(.8f, .3f, -.2f, .3f, .8f),
        "Treble" to listOf(0f, 0f, .2f, .7f, 1f),
        "Vocal" to listOf(-.4f, .1f, .7f, .5f, -.1f),
        "Rock" to listOf(.7f, .3f, -.3f, .4f, .8f),
        "Pop" to listOf(-.2f, .4f, .6f, .3f, -.2f),
        "Electronic" to listOf(.8f, .4f, 0f, .3f, .7f),
        "Classical" to listOf(.5f, .3f, -.2f, .2f, .5f),
        "Late night" to listOf(-.3f, 0f, .2f, 0f, -.3f),
    )

    fun levels(name: String, bands: Int, maxMb: Int): List<Int> {
        val c = curves[name] ?: curves.values.first()
        return List(bands) { i ->
            val x = if (bands <= 1) 0f else i.toFloat() / (bands - 1) * (c.size - 1)
            val lo = x.toInt().coerceAtMost(c.size - 1)
            val hi = (lo + 1).coerceAtMost(c.size - 1)
            val f = x - lo
            ((c[lo] * (1 - f) + c[hi] * f) * maxMb * 0.7f).toInt()
        }
    }
}
