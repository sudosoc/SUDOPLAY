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
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

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
    @Volatile var balance = 0f
    @Volatile var swap = false
    @Volatile var compOn = false
    @Volatile var compAmt = 0.5f

    @Volatile var pOn = false
    @Volatile var pf = IntArray(5) { FxState.DEFAULT_PF[it] }
    @Volatile var pg = IntArray(5)
    @Volatile var pq = IntArray(5) { 10 }
    @Volatile var pVersion = 0

    /** Loudness-normalisation gain (linear) the processor glides towards, and the per-song gains found by the analyser (dB, by media id). */
    @Volatile var targetGain = 1f
    val gains = java.util.concurrent.ConcurrentHashMap<String, Float>()

    /** Last 256 mono samples, for the oscilloscope view. */
    @Volatile var scope = FloatArray(256)

    fun set(s: FxState) {
        enabled = s.enabled
        widthOn = s.widthOn; width = s.width / 100f
        reverbOn = s.reverbOn; room = s.room / 100f; damp = s.damp / 100f; reverbMix = s.reverbMix / 100f
        echoOn = s.echoOn; echoMs = s.echoMs; echoFb = s.echoFb / 100f; echoMix = s.echoMix / 100f
        pOn = s.pOn; pf = s.pf.toIntArray(); pg = s.pg.toIntArray(); pq = s.pq.toIntArray(); pVersion++
        balance = s.balance / 100f; swap = s.swapCh; compOn = s.compOn; compAmt = s.compAmt / 100f
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

    // parametric EQ: 5 peaking biquads (RBJ cookbook), direct form I, per channel
    private val bq = Array(5) { FloatArray(5) }          // b0 b1 b2 a1 a2 (normalised)
    private val bz = Array(2) { Array(5) { FloatArray(4) } }  // x1 x2 y1 y2
    private var bqVersion = -1
    private fun updateBiquads() {
        val f = FxBus.pf; val g = FxBus.pg; val q = FxBus.pq
        for (i in 0 until 5) {
            val fc = f[i].toDouble().coerceIn(20.0, sr * 0.45)
            val a = 10.0.pow(g[i] / 400.0)               // gain is dB x 10 → A = 10^(dB/40)
            val w0 = 2 * PI * fc / sr
            val alpha = sin(w0) / (2 * (q[i] / 10.0).coerceIn(0.2, 12.0))
            val cw = cos(w0)
            val a0 = 1 + alpha / a
            bq[i][0] = ((1 + alpha * a) / a0).toFloat(); bq[i][1] = ((-2 * cw) / a0).toFloat(); bq[i][2] = ((1 - alpha * a) / a0).toFloat()
            bq[i][3] = ((-2 * cw) / a0).toFloat(); bq[i][4] = ((1 - alpha / a) / a0).toFloat()
        }
        bqVersion = FxBus.pVersion
    }
    private fun biquad(c: Int, x0: Float): Float {
        var x = x0
        for (i in 0 until 5) {
            if (FxBus.pg[i] == 0) continue
            val k = bq[i]; val z = bz[c][i]
            val y = k[0] * x + k[1] * z[0] + k[2] * z[1] - k[3] * z[2] - k[4] * z[3]
            z[1] = z[0]; z[0] = x; z[3] = z[2]; z[2] = y
            x = y
        }
        return x
    }

    private var curGain = 1f
    private var env = 0f
    private val scopeBuf = FloatArray(256)
    private var scopeIdx = 0
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
        env = 0f
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
        val bal = FxBus.balance
        val lGain = if (on && bal > 0f) 1f - bal else 1f
        val rGain = if (on && bal < 0f) 1f + bal else 1f
        val swap = on && FxBus.swap && ch == 2
        val cOn = on && FxBus.compOn
        val camt = FxBus.compAmt
        val thr = 0.55f - 0.45f * camt
        val ratio = 2f + 8f * camt
        val makeup = 1f / (thr + (1f - thr) / ratio)
        val tgt = FxBus.targetGain
        val pEq = on && FxBus.pOn
        if (pEq && bqVersion != FxBus.pVersion) updateBiquads()

        var pos = inB.position()
        for (f in 0 until frames) {
            var l = inB.getShort(pos) / 32768f; pos += 2
            var r = l
            if (ch == 2) { r = inB.getShort(pos) / 32768f; pos += 2 }

            // loudness normalisation glides to its target so a late analysis result never clicks
            curGain += (tgt - curGain) * 0.0008f
            l *= curGain; r *= curGain

            if (pEq) { l = biquad(0, l); r = if (ch == 2) biquad(1, r) else l }

            if (swap) { val tmp = l; l = r; r = tmp }
            if (ch == 2) { l *= lGain; r *= rGain }

            if (cOn) {
                val lvl = maxOf(abs(l), abs(r))
                env += (lvl - env) * (if (lvl > env) 0.02f else 0.0004f)
                val g = if (env > thr) (thr + (env - thr) / ratio) / env else 1f
                val k = g * (if (env > thr) makeup else 1f + (makeup - 1f) * 0.6f)
                l *= k; r *= k
            }

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

            l = softClip(l); r = softClip(r)
            ring[rp] = (l + r) * 0.5f
            if ((rp and 7) == 0) { scopeBuf[scopeIdx] = ring[rp]; if (++scopeIdx >= scopeBuf.size) { scopeIdx = 0; FxBus.scope = scopeBuf.copyOf() } }
            if (++rp >= N) { rp = 0; analyze() }

            out.putShort((l * 32767f).toInt().coerceIn(-32768, 32767).toShort())
            if (ch == 2) out.putShort((r * 32767f).toInt().coerceIn(-32768, 32767).toShort())
        }
        inputBuffer.position(inputBuffer.limit())
        out.flip()
    }

    private fun softClip(x: Float): Float = when {
        x > 0.92f -> 0.92f + 0.08f * tanh((x - 0.92f) / 0.08f)
        x < -0.92f -> -0.92f - 0.08f * tanh((-x - 0.92f) / 0.08f)
        else -> x
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
