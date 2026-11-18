package com.sudosoc.sudoplay

import com.sudosoc.sudoplay.online.OnlineItem
import com.sudosoc.sudoplay.online.OnlineKind
import com.sudosoc.sudoplay.online.OnlineMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnlineMatchTest {
    private fun c(title: String, artist: String, dur: Long, url: String) = OnlineItem(OnlineKind.SoundCloud, title, artist, null, dur, url)

    @Test fun picksTheSameSongIgnoringOfficialVideoNoise() {
        val got = OnlineMatch.best("Adele - Hello (Official Music Video)", "Adele", 295, listOf(
            c("Hello (Piano cover)", "Some Pianist", 301, "a"),
            c("Adele Hello", "Adele", 296, "b"),
            c("Hello - 1 hour loop", "Adele Fan", 3600, "c"),
        ))
        assertEquals("b", got?.url)
    }

    @Test fun rejectsDifferentSongsAndWrongLengths() {
        assertNull(OnlineMatch.best("Someone Like You", "Adele", 285, listOf(c("Rolling in the Deep", "Adele", 228, "x"))))
        assertNull(OnlineMatch.best("Someone Like You", "Adele", 285, listOf(c("Someone Like You", "Adele", 900, "y"))))
    }

    @Test fun lengthlessCandidatesAreAllowed() =
        assertEquals("z", OnlineMatch.best("Fairuz Habaitak", "Fairuz", 0, listOf(c("Habaitak", "Fairuz", 0, "z")))?.url)

    @Test fun arabicTitlesMatch() =
        assertEquals("k", OnlineMatch.best("سورة الكهف", "مشاري العفاسي", 0, listOf(c("سورة الكهف", "مشاري العفاسي", 0, "k")))?.url)
}
