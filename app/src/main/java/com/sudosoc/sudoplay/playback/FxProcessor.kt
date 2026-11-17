@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sudosoc.sudoplay.playback

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import com.sudosoc.sudoplay.data.FxState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Parameters + analysis results shared between the audio thread and the UI (same process). */
object FxBus {
    const val BANDS = 32

    @Volatile var levels = FloatArray(BANDS)
    @Volatile var lastUpdate = 0L

    @Volatile var enabled = false
    @Volatile var widthOn = false
    @Volatile var width = 1f
    @Volatile var reverbOn = false
    @Volatile var room = 0.5f
    @Volatile var damp = 0.4f
    @Volatile var reverbMix = 0.25f
    @Volatile var echoOn = false
    @Volatile var echoMs = 320
    @Volatile var echoFb = 0.35f
    @Volatile var echoMix = 0.3f

    fun set(s: FxState) {
        enabled = s.enabled
        widthOn = s.widthOn; width = s.width / 100f
        reverbOn = s.reverbOn; room = s.room / 100f; damp = s.damp / 100f; reverbMix = s.reverbMix / 100f
        echoOn = s.echoOn; echoMs = s.echoMs; echoFb = s.echoFb / 100f; echoMix = s.echoMix / 100f
    }
}

/**
 * Pass-through PCM processor (16-bit, mono/stereo) that adds stereo width, a Schroeder-style reverb and a feedback echo,
 * and feeds a 1024-point FFT so the UI can show the live spectrum. No permissions needed.
 */
class FxProcessor : BaseAudioProcessor() {
    private class Comb(len: Int) { val buf = FloatArray(len.coerceAtLeast(8)); var idx = 0; var store = 0f }
    private class Allpass(len: Int) { val buf = FloatArray(len.coerceAtLeast(8)); var idx = 0 }

    private var sr = 44100
    private var ch = 2
    private var echoL = FloatArray(1)
    private var echoR = FloatArray(1)
    private var wp = 0
    private var combL = emptyArray<Comb>()
    private var combR = emptyArray<Comb>()
    private var apL = emptyArray<Allpass>()
    private var apR = emptyArray<Allpass>()

