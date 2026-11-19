package com.sudosoc.sudoplay.online

/** Turns raw extractor / network errors into one plain sentence a person can act on. */
object Friendly {
    fun of(e: Throwable, max: Int = 200): String {
        val chain = generateSequence(e) { it.cause }.take(6).toList()
        val raw = chain.firstNotNullOfOrNull { it.message?.takeIf { m -> m.isNotBlank() } } ?: e.javaClass.simpleName
        val l = (chain.joinToString(" ") { it.javaClass.simpleName + " " + (it.message ?: "") }).lowercase()
        val s = when {
            "unknownhost" in l || "unable to resolve host" in l || "failed to connect" in l || "sockettimeout" in l || "timeout" in l || "network is unreachable" in l ->
                "No internet connection, or the site can't be reached. Check your network and try again."
            "sign in to confirm" in l || "not a bot" in l || "recaptcha" in l ->
                "YouTube wants to confirm you're not a bot on this network. Try again later, or use SoundCloud."
            "age" in l && "restrict" in l -> "This video is age-restricted, so it can't be played here."
            "private" in l -> "This item is private."
            "unplayable" in l || "not available" in l || "unavailable" in l || "removed" in l || "blocked in your country" in l ->
                "Not available — it was removed or is blocked in your country."
            "429" in l || "too many requests" in l -> "Too many requests. Wait a minute and try again."
            "403" in l || "forbidden" in l -> "The source refused the request (HTTP 403). This often passes after a few minutes."
            "404" in l || "not found" in l -> "Not found. The link may be wrong or the item was deleted."
            else -> raw
        }
        return s.take(max)
    }
}
