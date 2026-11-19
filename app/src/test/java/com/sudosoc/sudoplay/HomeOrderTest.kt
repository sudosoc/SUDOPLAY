package com.sudosoc.sudoplay

import com.sudosoc.sudoplay.ui.homeOrder
import org.junit.Assert.*
import org.junit.Test

class HomeOrderTest {
    @Test fun keepsSavedOrderAndAppendsNewSections() {
        val o = homeOrder("favs,most")
        assertEquals(listOf("favs", "most"), o.take(2))
        assertTrue(o.containsAll(listOf("continue", "mix", "added", "downloads", "unplayed", "stats")))
        assertEquals(o.size, o.toSet().size)
    }
    @Test fun ignoresJunkAndDuplicates() {
        val o = homeOrder("zzz,most,most,,favs")
        assertEquals(listOf("most", "favs"), o.take(2)); assertFalse("zzz" in o)
    }
}
