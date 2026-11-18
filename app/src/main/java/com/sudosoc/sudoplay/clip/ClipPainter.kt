package com.sudosoc.sudoplay.clip

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** What the clip looks like. Everything here is chosen on the clip screen. */
class ClipSpec(
    val title: String,
    val artist: String,
    val style: Int = 0,          // 0 bars, 1 mirror, 2 ring, 3 wave, 4 dots
    val bg: Int = 0,             // 0 blurred cover, 1 gradient, 2 black
    val showLyrics: Boolean = true,
    val showTitle: Boolean = true,
    val credit: String = "",
    val accent: Int = 0xFF7CF3E0.toInt(),
    val accent2: Int = 0xFFB07CFF.toInt(),
    val dark: Int = 0xFF0B1026.toInt(),
)

/**
 * Draws one frame of the clip (9:16) onto any Canvas. The same code runs in the on-screen preview and in the video
 * renderer, so what you see is what you get. Layout keeps the lower fifth and the right edge free for TikTok's own buttons.
 */
class ClipPainter(private val spec: ClipSpec, private val art: Bitmap?) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()
    private val blurSrc: Bitmap? = art?.let { Bitmap.createScaledBitmap(it, 28, 28, true) }
    private var cachedKey = ""
    private var cachedLayout: StaticLayout? = null

    private fun layout(text: String, size: Float, width: Int, bold: Boolean, color: Int, maxLines: Int): StaticLayout {
        val key = "$text|$size|$width|$bold|$color|$maxLines"
        if (key == cachedKey && cachedLayout != null) return cachedLayout!!
        tp.textSize = size
        tp.isFakeBoldText = bold
        tp.color = color
        tp.setShadowLayer(size * 0.12f, 0f, size * 0.05f, 0x99000000.toInt())
        val l = StaticLayout.Builder.obtain(text, 0, text.length, tp, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END)
            .setLineSpacing(0f, 1.08f).build()
        cachedKey = key; cachedLayout = l
        return l
    }

    private val titleCache = HashMap<String, StaticLayout>()
    private fun cachedText(id: String, text: String, size: Float, width: Int, bold: Boolean, color: Int, maxLines: Int): StaticLayout =
        titleCache.getOrPut("$id|$text|$size|$width|$color") {
            val t = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size; isFakeBoldText = bold; this.color = color; setShadowLayer(size * 0.12f, 0f, size * 0.05f, 0x99000000.toInt())
            }
            StaticLayout.Builder.obtain(text, 0, text.length, t, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).build()
        }

    /**
     * @param prog 0..1 position inside the clip
     * @param levels 32 band levels, 0..1
     * @param line current lyric line (null = none), [lineAge] seconds since it appeared
     */
    fun draw(c: Canvas, w: Int, h: Int, prog: Float, levels: FloatArray, line: String?, lineAge: Float) {
        val u = w / 1080f
        val bass = (0 until 5).map { levels.getOrElse(it) { 0f } }.average().toFloat().coerceIn(0f, 1f)

        // ---------------------------------------------------------------- background
        when {
            spec.bg == 2 -> c.drawColor(Color.BLACK)
            spec.bg == 1 || blurSrc == null -> {
                p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), spec.dark, adjust(spec.accent2, 0.45f), Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p); p.shader = null
            }
            else -> {
                val zoom = 1.12f + 0.10f * prog + 0.02f * bass
                c.save()
                c.scale(zoom, zoom, w / 2f, h / 2f)
                rect.set(-w * 0.1f, -h * 0.05f, w * 1.1f, h * 1.05f)
                c.drawBitmap(blurSrc, null, rect, p)
                c.restore()
                p.color = 0x99000000.toInt(); c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            }
        }
        // soft vignette so text always reads
        p.shader = LinearGradient(0f, h * 0.45f, 0f, h.toFloat(), 0x00000000, 0xCC000000.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, h * 0.45f, w.toFloat(), h.toFloat(), p); p.shader = null

        // ---------------------------------------------------------------- cover
        val side = 600f * u * (1f + 0.035f * bass)
        val cx = w / 2f
        val cy = h * 0.27f
        rect.set(cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2)
        val ring = spec.style == 2
        if (ring) drawRing(c, cx, cy, side / 2, levels, prog, u)
        if (art != null) {
            c.save()
            path.reset()
            if (ring) path.addCircle(cx, cy, side / 2, Path.Direction.CW) else path.addRoundRect(rect, 44f * u, 44f * u, Path.Direction.CW)
            p.color = 0x55000000; p.setShadowLayer(40f * u, 0f, 18f * u, 0x88000000.toInt())
            c.drawPath(path, p); p.clearShadowLayer()
            c.clipPath(path)
            c.drawBitmap(art, null, rect, p)
            c.restore()
        } else {
            p.color = adjust(spec.accent, 0.5f)
            c.drawRoundRect(rect, 44f * u, 44f * u, p)
        }

        // ---------------------------------------------------------------- spectrum
        val sy = h * 0.515f
        val sh = 170f * u
        if (!ring) when (spec.style) {
            1 -> mirror(c, 90f * u, sy, w - 180f * u, sh, levels, u)
            3 -> wave(c, 90f * u, sy, w - 180f * u, sh, levels, u)
            4 -> dots(c, 90f * u, sy, w - 180f * u, sh, levels, u)
            else -> bars(c, 90f * u, sy, w - 180f * u, sh, levels, u)
        }

        // ---------------------------------------------------------------- texts
        val textW = (w - 220f * u).toInt()
        var ty = h * 0.615f
        if (spec.showTitle) {
            val tl = cachedText("title", spec.title, 58f * u, textW, true, Color.WHITE, 2)
            c.save(); c.translate((w - textW) / 2f, ty); tl.draw(c); c.restore(); ty += tl.height + 8f * u
            val al = cachedText("artist", spec.artist, 40f * u, textW, false, 0xCCFFFFFF.toInt(), 1)
            c.save(); c.translate((w - textW) / 2f, ty); al.draw(c); c.restore(); ty += al.height
        }
        if (spec.showLyrics && !line.isNullOrBlank()) {
            val a = (lineAge / 0.28f).coerceIn(0f, 1f)
            val ll = layout(line, 62f * u, textW, true, adjust(Color.WHITE, 1f), 3)
            val ly = max(ty + 40f * u, h * 0.70f)
            c.save()
            c.translate((w - textW) / 2f, ly + (1f - a) * 26f * u)
            p.alpha = 255
            c.saveLayerAlpha(0f, -20f * u, textW.toFloat(), ll.height + 20f * u, (a * 255).toInt())
            ll.draw(c)
            c.restore(); c.restore()
        }
        if (spec.credit.isNotBlank()) {
            val cl = cachedText("credit", spec.credit, 36f * u, textW, false, 0xB3FFFFFF.toInt(), 1)
            c.save(); c.translate((w - textW) / 2f, h * 0.835f); cl.draw(c); c.restore()
        }

        // ---------------------------------------------------------------- progress line
        p.color = 0x33FFFFFF; c.drawRect(0f, 0f, w.toFloat(), 8f * u, p)
        p.color = spec.accent; c.drawRect(0f, 0f, w * prog.coerceIn(0f, 1f), 8f * u, p)
    }

    private fun bars(c: Canvas, x: Float, y: Float, w: Float, h: Float, lv: FloatArray, u: Float) {
        val n = 40; val gap = 8f * u; val bw = (w - gap * (n - 1)) / n
        for (i in 0 until n) {
            val v = lv.getOrElse(i * lv.size / n) { 0f }
            val bh = max(8f * u, v * h)
            p.color = mix(spec.accent2, spec.accent, v)
            rect.set(x + i * (bw + gap), y + h - bh, x + i * (bw + gap) + bw, y + h)
            c.drawRoundRect(rect, bw / 2, bw / 2, p)
        }
    }

    private fun mirror(c: Canvas, x: Float, y: Float, w: Float, h: Float, lv: FloatArray, u: Float) {
        val n = 40; val gap = 8f * u; val bw = (w - gap * (n - 1)) / n; val mid = y + h / 2
        for (i in 0 until n) {
            val v = lv.getOrElse(i * lv.size / n) { 0f }
            val bh = max(8f * u, v * h / 2)
            p.color = mix(spec.accent2, spec.accent, v)
            rect.set(x + i * (bw + gap), mid - bh, x + i * (bw + gap) + bw, mid + bh)
            c.drawRoundRect(rect, bw / 2, bw / 2, p)
        }
    }

    private fun wave(c: Canvas, x: Float, y: Float, w: Float, h: Float, lv: FloatArray, u: Float) {
        val n = lv.size; val mid = y + h / 2
        path.reset()
        for (i in 0 until n) {
            val px = x + w * i / (n - 1)
            val py = mid + (if (i % 2 == 0) -1 else 1) * lv[i] * h * 0.5f
            if (i == 0) path.moveTo(px, py) else {
                val qx = x + w * (i - 0.5f) / (n - 1)
                path.quadTo(qx, mid + (if (i % 2 == 0) 1 else -1) * lv[i - 1] * h * 0.2f, px, py)
            }
        }
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        p.color = adjustAlpha(spec.accent, 0.28f); p.strokeWidth = 22f * u; c.drawPath(path, p)
        p.color = spec.accent; p.strokeWidth = 7f * u; c.drawPath(path, p)
        p.style = Paint.Style.FILL
    }

    private fun dots(c: Canvas, x: Float, y: Float, w: Float, h: Float, lv: FloatArray, u: Float) {
        val cols = 24; val rows = 7; val cw = w / cols; val rh = h / rows; val r = min(cw, rh) * 0.34f
        for (i in 0 until cols) {
            val v = lv.getOrElse(i * lv.size / cols) { 0f }
            val lit = (v * rows).toInt().coerceIn(1, rows)
            for (j in 0 until rows) {
                p.color = if (j < lit) mix(spec.accent2, spec.accent, j / rows.toFloat() + 0.2f) else 0x22FFFFFF
                c.drawCircle(x + cw * (i + 0.5f), y + h - rh * (j + 0.5f), r, p)
            }
        }
    }

    private fun drawRing(c: Canvas, cx: Float, cy: Float, r0: Float, lv: FloatArray, prog: Float, u: Float) {
        val n = 72
        val spin = prog * 2f * PI.toFloat() * 0.6f
        p.strokeCap = Paint.Cap.ROUND
        p.style = Paint.Style.STROKE; p.strokeWidth = 9f * u
        for (i in 0 until n) {
            val a = spin + 2f * PI.toFloat() * i / n
            val idx = (if (i < n / 2) i else n - 1 - i) * lv.size / (n / 2)
            val v = lv.getOrElse(idx.coerceIn(0, lv.size - 1)) { 0f }
            val r1 = r0 + 22f * u
            val r2 = r1 + 14f * u + v * 120f * u
            p.color = mix(spec.accent2, spec.accent, v)
            c.drawLine(cx + cos(a) * r1, cy + sin(a) * r1, cx + cos(a) * r2, cy + sin(a) * r2, p)
        }
        p.style = Paint.Style.FILL
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        return Color.argb(255, (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(), (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(), (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt())
    }
    private fun adjust(c: Int, v: Float): Int { val hsv = FloatArray(3); Color.colorToHSV(c, hsv); hsv[2] = (hsv[2] * v).coerceIn(0f, 1f); return Color.HSVToColor(hsv) }
    private fun adjustAlpha(c: Int, a: Float) = Color.argb((a * 255).toInt(), Color.red(c), Color.green(c), Color.blue(c))
}
