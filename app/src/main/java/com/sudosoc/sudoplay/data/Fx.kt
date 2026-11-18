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
    val pOn: Boolean = false,              // parametric EQ (5 peaking bands, run in the DSP processor)
    val pf: List<Int> = DEFAULT_PF,        // centre frequency, Hz
    val pg: List<Int> = DEFAULT_PG,        // gain, dB x 10
    val pq: List<Int> = DEFAULT_PQ,        // Q x 10
    val preset: String = "",
) {
    fun toJson(): String = JSONObject().apply {
        put("on", enabled); put("lv", JSONArray(levels)); put("pre", preamp); put("bass", bass); put("loud", loud)
        put("vOn", virtOn); put("v", virt); put("vm", virtMode)
        put("wOn", widthOn); put("w", width)
        put("rOn", reverbOn); put("room", room); put("damp", damp); put("rMix", reverbMix)
        put("eOn", echoOn); put("eMs", echoMs); put("eFb", echoFb); put("eMix", echoMix)
        put("bal", balance); put("swap", swapCh); put("cOn", compOn); put("cAmt", compAmt)
        put("pOn", pOn); put("pf", JSONArray(pf)); put("pg", JSONArray(pg)); put("pq", JSONArray(pq))
        put("preset", preset)
    }.toString()

    companion object {
        val DEFAULT_PF = listOf(60, 250, 1000, 4000, 12000)
        val DEFAULT_PG = listOf(0, 0, 0, 0, 0)
        val DEFAULT_PQ = listOf(10, 10, 10, 10, 10)
        private fun ints(a: JSONArray?, def: List<Int>) = if (a != null && a.length() == def.size) List(def.size) { a.getInt(it) } else def
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
                pOn = o.optBoolean("pOn"), pf = ints(o.optJSONArray("pf"), DEFAULT_PF), pg = ints(o.optJSONArray("pg"), DEFAULT_PG), pq = ints(o.optJSONArray("pq"), DEFAULT_PQ),
                preset = o.optString("preset", ""),
            )
        }.getOrDefault(FxState())

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
