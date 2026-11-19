package com.sudosoc.sudoplay

import com.sudosoc.sudoplay.online.DownloadCenter
import com.sudosoc.sudoplay.online.OnlineItem
import com.sudosoc.sudoplay.online.OnlineKind
import org.junit.Assert.*
import org.junit.Test

class DownloadDedupTest {
    private val a = OnlineItem(OnlineKind.YouTube, "Blinding Lights", "The Weeknd • 3:20", null, 200, "https://www.youtube.com/watch?v=4NRXx6U8ABQ")
    private val sameSong = OnlineItem(OnlineKind.SoundCloud, "Blinding  Lights!", "the weeknd", null, 200, "https://soundcloud.com/x/y")
    private val other = OnlineItem(OnlineKind.YouTube, "Save Your Tears", "The Weeknd", null, 200, "https://www.youtube.com/watch?v=XXXX")

    @Test fun samePreparingSongIsBusyOnSecondTap() {
        val keys = DownloadCenter.keysOf(a, 5L)
        assertFalse(DownloadCenter.isBusyWith(keys))
        DownloadCenter.pending.addAll(keys)
        try {
            assertTrue(DownloadCenter.isBusyWith(DownloadCenter.keysOf(a, 5L)))           // same link
            assertTrue(DownloadCenter.isBusyWith(DownloadCenter.keysOf(sameSong)))        // same name, other source
            assertTrue(DownloadCenter.pendingFor(5L))
            assertFalse(DownloadCenter.isBusyWith(DownloadCenter.keysOf(other)))
        } finally { DownloadCenter.pending.removeAll(keys) }
        assertFalse(DownloadCenter.pendingFor(5L))
    }
}
