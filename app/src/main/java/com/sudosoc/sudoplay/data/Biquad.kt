package com.sudosoc.sudoplay.data

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** One band of the parametric EQ. Types follow the RBJ "Audio EQ Cookbook". */
object Biquad {
    const val PEAK = 0
    const val LOW_SHELF = 1
    const val HIGH_SHELF = 2
    const val HIGH_PASS = 3
    const val LOW_PASS = 4
    const val NOTCH = 5
    val typeNames = listOf("Bell", "Low shelf", "High shelf", "High-pass", "Low-pass", "Notch")

    /** Gain is meaningful for these types; the others only use frequency and Q. */
    fun usesGain(type: Int) = type == PEAK || type == LOW_SHELF || type == HIGH_SHELF

    /** Normalised [b0, b1, b2, a1, a2] (a0 = 1). gainDb in dB, q > 0. */
    fun coefs(type: Int, fc: Double, gainDb: Double, q: Double, sr: Double): DoubleArray {
        val f = fc.coerceIn(10.0, sr * 0.49)
        val qq = q.coerceIn(0.1, 30.0)
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2 * PI * f / sr
        val cw = cos(w0); val sw = sin(w0)
        val alpha = sw / (2 * qq)
        val b0: Double; val b1: Double; val b2: Double; val a0: Double; val a1: Double; val a2: Double
        when (type) {
            LOW_SHELF -> {
                val s = 2 * sqrt(a) * alpha
                b0 = a * ((a + 1) - (a - 1) * cw + s); b1 = 2 * a * ((a - 1) - (a + 1) * cw); b2 = a * ((a + 1) - (a - 1) * cw - s)
                a0 = (a + 1) + (a - 1) * cw + s; a1 = -2 * ((a - 1) + (a + 1) * cw); a2 = (a + 1) + (a - 1) * cw - s
            }
            HIGH_SHELF -> {
                val s = 2 * sqrt(a) * alpha
                b0 = a * ((a + 1) + (a - 1) * cw + s); b1 = -2 * a * ((a - 1) + (a + 1) * cw); b2 = a * ((a + 1) + (a - 1) * cw - s)
                a0 = (a + 1) - (a - 1) * cw + s; a1 = 2 * ((a - 1) - (a + 1) * cw); a2 = (a + 1) - (a - 1) * cw - s
            }
            HIGH_PASS -> {
                b0 = (1 + cw) / 2; b1 = -(1 + cw); b2 = (1 + cw) / 2
                a0 = 1 + alpha; a1 = -2 * cw; a2 = 1 - alpha
            }
            LOW_PASS -> {
                b0 = (1 - cw) / 2; b1 = 1 - cw; b2 = (1 - cw) / 2
                a0 = 1 + alpha; a1 = -2 * cw; a2 = 1 - alpha
            }
            NOTCH -> {
                b0 = 1.0; b1 = -2 * cw; b2 = 1.0
                a0 = 1 + alpha; a1 = -2 * cw; a2 = 1 - alpha
            }
            else -> {
                b0 = 1 + alpha * a; b1 = -2 * cw; b2 = 1 - alpha * a
                a0 = 1 + alpha / a; a1 = -2 * cw; a2 = 1 - alpha / a
            }
        }
        return doubleArrayOf(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }

    /** Magnitude of the filter at frequency f, in dB. */
    fun responseDb(c: DoubleArray, f: Double, sr: Double): Double {
        val w = 2 * PI * f / sr
        val cw = cos(w); val sw = sin(w); val c2 = cos(2 * w); val s2 = sin(2 * w)
        val nr = c[0] + c[1] * cw + c[2] * c2; val ni = -(c[1] * sw + c[2] * s2)
        val dr = 1 + c[3] * cw + c[4] * c2; val di = -(c[3] * sw + c[4] * s2)
        val num = nr * nr + ni * ni; val den = dr * dr + di * di
        return 10 * log10((num / den).coerceAtLeast(1e-20))
    }

    /** A band does something when it has gain, or when it is a pass / notch filter. */
    fun active(type: Int, enabled: Boolean, gainTenthDb: Int) = enabled && (!usesGain(type) || gainTenthDb != 0)

    /** Combined response of all active bands at f. */
    fun totalDb(state: FxState, f: Double, sr: Double = 48000.0): Double {
        var s = 0.0
        for (i in 0 until FxState.BANDS) {
            if (!active(state.pt[i], state.pe[i], state.pg[i])) continue
            s += responseDb(coefs(state.pt[i], state.pf[i].toDouble(), state.pg[i] / 10.0, state.pq[i] / 10.0, sr), f, sr)
        }
        return s
    }

    /** Highest boost of the whole curve, used by "auto headroom" so a boosted EQ never clips. */
    fun peakBoostDb(state: FxState, sr: Double = 48000.0): Double {
        var m = 0.0
        var f = 20.0
        while (f <= 20000.0) { m = maxOf(m, totalDb(state, f, sr)); f *= 1.05 }
        return m
    }

    fun absDb(x: Double) = abs(x)
}
