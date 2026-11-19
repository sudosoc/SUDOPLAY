package com.sudosoc.sudoplay

import com.sudosoc.sudoplay.online.Friendly
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

class FriendlyTest {
    @Test fun offline() = assertTrue(Friendly.of(UnknownHostException("youtube.com")).startsWith("No internet"))
    @Test fun nested() = assertTrue(Friendly.of(RuntimeException("x", IOException("HTTP 429 Too Many Requests"))).startsWith("Too many"))
    @Test fun bot() = assertTrue(Friendly.of(IOException("Sign in to confirm you’re not a bot")).contains("not a bot"))
    @Test fun unknownKeepsMessage() = assertEquals("weird thing", Friendly.of(IOException("weird thing")))
}
