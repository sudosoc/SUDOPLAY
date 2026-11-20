package com.sudosoc.sudoplay

import com.sudosoc.sudoplay.data.TagWriter
import com.sudosoc.sudoplay.data.Tags
import org.junit.Assert.*
import org.junit.Test

class TagWriterTest {
    private fun be32(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
    private fun box(t: String, p: ByteArray) = be32(p.size + 8) + t.toByteArray() + p
    private fun u32(b: ByteArray, p: Int) = ((b[p].toInt() and 0xFF) shl 24) or ((b[p + 1].toInt() and 0xFF) shl 16) or ((b[p + 2].toInt() and 0xFF) shl 8) or (b[p + 3].toInt() and 0xFF)

    private val payload = ByteArray(64) { (it + 1).toByte() }

    /** ftyp + moov(stco → first byte of the media) + mdat, with the index first like "faststart" files. */
    private fun fastStart(): ByteArray {
        val ftyp = box("ftyp", "M4A ".toByteArray() + be32(0) + "M4A mp42".toByteArray())
        fun moov(off: Int) = box("moov", box("mvhd", ByteArray(100)) + box("trak", box("mdia", box("minf", box("stbl", box("stco", be32(0) + be32(1) + be32(off)))))) + box("udta", ByteArray(12)))
        val moovLen = moov(0).size
        return ftyp + moov(ftyp.size + moovLen + 8) + box("mdat", payload)
    }

    private fun find(b: ByteArray, tag: String): Int { val t = tag.toByteArray(); outer@ for (i in 0..b.size - 4) { for (j in 0..3) if (b[i + j] != t[j]) continue@outer; return i }; return -1 }

    @Test fun m4aOffsetsFollowTheMedia() {
        val src = fastStart()
        val out = TagWriter.tagM4a(src, Tags("Song", "Artist", "Album", cover = ByteArray(300) { 7 }, lyrics = "la la"))!!
        val stco = find(out, "stco")
        val off = u32(out, stco + 4 + 8)
        assertArrayEquals(payload, out.copyOfRange(off, off + payload.size))
        assertTrue(find(out, "ilst") > 0 && find(out, "covr") > 0)
        assertTrue(out.size > src.size)
    }

    @Test fun m4aWithMediaFirstKeepsOffsets() {
        val ftyp = box("ftyp", "M4A ".toByteArray() + be32(0))
        val mdat = box("mdat", payload)
        val off = ftyp.size + 8
        val moov = box("moov", box("trak", box("mdia", box("minf", box("stbl", box("stco", be32(0) + be32(1) + be32(off)))))))
        val out = TagWriter.tagM4a(ftyp + mdat + moov, Tags("T", "A"))!!
        val stco = find(out, "stco")
        assertEquals(off, u32(out, stco + 12))
        assertArrayEquals(payload, out.copyOfRange(off, off + payload.size))
    }

    @Test fun brokenMp4IsRefused() {
        val broken = box("ftyp", ByteArray(8)) + be32(5000) + "moov".toByteArray() + ByteArray(10)
        assertNull(TagWriter.tagM4a(broken, Tags("T", "A")))
    }

    @Test fun mp3GetsId3AndKeepsAudio() {
        val audio = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00) + ByteArray(50) { 9 }
        val out = TagWriter.tagMp3(audio, Tags("سلام", "فنان", cover = ByteArray(20) { 1 }, lyrics = "x"))
        assertEquals("ID3", String(out, 0, 3))
        val size = (out[6].toInt() shl 21) or (out[7].toInt() shl 14) or (out[8].toInt() shl 7) or out[9].toInt()
        assertArrayEquals(audio, out.copyOfRange(10 + size, out.size))
        assertTrue(find(out, "TIT2") > 0 && find(out, "APIC") > 0 && find(out, "USLT") > 0)
    }

    @Test fun mp3WithOldTagIsReplacedNotStacked() {
        val audio = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00) + ByteArray(20) { 5 }
        val once = TagWriter.tagMp3(audio, Tags("One", "A"))
        val twice = TagWriter.tagMp3(once, Tags("Two", "A"))
        val size = (twice[6].toInt() shl 21) or (twice[7].toInt() shl 14) or (twice[8].toInt() shl 7) or twice[9].toInt()
        assertArrayEquals(audio, twice.copyOfRange(10 + size, twice.size))
    }
}
