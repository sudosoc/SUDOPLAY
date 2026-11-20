package com.sudosoc.sudoplay

import com.sudosoc.sudoplay.online.DownloadCenter
import com.sudosoc.sudoplay.online.TitleKey
import org.junit.Assert.*
import org.junit.Test

class DownloadResilienceTest {
    @Test fun waitsForNetworkThenWifi() {
        assertEquals("Waiting for network", DownloadCenter.waitingFor(false, internet = false, unmetered = false))
        assertEquals("Waiting for Wi-Fi", DownloadCenter.waitingFor(true, internet = true, unmetered = false))
        assertNull(DownloadCenter.waitingFor(true, internet = true, unmetered = true))
        assertNull(DownloadCenter.waitingFor(false, internet = true, unmetered = false))   // mobile data is fine when Wi-Fi-only is off
    }
    @Test fun sameSongSameKey() {
        val k = TitleKey.of("Blinding Lights")
        assertEquals(k, TitleKey.of("Blinding Lights (Official Video)"))
        assertEquals(k, TitleKey.of("BLINDING LIGHTS [Lyrics] ft. Someone"))
        assertNotEquals(k, TitleKey.of("Save Your Tears"))
    }
    @Test fun arabicTitlesKeepTheirLetters() = assertEquals(TitleKey.of("يا قلبي"), TitleKey.of("يا قلبي (فيديو كليب)"))
}
