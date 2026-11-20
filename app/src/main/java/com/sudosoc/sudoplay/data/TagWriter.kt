package com.sudosoc.sudoplay.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.ByteArrayOutputStream

/** Title, artist, cover and lyrics to write into the audio file itself. */
class Tags(val title: String, val artist: String, val album: String = "SUDOPLAY", val cover: ByteArray? = null, val lyrics: String? = null)

/**
 * Writes the song's own tags into the downloaded file (MP3: ID3v2.3, M4A: iTunes atoms), so the name, cover and lyrics
 * stay with the file when it is copied to another phone, a computer or another player. Pure byte work, so it can be tested.
 * Any file this cannot handle is left exactly as it was.
 */
object TagWriter {
    private const val MAX_BYTES = 80L * 1024 * 1024

    /** Rewrites the file behind [uri] with tags. Returns true when tags were written and the file still plays; otherwise the original is restored. */
    fun write(ctx: Context, uri: Uri, mime: String?, tags: Tags): Boolean {
        val cr = ctx.contentResolver
        val original = runCatching { cr.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: return false
        if (original.size > MAX_BYTES) return false
        val tagged = when {
            looksMp3(original) -> tagMp3(original, tags)
            looksMp4(original) -> tagM4a(original, tags)
            else -> null
        } ?: return false
        return try {
            cr.openOutputStream(uri, "wt")?.use { it.write(tagged) } ?: return false
            if (playable(ctx, uri)) true else { restore(ctx, uri, original); false }
        } catch (e: Throwable) { restore(ctx, uri, original); false }
    }

    private fun restore(ctx: Context, uri: Uri, original: ByteArray) {
        runCatching { ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(original) } }
    }

    private fun playable(ctx: Context, uri: Uri): Boolean = runCatching {
        val r = MediaMetadataRetriever()
        try { r.setDataSource(ctx, uri); r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() != null } finally { r.release() }
    }.getOrDefault(false)

    fun looksMp3(b: ByteArray) = b.size > 10 && ((b[0] == 'I'.code.toByte() && b[1] == 'D'.code.toByte() && b[2] == '3'.code.toByte()) || (b[0] == 0xFF.toByte() && (b[1].toInt() and 0xE0) == 0xE0))
    fun looksMp4(b: ByteArray) = b.size > 12 && b[4] == 'f'.code.toByte() && b[5] == 't'.code.toByte() && b[6] == 'y'.code.toByte() && b[7] == 'p'.code.toByte()

