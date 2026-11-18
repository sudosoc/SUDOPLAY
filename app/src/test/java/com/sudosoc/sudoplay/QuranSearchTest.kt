package com.sudosoc.sudoplay

import com.sudosoc.sudoplay.online.OnlineKind
import com.sudosoc.sudoplay.online.Quran
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Uses a trimmed copy of the real mp3quran.net directory (reciters + suwar). */
class QuranSearchTest {
    @Before fun load() { Quran.loadText(javaClass.getResource("/quran_sample.json")!!.readText()) }

    @Test fun reciterNameFindsReciterFolders() {
        val r = Quran.search(null, "عبد الباسط")
        assertTrue(r.isNotEmpty())
        assertTrue(r.all { it.collection?.startsWith("q:") == true && it.kind == OnlineKind.Quran })
        assertTrue(r.any { it.title.contains("الباسط") })
    }

    @Test fun surahPlusReciterGivesDirectFiles() {
        val r = Quran.search(null, "الشيخ العفاسي سورة الكهف")
        assertTrue(r.isNotEmpty())
        r.forEach { assertTrue(it.url.endsWith("018.mp3")); assertEquals(null, it.collection); assertTrue(it.subtitle.contains("العفاسي")) }
    }

    @Test fun surahWithoutAdjectiveAndTashkeel() {
        assertTrue(Quran.search(null, "سُورَةُ الكَهف").all { it.url.endsWith("018.mp3") })
        assertTrue(Quran.search(null, "الفاتحة").all { it.url.endsWith("001.mp3") })
    }

    @Test fun childrenListSurahsWithPaddedFileNames() {
        val folder = Quran.search(null, "عبد الباسط").first()
        var kids = Quran.children(null, folder.collection!!)
        if (kids.first().collection != null) kids = Quran.children(null, kids.first().collection!!)   // reciters with several mushafs list them first
        assertTrue(kids.isNotEmpty())
        assertTrue(kids.all { Regex("/\\d{3}\\.mp3$").containsMatchIn(it.url) })
    }

    @Test fun latinQueryFindsNothing() = assertTrue(Quran.search(null, "adele hello").isEmpty())
}
