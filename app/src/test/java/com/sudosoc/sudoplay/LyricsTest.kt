package com.sudosoc.sudoplay

import com.sudosoc.sudoplay.data.LyricsLoader
import org.junit.Assert.*
import org.junit.Test

class LyricsTest {
    @Test fun syncedLrcIsParsedAndSorted() {
        val l = LyricsLoader.parse("[ar:Someone]\n[00:12.50]second\n[00:01.5]first\n[01:02.345]third")!!
        assertTrue(l.synced)
        assertEquals(listOf("first", "second", "third"), l.lines.map { it.text })
        assertEquals(listOf(1500L, 12500L, 62345L), l.lines.map { it.timeMs })
    }

    @Test fun multipleTimestampsOnOneLine() {
        val l = LyricsLoader.parse("[00:10.00][00:20.00]chorus")!!
        assertEquals(listOf(10000L, 20000L), l.lines.map { it.timeMs })
        assertTrue(l.lines.all { it.text == "chorus" })
    }

    @Test fun plainTextIsNotSynced() {
        val l = LyricsLoader.parse("line one\n\nline two")!!
        assertFalse(l.synced)
        assertEquals(2, l.lines.size)
    }

    @Test fun emptyReturnsNull() = assertNull(LyricsLoader.parse("  \n[ti:Title]"))
}
