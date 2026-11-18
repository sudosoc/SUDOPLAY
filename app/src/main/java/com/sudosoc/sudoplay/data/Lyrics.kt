package com.sudosoc.sudoplay.data

import android.content.Context
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset

data class LyricLine(val timeMs: Long, val text: String)
data class Lyrics(val lines: List<LyricLine>, val synced: Boolean)

/**
 * Lyrics sources (no network): a sidecar `.lrc` next to the audio file (needs "all files access" on Android 11+),
 * or an embedded ID3v2 USLT frame (MP3). Both plain text and LRC-style timestamps are understood.
 */
object LyricsLoader {
    private val ts = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

    fun load(ctx: Context, song: Song): Lyrics? {
        if (song.path.isNotEmpty()) {
            runCatching {
                val f = File(song.path)
                val lrc = File(f.parentFile, f.nameWithoutExtension + ".lrc")
                if (lrc.exists() && lrc.canRead()) parse(lrc.readText())?.let { return it }
            }
        }
        return readUslt(ctx, song)?.let { parse(it) } ?: LyricsFetcher.cached(ctx, song)
    }

    fun parse(text: String): Lyrics? {
        val lines = ArrayList<LyricLine>()
        var synced = false
        for (raw in text.lines()) {
            val ms = ts.findAll(raw).toList()
            if (ms.isNotEmpty()) {
                synced = true
                val content = raw.substring(ms.last().range.last + 1).trim()
                for (m in ms) {
                    val min = m.groupValues[1].toLong()
                    val sec = m.groupValues[2].toLong()
                    val frac = m.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                    lines += LyricLine(min * 60_000 + sec * 1000 + frac, content)
                }
            } else if (raw.isNotBlank() && !raw.trim().startsWith("[")) {
                lines += LyricLine(-1, raw.trim())
            }
        }
        if (lines.isEmpty()) return null
        return if (synced) Lyrics(lines.filter { it.timeMs >= 0 }.sortedBy { it.timeMs }, true) else Lyrics(lines, false)
    }

    // ---- ID3v2.3 / v2.4 USLT reader ----
    private fun readUslt(ctx: Context, song: Song): String? {
        try {
            ctx.contentResolver.openInputStream(song.uri)?.buffered(64 * 1024)?.use { ins ->
                val h = ByteArray(10)
                if (!readFully(ins, h)) return null
                if (h[0] != 'I'.code.toByte() || h[1] != 'D'.code.toByte() || h[2] != '3'.code.toByte()) return null
                val ver = h[3].toInt()
                if (ver < 3) return null
                var remaining = synchsafe(h, 6)
                if (h[5].toInt() and 0x40 != 0) {
                    val eh = ByteArray(4)
                    if (!readFully(ins, eh)) return null
                    if (ver == 4) { val sz = synchsafe(eh, 0); skipFully(ins, (sz - 4).toLong()); remaining -= sz }
                    else { val sz = bigEndian(eh, 0); skipFully(ins, sz.toLong()); remaining -= 4 + sz }
                }
                val fh = ByteArray(10)
                while (remaining >= 10) {
                    if (!readFully(ins, fh)) return null
                    remaining -= 10
                    if (fh[0].toInt() == 0) return null
                    val id = String(fh, 0, 4, Charsets.ISO_8859_1)
                    val size = if (ver == 4) synchsafe(fh, 4) else bigEndian(fh, 4)
                    if (size < 0 || size > remaining) return null
                    if (id == "USLT") {
                        if (size > 512 * 1024) return null
                        val body = ByteArray(size)
                        if (!readFully(ins, body)) return null
                        return decodeUslt(body)
                    }
                    skipFully(ins, size.toLong())
                    remaining -= size
                }
            }
        } catch (_: Exception) { }
        return null
    }

    private fun decodeUslt(b: ByteArray): String? {
        if (b.size < 5) return null
        val enc = b[0].toInt()
        val wide = enc == 1 || enc == 2
        var i = 4 // skip encoding byte + 3-byte language
        if (wide) {
            while (i + 1 < b.size && !(b[i].toInt() == 0 && b[i + 1].toInt() == 0)) i += 2
            i += 2
        } else {
            while (i < b.size && b[i].toInt() != 0) i++
            i += 1
        }
        if (i >= b.size) return null
        val cs: Charset = when (enc) { 0 -> Charsets.ISO_8859_1; 1 -> Charsets.UTF_16; 2 -> Charsets.UTF_16BE; else -> Charsets.UTF_8 }
        return String(b, i, b.size - i, cs).trim().ifEmpty { null }
    }

    private fun synchsafe(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0x7F) shl 21) or ((b[o + 1].toInt() and 0x7F) shl 14) or
            ((b[o + 2].toInt() and 0x7F) shl 7) or (b[o + 3].toInt() and 0x7F)

    private fun bigEndian(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
            ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)

    private fun readFully(ins: InputStream, buf: ByteArray): Boolean {
        var n = 0
        while (n < buf.size) { val r = ins.read(buf, n, buf.size - n); if (r < 0) return false; n += r }
        return true
    }

    private fun skipFully(ins: InputStream, count: Long) {
        var left = count
        while (left > 0) {
            val s = ins.skip(left)
            if (s <= 0) { if (ins.read() < 0) return else left-- } else left -= s
        }
    }
}
