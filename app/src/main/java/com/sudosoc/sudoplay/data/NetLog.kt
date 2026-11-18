package com.sudosoc.sudoplay.data

import androidx.compose.runtime.mutableStateListOf

/**
 * Every network request SUDOPLAY ever makes is written here, so you can see exactly what leaves the phone.
 * (The network is only used by the optional lyrics download and the Online tab; the audio itself, once it plays, streams straight from its source.)
 */
class NetEntry(val time: Long, val method: String, val host: String, val path: String, val status: Int, val bytes: Long, val ms: Long)

object NetLog {
    val entries = mutableStateListOf<NetEntry>()
    @Synchronized fun add(e: NetEntry) {
        entries.add(0, e)
        while (entries.size > 200) entries.removeAt(entries.lastIndex)
    }
    fun clear() = entries.clear()
}
