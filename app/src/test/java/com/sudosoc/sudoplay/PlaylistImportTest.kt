package com.sudosoc.sudoplay

import com.sudosoc.sudoplay.online.OnlineKind
import com.sudosoc.sudoplay.online.PlaylistImport
import org.junit.Assert.*
import org.junit.Test

class PlaylistImportTest {
    @Test fun linesWithArtistAndTitle() {
        val l = PlaylistImport.parseText("x", "1. Adele - Hello\n\n# comment\nRolling in the Deep\nعمرو دياب – نور العين").tracks
        assertEquals(3, l.size)
        assertEquals("Hello", l[0].title); assertEquals("Adele", l[0].artist)
        assertEquals("Rolling in the Deep", l[1].title); assertEquals("", l[1].artist)
        assertEquals("نور العين", l[2].title); assertEquals("عمرو دياب", l[2].artist)
    }

    @Test fun linkLinesKeepTheirUrlAndKind() {
        val l = PlaylistImport.parseText("x", "https://www.youtube.com/watch?v=abc\nhttps://soundcloud.com/a/b\nhttps://example.com/a.mp3").tracks
        assertEquals(listOf(OnlineKind.YouTube, OnlineKind.SoundCloud, OnlineKind.Link), l.map { it.kind })
        assertTrue(l.all { it.url.startsWith("https://") })
    }

    @Test fun m3uWithExtinf() {
        val txt = "#EXTM3U\n#EXTINF:215,Queen - Bohemian Rhapsody\nhttp://x.test/q.mp3\n#EXTINF:-1,Local Song\nC:\\music\\a.mp3\n"
        val l = PlaylistImport.parseText("m", txt).tracks
        assertEquals(2, l.size)
        assertEquals("Bohemian Rhapsody", l[0].title); assertEquals("Queen", l[0].artist); assertEquals(215L, l[0].durationSec)
        assertEquals("http://x.test/q.mp3", l[0].url)
        assertEquals("", l[1].url)
        assertEquals("Local Song", l[1].title)
    }

    @Test fun exportifyCsv() {
        val csv = "Track URI,Track Name,Artist Name(s),Album Name,Duration (ms)\n" +
            "spotify:track:1,\"Hello, World\",Adele;Someone,21,245000\n" +
            "spotify:track:2,Skyfall,Adele,Skyfall,286000\n"
        val l = PlaylistImport.parseText("c", csv).tracks
        assertEquals(2, l.size)
        assertEquals("Hello, World", l[0].title); assertEquals("Adele, Someone", l[0].artist); assertEquals(245L, l[0].durationSec)
        assertEquals("Skyfall", l[1].title)
    }

    @Test fun csvRowQuotes() {
        assertEquals(listOf("a", "b,c", "d\"e"), PlaylistImport.csvRow("a,\"b,c\",\"d\"\"e\""))
    }

    @Test fun spotifyEmbed() {
        val html = """<html><script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"state":{"data":{"entity":{"title":"My Mix","trackList":[
            {"title":"Song A","subtitle":"Artist 1, Artist 2","duration":225834},{"title":"Song B","subtitle":"Artist 3","duration":180000}]}}}}}}</script></html>"""
        val l = PlaylistImport.parseSpotifyEmbed(html)
        assertEquals("My Mix", l.name)
        assertEquals(2, l.tracks.size)
        assertEquals("Artist 1, Artist 2", l.tracks[0].artist); assertEquals(225L, l.tracks[0].durationSec)
    }

    @Test fun playlistLinkDetection() {
        assertTrue(PlaylistImport.isPlaylistLink("https://www.youtube.com/playlist?list=PL123"))
        assertTrue(PlaylistImport.isPlaylistLink("https://soundcloud.com/u/sets/mix"))
        assertTrue(PlaylistImport.isPlaylistLink("https://open.spotify.com/playlist/37i9dQ"))
        assertTrue(PlaylistImport.isPlaylistLink("https://www.deezer.com/en/playlist/123"))
        assertFalse(PlaylistImport.isPlaylistLink("https://www.youtube.com/watch?v=abc&list=PL1"))
        assertFalse(PlaylistImport.isPlaylistLink("https://soundcloud.com/u/track"))
        assertFalse(PlaylistImport.isPlaylistLink("hello"))
    }
}