    // ------------------------------------------------------------------------------------------ MP3 (ID3v2.3)
    fun tagMp3(src: ByteArray, tags: Tags): ByteArray {
        // drop an existing ID3v2 tag at the start
        var start = 0
        if (src[0] == 'I'.code.toByte() && src[1] == 'D'.code.toByte() && src[2] == '3'.code.toByte()) {
            val size = (src[6].toInt() shl 21) or (src[7].toInt() shl 14) or (src[8].toInt() shl 7) or src[9].toInt()
            start = (10 + size).coerceAtMost(src.size)
        }
        val frames = ByteArrayOutputStream()
        fun frame(id: String, body: ByteArray) {
            frames.write(id.toByteArray(Charsets.ISO_8859_1))
            frames.write(be32(body.size)); frames.write(byteArrayOf(0, 0)); frames.write(body)
        }
        fun utf16(s: String) = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + s.toByteArray(Charsets.UTF_16LE)
        fun text(id: String, s: String) { if (s.isNotBlank()) frame(id, byteArrayOf(1) + utf16(s) + byteArrayOf(0, 0)) }
        text("TIT2", tags.title); text("TPE1", tags.artist); text("TALB", tags.album)
        tags.lyrics?.takeIf { it.isNotBlank() }?.let { l ->
            frame("USLT", byteArrayOf(1) + "eng".toByteArray(Charsets.ISO_8859_1) + utf16("") + byteArrayOf(0, 0) + utf16(l) + byteArrayOf(0, 0))
        }
        tags.cover?.let { c -> frame("APIC", byteArrayOf(0) + "image/jpeg".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0, 3, 0) + c) }
        val body = frames.toByteArray()
        val out = ByteArrayOutputStream(body.size + src.size)
        out.write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0))
        out.write(synchsafe(body.size)); out.write(body)
        out.write(src, start, src.size - start)
        return out.toByteArray()
    }

    // ------------------------------------------------------------------------------------------ M4A (iTunes atoms)
    private class Box(val type: String, val start: Int, val size: Int, val header: Int)

    private fun boxes(b: ByteArray, from: Int, to: Int): List<Box>? {
        val out = ArrayList<Box>(); var p = from
        while (p < to) {
            if (p + 8 > to) return null
            var size = u32(b, p).toLong(); var header = 8
            val type = String(b, p + 4, 4, Charsets.ISO_8859_1)
            if (size == 1L) { if (p + 16 > to) return null; size = (u32(b, p + 8).toLong() shl 32) or u32(b, p + 12).toLong(); header = 16 }
            if (size == 0L) size = (to - p).toLong()
            if (size < header || p + size > to) return null
            out += Box(type, p, size.toInt(), header); p += size.toInt()
        }
        return out
    }

    fun tagM4a(src: ByteArray, tags: Tags): ByteArray? {
        val top = boxes(src, 0, src.size) ?: return null
        val moov = top.firstOrNull { it.type == "moov" } ?: return null
        val moovKids = boxes(src, moov.start + moov.header, moov.start + moov.size) ?: return null

        val ilst = ByteArrayOutputStream()
        fun item(type: ByteArray, flag: Int, payload: ByteArray) {
            val data = box("data", be32(flag) + be32(0) + payload)
            ilst.write(box(type, data))
        }
        val c9 = 0xA9.toByte()
        // the four-character names: ©nam, ©ART, ©alb, ©lyr
        fun tag4(name: String, s: String) { if (s.isNotBlank()) item(byteArrayOf(c9) + name.toByteArray(Charsets.ISO_8859_1), 1, s.toByteArray(Charsets.UTF_8)) }
        tag4("nam", tags.title); tag4("ART", tags.artist); tag4("alb", tags.album)
        tags.lyrics?.takeIf { it.isNotBlank() }?.let { tag4("lyr", it) }
        tags.cover?.let { item("covr".toByteArray(Charsets.ISO_8859_1), 13, it) }

        val hdlr = box("hdlr", be32(0) + be32(0) + "mdir".toByteArray() + "appl".toByteArray() + be32(0) + be32(0) + byteArrayOf(0))
        val meta = box("meta", be32(0) + hdlr + box("ilst", ilst.toByteArray()))
        val udta = box("udta", meta)

        val newKids = ByteArrayOutputStream()
        moovKids.filter { it.type != "udta" }.forEach { newKids.write(src, it.start, it.size) }
        newKids.write(udta)
        val newMoov = box("moov", newKids.toByteArray())
        val delta = newMoov.size - moov.size

        val out = ByteArrayOutputStream(src.size + delta + 16)
        out.write(src, 0, moov.start); out.write(newMoov); out.write(src, moov.start + moov.size, src.size - moov.start - moov.size)
        val res = out.toByteArray()

        // if the media data comes after the index, every chunk offset moves by the size change
        val mdatAfter = top.any { it.type == "mdat" && it.start > moov.start }
        if (mdatAfter && delta != 0 && !shiftOffsets(res, moov.start + 8, moov.start + newMoov.size, delta)) return null
        return if (boxes(res, 0, res.size) != null) res else null
    }

    /** Adds [delta] to every entry of every stco / co64 table below [from, to). */
    private fun shiftOffsets(b: ByteArray, from: Int, to: Int, delta: Int): Boolean {
        val kids = boxes(b, from, to) ?: return false
        for (k in kids) {
            when (k.type) {
                "trak", "mdia", "minf", "stbl", "edts" -> if (!shiftOffsets(b, k.start + k.header, k.start + k.size, delta)) return false
                "stco" -> {
                    val n = u32(b, k.start + 12); var p = k.start + 16
                    if (p + n.toLong() * 4 > k.start + k.size) return false
                    repeat(n.toInt()) { put32(b, p, u32(b, p) + delta); p += 4 }
                }
                "co64" -> {
                    val n = u32(b, k.start + 12); var p = k.start + 16
                    if (p + n.toLong() * 8 > k.start + k.size) return false
                    repeat(n.toInt()) { val v = (u32(b, p).toLong() shl 32) or u32(b, p + 4).toLong(); val w = v + delta; put32(b, p, (w ushr 32).toInt()); put32(b, p + 4, w.toInt()); p += 8 }
                }
            }
        }
        return true
    }

    // ------------------------------------------------------------------------------------------ helpers
    private fun u32(b: ByteArray, p: Int): Int = ((b[p].toInt() and 0xFF) shl 24) or ((b[p + 1].toInt() and 0xFF) shl 16) or ((b[p + 2].toInt() and 0xFF) shl 8) or (b[p + 3].toInt() and 0xFF)
    private fun put32(b: ByteArray, p: Int, v: Int) { b[p] = (v ushr 24).toByte(); b[p + 1] = (v ushr 16).toByte(); b[p + 2] = (v ushr 8).toByte(); b[p + 3] = v.toByte() }
    private fun be32(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
    private fun synchsafe(v: Int) = byteArrayOf(((v ushr 21) and 0x7F).toByte(), ((v ushr 14) and 0x7F).toByte(), ((v ushr 7) and 0x7F).toByte(), (v and 0x7F).toByte())
    private fun box(type: String, payload: ByteArray) = box(type.toByteArray(Charsets.ISO_8859_1), payload)
    private fun box(type: ByteArray, payload: ByteArray) = be32(payload.size + 8) + type + payload
}
