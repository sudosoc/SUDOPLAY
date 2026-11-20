package com.sudosoc.sudoplay

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.sudosoc.sudoplay.data.AsyncFile
import com.sudosoc.sudoplay.data.Song
import com.sudosoc.sudoplay.ui.SelectionState
import com.sudosoc.sudoplay.ui.UndoState
import com.sudosoc.sudoplay.ui.describe
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SelectionTest {
    // android.net.Uri is a stub on the JVM, so build the Song without running its constructor
    private fun song(id: Long): Song {
        val uc = Class.forName("sun.misc.Unsafe")
        val u = uc.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        val s = uc.getMethod("allocateInstance", Class::class.java).invoke(u, Song::class.java) as Song
        Song::class.java.getDeclaredField("id").apply { isAccessible = true }.setLong(s, id)
        return s
    }
    private val list = (1L..5L).map(::song)

    @Test fun startsAndEndsWithTheLastTick() {
        val s = SelectionState()
        assertFalse(s.active)
        s.toggle(list[2], list); assertTrue(s.active); assertEquals(1, s.count)
        s.toggle(list[2], list); assertFalse(s.active); assertTrue(s.pool.isEmpty())
    }

    @Test fun keepsTheOrderOfTheList() {
        val s = SelectionState()
        s.toggle(list[3], list); s.toggle(list[0], list); s.toggle(list[4], list)
        assertEquals(listOf(1L, 4L, 5L), s.songs.map { it.id })
    }

    @Test fun selectAllAndClear() {
        val s = SelectionState()
        s.toggle(list[1], list)
        assertFalse(s.allSelected()); s.selectAll(); assertTrue(s.allSelected()); assertEquals(5, s.count)
        s.clear(); assertFalse(s.active)
    }

    @Test fun dropRemovesDeletedSongs() {
        val s = SelectionState()
        s.toggle(list[0], list); s.toggle(list[1], list)
        s.drop(setOf(1L)); assertEquals(1, s.count); s.drop(setOf(2L)); assertFalse(s.active)
    }

    @Test fun undoRunsOnceAndClears() {
        val u = UndoState(); var n = 0
        u.show("Removed") { n++ }
        assertEquals("Removed", u.message)
        u.run(); u.run()
        assertEquals(1, n); assertNull(u.message)
    }

    @Test fun iconNamesBecomeSpeech() {
        fun icon(name: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).build()
        assertEquals("More options", describe(icon("Rounded.MoreVert")))
        assertEquals("Delete", describe(icon("Rounded.Delete_Glass")))
        assertEquals("Add to playlist", describe(icon("Filled.PlaylistAdd")))
        assertEquals("Library music", describe(icon("Rounded.LibraryMusic")))
    }

    @Test fun savesAreAtomicAndOrdered() {
        val f = File.createTempFile("sudo", ".json")
        AsyncFile.write(f, "one"); AsyncFile.write(f, "two"); AsyncFile.write(f, "three")
        AsyncFile.flush()
        assertEquals("three", f.readText())
        assertFalse(File(f.parentFile, f.name + ".tmp").exists())
        f.delete()
    }
}
