package com.sudosoc.sudoplay

import com.sudosoc.sudoplay.online.YtPlaylist
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class YtPlaylistTest {
    private val page = """{"contents":{"twoColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[{"itemSectionRenderer":{"contents":[
      {"lockupViewModel":{"contentId":"ekr2nIex040","contentType":"LOCKUP_CONTENT_TYPE_VIDEO",
        "contentImage":{"thumbnailViewModel":{"overlays":[{"thumbnailBottomOverlayViewModel":{"badges":[{"thumbnailBadgeViewModel":{"text":"2:54"}}]}}]}},
        "metadata":{"lockupMetadataViewModel":{"title":{"content":"Song One"},"metadata":{"contentMetadataViewModel":{"metadataRows":[{"metadataParts":[{"text":{"content":"Some Artist - Topic"}}]},{"metadataParts":[{"text":{"content":"1.2M views"}}]}]}}}}}},
      {"lockupViewModel":{"contentId":"abcdefghijk","contentType":"LOCKUP_CONTENT_TYPE_PLAYLIST","metadata":{"lockupMetadataViewModel":{"title":{"content":"Not a song"}}}}},
      {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"TOK123"}}}}
    ]}}]}}}}]}},"metadata":{"playlistMetadataRenderer":{"title":"My Mix"}}}"""

    @Test fun readsSongsNameAndContinuation() {
        val p = YtPlaylist.parseJson(JSONObject(page), true)
        assertEquals("My Mix", p.name)
        assertEquals(1, p.tracks.size)
        val t = p.tracks[0]
        assertEquals("Song One", t.title); assertEquals("Some Artist", t.artist); assertEquals(174L, t.durationSec)
        assertEquals("https://www.youtube.com/watch?v=ekr2nIex040", t.url)
        assertEquals("TOK123", p.next)
    }

    @Test fun listIdAndClock() {
        assertEquals("PL123-_x", YtPlaylist.idOf("https://www.youtube.com/watch?v=abc&list=PL123-_x&index=2"))
        assertNull(YtPlaylist.idOf("https://youtu.be/abc"))
        assertEquals(3725L, YtPlaylist.parseClock("1:02:05")); assertNull(YtPlaylist.parseClock("LIVE"))
    }
}
