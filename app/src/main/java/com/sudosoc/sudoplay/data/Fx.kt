package com.sudosoc.sudoplay.data

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Everything the sound engine needs. Hardware effects (EQ, bass, loudness, virtualizer) run through android.media.audiofx;
 * stereo width, reverb and echo run in our own audio processor so they behave the same on every phone.
 */
data class FxState(
    val enabled: Boolean = false,
    val levels: List<Int> = emptyList(),   // millibels per EQ band
    val preamp: Int = 0,                   // dB × 10, -120..0
    val bass: Int = 0,                     // 0..1000
    val loud: Int = 0,                     // 0..1000 (millibels of gain)
    val virtOn: Boolean = false,
    val virt: Int = 500,                   // 0..1000
    val virtMode: Int = 0,                 // 0 auto, 1 headphones, 2 speakers
    val widthOn: Boolean = false,
    val width: Int = 100,                  // percent, 0..200
    val reverbOn: Boolean = false,
    val room: Int = 50,                    // %
    val damp: Int = 40,                    // %
    val reverbMix: Int = 25,               // %
    val echoOn: Boolean = false,
    val echoMs: Int = 320,
    val echoFb: Int = 35,                  // %
    val echoMix: Int = 30,                 // %
    val balance: Int = 0,                  // -100 (left) .. 100 (right)
    val swapCh: Boolean = false,
    val compOn: Boolean = false,           // "night mode" compressor
    val compAmt: Int = 50,                 // 0..100
    val pOn: Boolean = false,              // parametric EQ (10 bands, run in the DSP processor)
    val pf: List<Int> = DEFAULT_PF,        // centre / corner frequency, Hz
    val pg: List<Int> = DEFAULT_PG,        // gain, dB x 10
    val pq: List<Int> = DEFAULT_PQ,        // Q x 10
    val pt: List<Int> = DEFAULT_PT,        // band type (Biquad.PEAK, LOW_SHELF, ...)
    val pe: List<Boolean> = DEFAULT_PE,    // band switched on
    val pAuto: Boolean = true,             // lower the level by the biggest boost so the EQ never clips
    val limiter: Boolean = true,           // soft limiter on the output
    val preset: String = "",
) {
    /** Copy with band [i] changed; unspecified values stay. */
    fun band(i: Int, f: Int? = null, g: Int? = null, q: Int? = null, t: Int? = null, e: Boolean? = null) = copy(
        pf = pf.toMutableList().also { if (f != null) it[i] = f.coerceIn(20, 20000) },
        pg = pg.toMutableList().also { if (g != null) it[i] = g.coerceIn(-150, 150) },
        pq = pq.toMutableList().also { if (q != null) it[i] = q.coerceIn(3, 200) },
        pt = pt.toMutableList().also { if (t != null) it[i] = t },
        pe = pe.toMutableList().also { if (e != null) it[i] = e },
        pOn = true, preset = "",
    )

    fun toJson(): String = JSONObject().apply {
        put("on", enabled); put("lv", JSONArray(levels)); put("pre", preamp); put("bass", bass); put("loud", loud)
        put("vOn", virtOn); put("v", virt); put("vm", virtMode)
        put("wOn", widthOn); put("w", width)
        put("rOn", reverbOn); put("room", room); put("damp", damp); put("rMix", reverbMix)
        put("eOn", echoOn); put("eMs", echoMs); put("eFb", echoFb); put("eMix", echoMix)
        put("bal", balance); put("swap", swapCh); put("cOn", compOn); put("cAmt", compAmt)
        put("pOn", pOn); put("pf10", JSONArray(pf)); put("pg10", JSONArray(pg)); put("pq10", JSONArray(pq)); put("pt10", JSONArray(pt)); put("pe10", JSONArray(pe))
        put("pAuto", pAuto); put("lim", limiter)
        put("preset", preset)
    }.toString()

    companion object {
        const val BANDS = 10
        val DEFAULT_PF = listOf(32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        val DEFAULT_PG = List(BANDS) { 0 }
        val DEFAULT_PQ = listOf(7, 14, 14, 14, 14, 14, 14, 14, 14, 7)
        val DEFAULT_PT = listOf(Biquad.LOW_SHELF) + List(8) { Biquad.PEAK } + listOf(Biquad.HIGH_SHELF)
        val DEFAULT_PE = List(BANDS) { true }
        private fun ints(a: JSONArray?, def: List<Int>) = if (a != null && a.length() == def.size) List(def.size) { a.getInt(it) } else def
        private fun bools(a: JSONArray?, def: List<Boolean>) = if (a != null && a.length() == def.size) List(def.size) { a.getBoolean(it) } else def
        fun fromJson(s: String?): FxState = runCatching {
            val o = JSONObject(s ?: return FxState())
            val lv = o.optJSONArray("lv")
            FxState(
                enabled = o.optBoolean("on"), levels = List(lv?.length() ?: 0) { lv!!.getInt(it) },
                preamp = o.optInt("pre"), bass = o.optInt("bass"), loud = o.optInt("loud"),
                virtOn = o.optBoolean("vOn"), virt = o.optInt("v", 500), virtMode = o.optInt("vm"),
                widthOn = o.optBoolean("wOn"), width = o.optInt("w", 100),
                reverbOn = o.optBoolean("rOn"), room = o.optInt("room", 50), damp = o.optInt("damp", 40), reverbMix = o.optInt("rMix", 25),
                echoOn = o.optBoolean("eOn"), echoMs = o.optInt("eMs", 320), echoFb = o.optInt("eFb", 35), echoMix = o.optInt("eMix", 30),
                balance = o.optInt("bal"), swapCh = o.optBoolean("swap"), compOn = o.optBoolean("cOn"), compAmt = o.optInt("cAmt", 50),
                pOn = o.optBoolean("pOn"), pf = DEFAULT_PF, pg = DEFAULT_PG, pq = DEFAULT_PQ,
                pt = ints(o.optJSONArray("pt10"), DEFAULT_PT), pe = bools(o.optJSONArray("pe10"), DEFAULT_PE),
                pAuto = o.optBoolean("pAuto", true), limiter = o.optBoolean("lim", true),
                preset = o.optString("preset", ""),
            ).let { st ->
                if (o.has("pf10")) st.copy(pf = ints(o.optJSONArray("pf10"), DEFAULT_PF), pg = ints(o.optJSONArray("pg10"), DEFAULT_PG), pq = ints(o.optJSONArray("pq10"), DEFAULT_PQ))
                else migrateOld(st, o)          // saved by 0.7–0.10 with 5 bands: keep those as the first five bell bands
            }
        }.getOrDefault(FxState())

        private fun migrateOld(st: FxState, o: JSONObject): FxState {
            val of = o.optJSONArray("pf"); val og = o.optJSONArray("pg"); val oq = o.optJSONArray("pq")
            if (of == null || og == null || oq == null || of.length() != 5) return st
            val f = DEFAULT_PF.toMutableList(); val g = DEFAULT_PG.toMutableList(); val q = DEFAULT_PQ.toMutableList(); val t = DEFAULT_PT.toMutableList()
            // old bands become bands 2..6 (bells); the others stay flat
            for (i in 0 until 5) { f[i + 1] = of.getInt(i); g[i + 1] = og.getInt(i); q[i + 1] = oq.getInt(i); t[i + 1] = Biquad.PEAK }
            return st.copy(pf = f, pg = g, pq = q, pt = t)
        }

        fun load(prefs: SharedPreferences) = fromJson(prefs.getString("fx", null))
    }
}

/** A named whole-chain preset: an EQ curve plus effect settings. */
class FxScene(val name: String, val curve: String, val patch: (FxState) -> FxState)

object FxScenes {
    val builtIn: List<FxScene> = listOf(
        FxScene("Flat", "Flat") { it },
        FxScene("Bass Party", "Bass boost") { it.copy(bass = 700, loud = 300, preamp = -30) },
        FxScene("Concert Hall", "Pop") { it.copy(reverbOn = true, room = 72, damp = 45, reverbMix = 34, virtOn = true, virt = 500, widthOn = true, width = 130, preamp = -20) },
        FxScene("Cathedral", "Classical") { it.copy(reverbOn = true, room = 95, damp = 28, reverbMix = 55, preamp = -40) },
        FxScene("Karaoke Echo", "Vocal") { it.copy(echoOn = true, echoMs = 280, echoFb = 40, echoMix = 45, reverbOn = true, room = 40, reverbMix = 20) },
        FxScene("Wide Studio", "Flat") { it.copy(widthOn = true, width = 160, virtOn = true, virt = 350) },
        FxScene("Vocal Clarity", "Vocal") { it.copy(loud = 200) },
        FxScene("Night Drive", "Bass + treble") { it.copy(bass = 400, widthOn = true, width = 120, reverbOn = true, room = 35, reverbMix = 12, loud = 250) },
        FxScene("Headphone Space", "Flat") { it.copy(virtOn = true, virt = 700, virtMode = 1, widthOn = true, width = 140, reverbOn = true, room = 30, reverbMix = 15) },
        FxScene("Low Volume", "Late night") { it.copy(loud = 450, preamp = -20) },
        FxScene("Night Mode", "Late night") { it.copy(compOn = true, compAmt = 70, loud = 200) },
    )

    fun apply(scene: FxScene, bands: Int, maxMb: Int): FxState =
        scene.patch(FxState(enabled = true, levels = EqPresets.levels(scene.curve, bands, maxMb))).copy(preset = scene.name)

    fun loadUser(prefs: SharedPreferences): List<Pair<String, FxState>> = runCatching {
        val arr = JSONArray(prefs.getString("fx_scenes", "[]"))
        List(arr.length()) { val o = arr.getJSONObject(it); o.getString("n") to FxState.fromJson(o.getString("s")) }
    }.getOrDefault(emptyList())

    fun saveUser(prefs: SharedPreferences, list: List<Pair<String, FxState>>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("n", it.first).put("s", it.second.toJson())) }
        prefs.edit().putString("fx_scenes", arr.toString()).apply()
    }
}