    private val ring = FloatArray(N)
    private var rp = 0
    private val re = FloatArray(N)
    private val im = FloatArray(N)
    private val win = FloatArray(N) { (0.5 - 0.5 * cos(2.0 * PI * it / (N - 1))).toFloat() }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..2) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sr = inputAudioFormat.sampleRate
        ch = inputAudioFormat.channelCount
        val sc = sr / 44100f
        echoL = FloatArray(sr + 1); echoR = FloatArray(sr + 1); wp = 0
        val combLens = intArrayOf(1116, 1188, 1277, 1356)
        val apLens = intArrayOf(556, 441)
        combL = Array(4) { Comb((combLens[it] * sc).toInt()) }
        combR = Array(4) { Comb(((combLens[it] + 23) * sc).toInt()) }
        apL = Array(2) { Allpass((apLens[it] * sc).toInt()) }
        apR = Array(2) { Allpass(((apLens[it] + 23) * sc).toInt()) }
        return inputAudioFormat
    }

    override fun onFlush() {
        echoL.fill(0f); echoR.fill(0f); wp = 0
        combL.forEach { it.buf.fill(0f); it.idx = 0; it.store = 0f }
        combR.forEach { it.buf.fill(0f); it.idx = 0; it.store = 0f }
        apL.forEach { it.buf.fill(0f); it.idx = 0 }
        apR.forEach { it.buf.fill(0f); it.idx = 0 }
        rp = 0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val n = inputBuffer.remaining()
        if (n == 0) return
        val inB = inputBuffer.duplicate().order(ByteOrder.nativeOrder())
        val out = replaceOutputBuffer(n)
        val frames = n / (2 * ch)

        val on = FxBus.enabled
        val wOn = on && FxBus.widthOn && ch == 2
        val width = FxBus.width
        val rOn = on && FxBus.reverbOn
        val fb = 0.70f + 0.26f * FxBus.room
        val damp = 0.05f + 0.6f * FxBus.damp
        val rm = FxBus.reverbMix
        val eOn = on && FxBus.echoOn
        val delay = (FxBus.echoMs * sr / 1000).coerceIn(1, echoL.size - 1)
        val efb = FxBus.echoFb
        val emix = FxBus.echoMix

        var pos = inB.position()
        for (f in 0 until frames) {
            var l = inB.getShort(pos) / 32768f; pos += 2
            var r = l
            if (ch == 2) { r = inB.getShort(pos) / 32768f; pos += 2 }

            if (wOn) {
                val m = (l + r) * 0.5f
                val s = (l - r) * 0.5f * width
                l = m + s; r = m - s
            }

            // echo (the delay line always runs so toggling it never plays stale audio)
            val rpos = (wp - delay + echoL.size) % echoL.size
            val dl = echoL[rpos]
            val dr = echoR[rpos]
            echoL[wp] = l + (if (eOn) efb * dl else 0f)
            echoR[wp] = r + (if (eOn) efb * dr else 0f)
            if (++wp >= echoL.size) wp = 0
            if (eOn) { l += emix * dl; r += emix * dr }

            if (rOn) {
                val inp = (l + r) * 0.5f * 0.07f
                var wl = 0f
                var wr = 0f
                for (c in combL) {
                    val o = c.buf[c.idx]; c.store = o * (1f - damp) + c.store * damp
                    c.buf[c.idx] = inp + c.store * fb; if (++c.idx >= c.buf.size) c.idx = 0; wl += o
                }
                for (c in combR) {
                    val o = c.buf[c.idx]; c.store = o * (1f - damp) + c.store * damp
                    c.buf[c.idx] = inp + c.store * fb; if (++c.idx >= c.buf.size) c.idx = 0; wr += o
                }
                for (a in apL) { val b = a.buf[a.idx]; val o = -wl + b; a.buf[a.idx] = wl + b * 0.5f; if (++a.idx >= a.buf.size) a.idx = 0; wl = o }
                for (a in apR) { val b = a.buf[a.idx]; val o = -wr + b; a.buf[a.idx] = wr + b * 0.5f; if (++a.idx >= a.buf.size) a.idx = 0; wr = o }
                l = l * (1f - 0.25f * rm) + wl * rm
                r = r * (1f - 0.25f * rm) + wr * rm
            }

            ring[rp] = (l + r) * 0.5f
            if (++rp >= N) { rp = 0; analyze() }

            out.putShort((l * 32767f).toInt().coerceIn(-32768, 32767).toShort())
            if (ch == 2) out.putShort((r * 32767f).toInt().coerceIn(-32768, 32767).toShort())
        }
        inputBuffer.position(inputBuffer.limit())
        out.flip()
    }

    private fun analyze() {
        for (i in 0 until N) { re[i] = ring[i] * win[i]; im[i] = 0f }
        fft()
        val bands = FxBus.BANDS
        val res = FloatArray(bands)
        val binHz = sr / N.toFloat()
        for (b in 0 until bands) {
            val f0 = 40f * (16000f / 40f).pow(b / bands.toFloat())
            val f1 = 40f * (16000f / 40f).pow((b + 1) / bands.toFloat())
            val i0 = (f0 / binHz).toInt().coerceIn(1, N / 2 - 1)
            val i1 = (f1 / binHz).toInt().coerceIn(i0, N / 2 - 1)
            var m = 0f
            for (i in i0..i1) { val mag = sqrt(re[i] * re[i] + im[i] * im[i]); if (mag > m) m = mag }
            val db = 20f * log10(m / 256f + 1e-6f) + b * 0.55f
            res[b] = ((db + 72f) / 60f).coerceIn(0f, 1f)
        }
        FxBus.levels = res
        FxBus.lastUpdate = SystemClock.elapsedRealtime()
    }

    private fun fft() {
        var j = 0
        for (i in 1 until N) {
            var bit = N shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val tr = re[i]; re[i] = re[j]; re[j] = tr; val ti = im[i]; im[i] = im[j]; im[j] = ti }
        }
        var len = 2
        while (len <= N) {
            val ang = (-2.0 * PI / len)
            val wlr = cos(ang).toFloat(); val wli = sin(ang).toFloat()
            var i = 0
            while (i < N) {
                var wr = 1f; var wi = 0f
                val half = len / 2
                for (k in 0 until half) {
                    val a = i + k; val b = a + half
                    val vr = re[b] * wr - im[b] * wi
                    val vi = re[b] * wi + im[b] * wr
                    re[b] = re[a] - vr; im[b] = im[a] - vi
                    re[a] += vr; im[a] += vi
                    val nwr = wr * wlr - wi * wli
                    wi = wr * wli + wi * wlr
                    wr = nwr
                }
                i += len
            }
            len = len shl 1
        }
    }

    private companion object { const val N = 1024 }
}
