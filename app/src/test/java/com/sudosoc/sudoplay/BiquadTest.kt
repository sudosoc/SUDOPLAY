package com.sudosoc.sudoplay

import com.sudosoc.sudoplay.data.Biquad
import com.sudosoc.sudoplay.data.FxState
import com.sudosoc.sudoplay.data.ParamPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BiquadTest {
    private val sr = 48000.0

    @Test fun bellHitsItsGainAtTheCentre() {
        val c = Biquad.coefs(Biquad.PEAK, 1000.0, 6.0, 1.0, sr)
        assertEquals(6.0, Biquad.responseDb(c, 1000.0, sr), 0.05)
        assertEquals(0.0, Biquad.responseDb(c, 40.0, sr), 0.3)
        assertEquals(0.0, Biquad.responseDb(c, 18000.0, sr), 0.5)
    }

    @Test fun shelvesReachTheirGainFarFromTheCorner() {
        val lo = Biquad.coefs(Biquad.LOW_SHELF, 200.0, 5.0, 0.7, sr)
        assertEquals(5.0, Biquad.responseDb(lo, 20.0, sr), 0.3)
        assertEquals(0.0, Biquad.responseDb(lo, 8000.0, sr), 0.3)
        val hi = Biquad.coefs(Biquad.HIGH_SHELF, 4000.0, -4.0, 0.7, sr)
        assertEquals(-4.0, Biquad.responseDb(hi, 18000.0, sr), 0.4)
        assertEquals(0.0, Biquad.responseDb(hi, 100.0, sr), 0.2)
    }

    @Test fun passFiltersAreMinus3dBAtCornerWithButterworthQ() {
        val q = 0.7071
        assertEquals(-3.0, Biquad.responseDb(Biquad.coefs(Biquad.HIGH_PASS, 100.0, 0.0, q, sr), 100.0, sr), 0.1)
        assertEquals(-3.0, Biquad.responseDb(Biquad.coefs(Biquad.LOW_PASS, 8000.0, 0.0, q, sr), 8000.0, sr), 0.1)
        assertTrue(Biquad.responseDb(Biquad.coefs(Biquad.HIGH_PASS, 100.0, 0.0, q, sr), 20.0, sr) < -20)
    }

    @Test fun notchKillsTheCentre() =
        assertTrue(Biquad.responseDb(Biquad.coefs(Biquad.NOTCH, 1000.0, 0.0, 5.0, sr), 1000.0, sr) < -60)

    @Test fun flatStateIsFlatAndInactive() {
        val s = FxState()
        assertEquals(0.0, Biquad.totalDb(s, 1000.0), 1e-9)
        assertEquals(0.0, Biquad.peakBoostDb(s), 1e-9)
    }

    @Test fun peakBoostFindsTheBiggestLift() {
        val s = FxState().band(5, g = 60)               // +6 dB at 1 kHz
        assertEquals(6.0, Biquad.peakBoostDb(s), 0.4)
    }

    @Test fun presetsKeepTenBandsAndSurviveJson() {
        ParamPresets.all.forEach { p ->
            assertEquals(p.name, 10, p.gains.size)
            val s = p.apply(FxState())
            val back = FxState.fromJson(s.toJson())
            assertEquals(s.pg, back.pg); assertEquals(s.pt, back.pt); assertEquals(s.pf, back.pf)
        }
    }

    @Test fun oldFiveBandSettingsMigrate() {
        val old = """{"on":true,"pOn":true,"pf":[60,250,1000,4000,12000],"pg":[10,0,-20,0,30],"pq":[10,10,10,10,10]}"""
        val s = FxState.fromJson(old)
        assertEquals(10, s.pg.size)
        assertTrue(s.pOn)
        assertEquals(listOf(10, 0, -20, 0, 30), s.pg.subList(1, 6))
        assertEquals(listOf(60, 250, 1000, 4000, 12000), s.pf.subList(1, 6))
    }
}
