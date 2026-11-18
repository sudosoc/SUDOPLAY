package com.sudosoc.sudoplay.online

import android.content.Context
import com.sudosoc.sudoplay.data.NetEntry
import com.sudosoc.sudoplay.data.NetLog
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.File

/**
 * Quran recitations from mp3quran.net (free, no key): every reciter and every surah. The two lists are downloaded once
 * (about 170 KB), cached in the app folder and refreshed every two weeks, then searched locally — so searching is instant.
 */
object Quran {
    class Moshaf(val id: Int, val name: String, val server: String, val surahs: Set<Int>)
    class Reciter(val id: Int, val name: String, val folded: String, val moshaf: List<Moshaf>) { val packed = folded.replace(" ", "") }

    private val http = OkHttpClient()
    private var reciters: List<Reciter>? = null
    private var surahNames: List<String> = emptyList()
    private var surahFolded: List<String> = emptyList()

    fun fold(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s.lowercase()) when {
            ch in 'ً'..'ٟ' || ch == 'ٰ' || ch == 'ـ' -> {}
            ch == 'أ' || ch == 'إ' || ch == 'آ' || ch == 'ٱ' -> sb.append('ا')
            ch == 'ى' -> sb.append('ي')
            ch == 'ة' -> sb.append('ه')
            else -> sb.append(ch)
        }
        return sb.toString()
    }

    private fun fetch(url: String): String {
        val t0 = System.currentTimeMillis()
        http.newCall(okhttp3.Request.Builder().url(url).header("User-Agent", "SUDOPLAY/0.9").build()).execute().use { r ->
            val body = r.body?.string() ?: ""
            val u = android.net.Uri.parse(url)
            NetLog.add(NetEntry(System.currentTimeMillis(), "GET", u.host ?: "?", (u.path ?: "/").take(60), r.code, body.length.toLong(), System.currentTimeMillis() - t0))
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
            return body
        }
    }

    @Synchronized private fun ensure(ctx: Context) {
        if (reciters != null) return
        val f = File(ctx.filesDir, "quran_v1.json")
        val fresh = f.exists() && System.currentTimeMillis() - f.lastModified() < 14L * 24 * 3600 * 1000
        var text: String? = if (fresh) runCatching { f.readText() }.getOrNull() else null
        if (text == null) {
            text = runCatching {
                val r = fetch("https://www.mp3quran.net/api/v3/reciters?language=ar")
                val s = fetch("https://www.mp3quran.net/api/v3/suwar?language=ar")
                JSONObject().put("r", JSONObject(r).getJSONArray("reciters")).put("s", JSONObject(s).getJSONArray("suwar")).toString().also { f.writeText(it) }
            }.getOrNull() ?: runCatching { f.readText() }.getOrNull() ?: throw java.io.IOException("Could not reach the Quran directory")
        }
        loadText(text)
    }

    /** Parses the cached {"r":[reciters],"s":[suwar]} document (also used by the unit test). */
    internal fun loadText(text: String) {
        val o = JSONObject(text)
        val rs = o.getJSONArray("r"); val ss = o.getJSONArray("s")
        surahNames = (0 until ss.length()).map { ss.getJSONObject(it).getString("name") }       // ids are 1..114 in order
        surahFolded = surahNames.map { fold(it) }
        reciters = (0 until rs.length()).map { i ->
            val r = rs.getJSONObject(i)
            val ms = r.getJSONArray("moshaf")
            Reciter(r.getInt("id"), r.getString("name"), fold(r.getString("name")), (0 until ms.length()).map { j ->
                val m = ms.getJSONObject(j)
                Moshaf(m.getInt("id"), m.getString("name"), m.getString("server"), m.getString("surah_list").split(",").mapNotNull { it.trim().toIntOrNull() }.toSet())
            })
        }
    }

    private val noise = setOf("سوره", "سورة", "الشيخ", "شيخ", "القارئ", "قارئ", "تلاوه", "تلاوة", "قران", "القران", "قرآن", "القرآن", "sheikh", "surah", "sura", "quran", "ال")

    private fun surahFor(tok: String): Int {
        val t = tok.removePrefix("ال")
        if (t.length < 2) return -1
        // exact (with/without "ال") first, then prefix for longer words
        surahFolded.indexOfFirst { it == tok || it.removePrefix("ال") == t }.let { if (it >= 0) return it + 1 }
        if (t.length >= 3) surahFolded.indexOfFirst { it.removePrefix("ال").startsWith(t) }.let { if (it >= 0) return it + 1 }
        return -1
    }

    private fun url(m: Moshaf, surah: Int) = m.server.trimEnd('/') + "/" + surah.toString().padStart(3, '0') + ".mp3"

    fun search(ctx: Context?, q: String): List<OnlineItem> {
        if (reciters == null) ensure(ctx!!)
        val all = reciters!!
        val toks = fold(q).split(Regex("\\s+")).filter { it.isNotBlank() && it !in noise }
        if (toks.isEmpty()) return emptyList()
        val surahToks = toks.filter { surahFor(it) > 0 }
        val reciterToks = toks - surahToks.toSet()
        val surah = surahToks.firstOrNull()?.let { surahFor(it) } ?: -1
        // reciter words must all appear in the name; when none, take the best-known (lowest id) reciters
        val matched = if (reciterToks.isEmpty()) all else all.filter { r -> reciterToks.all { t -> r.packed.contains(t) } }
        if (surah > 0) {
            val name = surahNames[surah - 1]
            return matched.mapNotNull { r -> r.moshaf.firstOrNull { surah in it.surahs }?.let { m -> r to m } }.take(10).map { (r, m) ->
                OnlineItem(OnlineKind.Quran, "سورة $name", r.name + " • " + m.name, null, 0, url(m, surah))
            }
        }
        if (reciterToks.isEmpty()) return emptyList()
        return matched.take(12).map { r ->
            OnlineItem(OnlineKind.Quran, r.name, "${r.moshaf.size} مصحف • قائمة السور", null, 0, "", "q:${r.id}")
        }
    }

    /** "q:<reciter>" → their mushafs (or the surahs when there is only one); "q:<reciter>:<mushaf>" → the surahs. */
    fun children(ctx: Context?, key: String): List<OnlineItem> {
        if (reciters == null) ensure(ctx!!)
        val parts = key.split(":")
        val r = reciters!!.firstOrNull { it.id == parts.getOrNull(1)?.toIntOrNull() } ?: return emptyList()
        val m = parts.getOrNull(2)?.toIntOrNull()?.let { mid -> r.moshaf.firstOrNull { it.id == mid } }
        if (m == null && r.moshaf.size > 1) {
            return r.moshaf.map { OnlineItem(OnlineKind.Quran, it.name, "${it.surahs.size} سورة", null, 0, "", "q:${r.id}:${it.id}") }
        }
        val mm = m ?: r.moshaf.first()
        return mm.surahs.sorted().filter { it in 1..114 }.map { s ->
            OnlineItem(OnlineKind.Quran, "$s. " + surahNames[s - 1], r.name + " • " + mm.name, null, 0, url(mm, s))
        }
    }
}