/** Presets for the 10-band parametric EQ (gains in dB, on the default band frequencies 32 Hz … 16 kHz). */
class ParamPreset(val name: String, val hint: String, val gains: List<Double>, val highPassHz: Int? = null, val lowPassHz: Int? = null) {
    fun apply(s: FxState): FxState {
        var o = s.copy(pOn = true, enabled = true, pf = FxState.DEFAULT_PF, pq = FxState.DEFAULT_PQ, pt = FxState.DEFAULT_PT, pe = FxState.DEFAULT_PE,
            pg = gains.map { (it * 10).toInt() }, preset = "")
        if (highPassHz != null) o = o.band(0, f = highPassHz, t = Biquad.HIGH_PASS, q = 7, e = true, g = 0)
        if (lowPassHz != null) o = o.band(9, f = lowPassHz, t = Biquad.LOW_PASS, q = 7, e = true, g = 0)
        return o.copy(pOn = true, preset = "")
    }
}

object ParamPresets {
    val all = listOf(
        ParamPreset("Flat", "Every band at 0 dB", List(10) { 0.0 }),
        ParamPreset("Bass boost", "Fuller low end", listOf(6.0, 5.0, 3.5, 1.5, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)),
        ParamPreset("Treble boost", "More air and detail", listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.5, 1.5, 3.0, 4.5, 5.0)),
        ParamPreset("V-shape", "Strong bass and treble, scooped mids", listOf(5.0, 4.0, 2.0, -1.0, -2.5, -2.5, -1.0, 2.0, 4.0, 5.0)),
        ParamPreset("Vocal", "Pushes voices forward", listOf(-3.0, -2.0, -1.0, 0.0, 1.5, 3.0, 3.5, 2.0, 0.0, -1.0)),
        ParamPreset("Quran / speech", "Cuts rumble and hiss, lifts the voice range", listOf(0.0, -4.0, -2.0, 0.5, 2.0, 3.5, 3.5, 1.5, -2.0, -4.0), highPassHz = 70, lowPassHz = 12000),
        ParamPreset("Warm", "Softer, smoother highs", listOf(3.0, 2.5, 2.0, 1.0, 0.0, -0.5, -1.5, -2.5, -3.5, -4.0)),
        ParamPreset("Bright", "Crisp and open", listOf(-1.0, -1.0, 0.0, 0.0, 0.0, 1.0, 2.5, 4.0, 5.0, 5.0)),
        ParamPreset("Cut mud", "Clears the 200–500 Hz boxiness", listOf(0.0, 0.0, -1.0, -4.0, -3.0, 0.0, 0.5, 1.0, 1.0, 0.0)),
        ParamPreset("Loudness", "Bass + treble lift for low volume", listOf(6.0, 4.5, 2.0, 0.0, -1.0, -1.0, 0.0, 2.0, 4.0, 5.0)),
        ParamPreset("Small speakers", "Phone speaker: drops what it can't reproduce", listOf(0.0, 0.0, -3.0, -1.0, 1.0, 2.0, 2.5, 2.0, 1.0, 0.0), highPassHz = 120),
        ParamPreset("Harsh highs", "Tames sibilance and fatigue", listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, -1.0, -3.0, -4.0, -2.0)),
        ParamPreset("Hip-hop", "Deep punchy bass, crisp vocals", listOf(5.5, 5.0, 3.0, 0.5, -1.0, 0.0, 1.5, 2.0, 2.5, 2.0)),
        ParamPreset("Rock", "Meaty low-mids, bright top", listOf(3.5, 3.0, 1.5, -0.5, -1.5, 0.0, 2.0, 3.5, 4.0, 4.0)),
        ParamPreset("Acoustic", "Natural guitars and strings", listOf(2.0, 1.5, 1.0, 0.5, 1.0, 1.5, 2.0, 2.5, 2.0, 1.0)),
        ParamPreset("Electronic", "Sub bass and sparkle", listOf(6.0, 5.0, 1.5, 0.0, -1.5, 0.0, 1.0, 3.0, 4.5, 4.0)),
    )
}
