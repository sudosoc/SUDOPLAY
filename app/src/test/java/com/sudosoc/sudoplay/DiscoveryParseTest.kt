package com.sudosoc.sudoplay

import com.sudosoc.sudoplay.online.Discovery
import com.sudosoc.sudoplay.ui.Fonts
import org.junit.Assert.*
import org.junit.Test

class DiscoveryParseTest {
    @Test fun deezerTracks() {
        val j = """{"data":[{"title":"Dracula","duration":209,"artist":{"name":"Tame Impala"},"album":{"cover_medium":"https://x/y.jpg"}},{"title":"","artist":{"name":"z"}}]}"""
        val t = Discovery.parseTracks(j)
        assertEquals(1, t.size); assertEquals("Tame Impala", t[0].artist); assertEquals(209L, t[0].durationSec); assertEquals("https://x/y.jpg", t[0].thumb)
    }

    @Test fun deezerPlaylists() {
        val p = Discovery.parsePlaylists("""{"data":[{"id":123,"title":"Top","nb_tracks":50,"picture_medium":"https://p/1.jpg"},{"title":"no id"}]}""")
        assertEquals(1, p.size); assertEquals(123L, p[0].id); assertEquals(50, p[0].count)
    }

    @Test fun hexColors() {
        assertNotNull(Fonts.parse("#FFD54F")); assertNotNull(Fonts.parse("ffd54f"))
        assertNull(Fonts.parse("#12345")); assertNull(Fonts.parse("zzzzzz")); assertNull(Fonts.parse(""))
        assertEquals("#FFD54F", Fonts.hexOf(Fonts.parse("#ffd54f")!!))
    }

    @Test fun contrastRatio() {
        val w = Fonts.parse("#FFFFFF")!!; val b = Fonts.parse("#000000")!!
        assertTrue(Fonts.contrast(w, b) > 20f)
        assertTrue(Fonts.contrast(w, w) < 1.01f)
    }
}
