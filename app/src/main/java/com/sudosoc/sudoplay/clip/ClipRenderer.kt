package com.sudosoc.sudoplay.clip

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.sudosoc.sudoplay.data.Lyrics
import java.io.File
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Turns a part of a song into a vertical MP4: the audio is decoded, cut and re-encoded to AAC; every video frame is drawn
 * by [ClipPainter] with the real spectrum of that exact moment and encoded to H.264. No network, no extra libraries.
 */
object ClipRenderer {
    class Cancelled : RuntimeException()

    private class Pcm(val sr: Int, val ch: Int, val data: ShortArray)
    private class Au(val bytes: ByteArray, val pts: Long, val flags: Int)

    /** Returns the saved video's Uri (or file Uri on very old phones). */
    fun render(
        ctx: Context, songUri: Uri, startMs: Long, lenMs: Long, spec: ClipSpec, art: Bitmap?, lyrics: Lyrics?, hd: Boolean,
        progress: (String, Float) -> Unit, cancelled: () -> Boolean,
    ): Uri {
        val tmp = File(ctx.cacheDir, "clip_${System.currentTimeMillis()}.mp4")
        try {
            progress("Reading the audio…", 0f)
            val pcm = decodeSegment(ctx, songUri, startMs, lenMs, cancelled) ?: error("Could not decode this song")
            fade(pcm, 300)
            if (cancelled()) throw Cancelled()

            progress("Encoding the sound…", 0.08f)
            val (aacFormat, aac) = encodeAudio(pcm, cancelled)

            val w = if (hd) 1080 else 720
            val h = if (hd) 1920 else 1280
            val fps = 30
            val total = (pcm.data.size / pcm.ch * 1000L / pcm.sr * fps / 1000L).toInt().coerceAtLeast(1)

            val painter = ClipPainter(spec, art)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val argb = IntArray(w * h)

            val vf = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, if (hd) 10_000_000 else 6_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            val venc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            venc.configure(vf, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            venc.start()
            val muxer = MediaMuxer(tmp.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val audioTrack = muxer.addTrack(aacFormat)
            var videoTrack = -1
            var started = false
            var audioIdx = 0
            val info = MediaCodec.BufferInfo()
            val mono = FloatArray(pcm.data.size / pcm.ch) { i ->
                var s = 0f; for (k in 0 until pcm.ch) s += pcm.data[i * pcm.ch + k]; s / pcm.ch / 32768f
            }
            val spectrum = Spectrum(pcm.sr)
            var smooth = FloatArray(32)

            fun writeAudioUpTo(ptsUs: Long) {
                while (audioIdx < aac.size && aac[audioIdx].pts <= ptsUs) {
                    val a = aac[audioIdx++]
                    info.set(0, a.bytes.size, a.pts, a.flags)
                    muxer.writeSampleData(audioTrack, java.nio.ByteBuffer.wrap(a.bytes), info)
                }
            }

            fun drain(eos: Boolean) {
                var guard = 0
                while (guard++ < 200_000) {
                    val oi = venc.dequeueOutputBuffer(info, if (eos) 10_000 else 0)
                    when {
                        oi == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!eos) return
                        oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            videoTrack = muxer.addTrack(venc.outputFormat)
                            muxer.start(); started = true
                        }
                        oi >= 0 -> {
                            val buf = venc.getOutputBuffer(oi)
                            if (buf != null && info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && started) {
                                buf.position(info.offset); buf.limit(info.offset + info.size)
                                writeAudioUpTo(info.presentationTimeUs)
                                muxer.writeSampleData(videoTrack, buf, info)
                            }
                            venc.releaseOutputBuffer(oi, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                        }
                    }
                }
            }

            try {
                for (i in 0 until total) {
                    if (cancelled()) throw Cancelled()
                    val tMs = i * 1000L / fps
                    val center = (tMs * pcm.sr / 1000L).toInt()
                    val raw = spectrum.levels(mono, center)
                    for (b in raw.indices) smooth[b] = maxOf(raw[b], smooth[b] * 0.82f)

                    val absMs = startMs + tMs
                    var line: String? = null
                    var age = 9f
                    if (lyrics != null && lyrics.synced) {
                        val idx = lyrics.lines.indexOfLast { it.timeMs <= absMs }
                        if (idx >= 0) { line = lyrics.lines[idx].text; age = (absMs - lyrics.lines[idx].timeMs) / 1000f }
                    }
                    bmp.eraseColor(0)
                    painter.draw(canvas, w, h, i / total.toFloat(), smooth, line, age)
                    bmp.getPixels(argb, 0, w, 0, 0, w, h)

                    var idx: Int
                    while (true) {
                        idx = venc.dequeueInputBuffer(10_000)
                        if (idx >= 0) break
                        drain(false)
                        if (cancelled()) throw Cancelled()
                    }
                    fillInput(venc, idx, argb, w, h)
                    venc.queueInputBuffer(idx, 0, w * h * 3 / 2, i * 1_000_000L / fps, 0)
                    drain(false)
                    if (i % 6 == 0) progress("Drawing the video…", 0.12f + 0.80f * i / total)
                }
                var eosIdx: Int
                while (true) { eosIdx = venc.dequeueInputBuffer(10_000); if (eosIdx >= 0) break; drain(false) }
                venc.queueInputBuffer(eosIdx, 0, 0, total * 1_000_000L / fps, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                drain(true)
                writeAudioUpTo(Long.MAX_VALUE)
                if (started) muxer.stop()
            } finally {
                runCatching { venc.stop() }; runCatching { venc.release() }; runCatching { muxer.release() }
                bmp.recycle()
            }

            progress("Saving…", 0.96f)
            val name = "SUDOPLAY_${spec.title.filter { it.isLetterOrDigit() }.take(18).ifEmpty { "clip" }}_${System.currentTimeMillis() / 1000}.mp4"
            return saveVideo(ctx, tmp, name)
        } finally {
            tmp.delete()
        }
    }

    // ------------------------------------------------------------------------------------------ audio
    private fun decodeSegment(ctx: Context, uri: Uri, startMs: Long, lenMs: Long, cancelled: () -> Boolean): Pcm? {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(ctx, uri, null)
            var track = -1
            for (i in 0 until ex.trackCount) if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { track = i; break }
            if (track < 0) return null
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            val startUs = startMs * 1000L
            ex.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(fmt, null, null, 0); codec.start()

            var sr = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var inCh = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var isFloat = false
            val outCh = if (inCh >= 2) 2 else 1
            val wantFrames = (lenMs * sr / 1000L).toInt()
            var data = ShortArray(wantFrames * outCh)
            var got = 0
            val info = MediaCodec.BufferInfo()
            var inDone = false; var outDone = false; var guard = 0
            while (!outDone && got < wantFrames && guard++ < 3_000_000) {
                if (cancelled()) throw Cancelled()
                if (!inDone) {
                    val ii = codec.dequeueInputBuffer(4000)
                    if (ii >= 0) {
                        val b = codec.getInputBuffer(ii) ?: continue
                        val n = ex.readSampleData(b, 0)
                        if (n < 0) { codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                        else { codec.queueInputBuffer(ii, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, 4000)
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val of = codec.outputFormat
                    sr = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    inCh = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    isFloat = of.containsKey(MediaFormat.KEY_PCM_ENCODING) && of.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (oi >= 0) {
                    val out = codec.getOutputBuffer(oi)
                    if (out != null && info.size > 0) {
                        out.position(info.offset); out.limit(info.offset + info.size); out.order(ByteOrder.nativeOrder())
                        val c = inCh.coerceAtLeast(1)
                        val frames = info.size / ((if (isFloat) 4 else 2) * c)
                        val skip = ((startUs - info.presentationTimeUs) * sr / 1_000_000L).toInt().coerceIn(0, frames)
                        val sb = if (!isFloat) out.asShortBuffer() else null
                        val fb = if (isFloat) out.asFloatBuffer() else null
                        for (f in 0 until frames) {
                            val s0: Float; val s1: Float
                            if (sb != null) { s0 = sb.get(f * c) / 32768f; s1 = if (c > 1) sb.get(f * c + 1) / 32768f else s0 }
                            else { s0 = fb!!.get(f * c); s1 = if (c > 1) fb.get(f * c + 1) else s0 }
                            if (f < skip) continue
                            if (got >= wantFrames) break
                            data[got * outCh] = (s0 * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                            if (outCh == 2) data[got * outCh + 1] = (s1 * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                            got++
                        }
                    }
                    codec.releaseOutputBuffer(oi, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                }
            }
            if (got < sr) return null
            if (got < wantFrames) data = data.copyOf(got * outCh)
            return Pcm(sr, outCh, data)
        } finally {
            runCatching { codec?.stop() }; runCatching { codec?.release() }; runCatching { ex.release() }
        }
    }

    private fun fade(p: Pcm, ms: Int) {
        val n = (p.sr * ms / 1000).coerceAtMost(p.data.size / p.ch / 2)
        for (i in 0 until n) {
            val g = i / n.toFloat()
            for (k in 0 until p.ch) {
                val a = i * p.ch + k
                p.data[a] = (p.data[a] * g).toInt().toShort()
                val b = p.data.size - 1 - (i * p.ch + (p.ch - 1 - k))
                p.data[b] = (p.data[b] * g).toInt().toShort()
            }
        }
    }

    private fun encodeAudio(p: Pcm, cancelled: () -> Boolean): Pair<MediaFormat, List<Au>> {
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, p.sr, p.ch).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 160_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 32 * 1024)
        }
        val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        enc.start()
        val out = ArrayList<Au>()
        var outFormat: MediaFormat? = null
        val info = MediaCodec.BufferInfo()
        var fedSamples = 0                         // shorts
        val frameBytes = 2 * p.ch
        var eosSent = false
        var done = false
        var guard = 0
        try {
            while (!done && guard++ < 2_000_000) {
                if (cancelled()) throw Cancelled()
                if (!eosSent) {
                    val ii = enc.dequeueInputBuffer(4000)
                    if (ii >= 0) {
                        val b = enc.getInputBuffer(ii)!!
                        b.clear()
                        val remaining = p.data.size - fedSamples
                        if (remaining <= 0) {
                            enc.queueInputBuffer(ii, 0, 0, fedSamples / p.ch * 1_000_000L / p.sr, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            eosSent = true
                        } else {
                            val n = minOf(remaining, b.capacity() / 2) / p.ch * p.ch
                            b.order(ByteOrder.nativeOrder())
                            val sb = b.asShortBuffer(); sb.put(p.data, fedSamples, n)
                            enc.queueInputBuffer(ii, 0, n * 2, fedSamples / p.ch * 1_000_000L / p.sr, 0)
                            fedSamples += n
                        }
                    }
                }
                val oi = enc.dequeueOutputBuffer(info, 4000)
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) outFormat = enc.outputFormat
                else if (oi >= 0) {
                    val b = enc.getOutputBuffer(oi)
                    if (b != null && info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        val bytes = ByteArray(info.size); b.position(info.offset); b.get(bytes)
                        out += Au(bytes, info.presentationTimeUs, info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM.inv())
                    }
                    enc.releaseOutputBuffer(oi, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) done = true
                }
            }
        } finally { runCatching { enc.stop() }; runCatching { enc.release() } }
        return (outFormat ?: error("AAC encoder gave no format")) to out
    }

    // ------------------------------------------------------------------------------------------ video input
    /** ARGB → I420/NV12 written straight into the encoder's input image. */
    private fun fillInput(codec: MediaCodec, idx: Int, argb: IntArray, w: Int, h: Int) {
        val img = codec.getInputImage(idx)
        val yB = ByteArray(w * h)
        val cw = w / 2; val chh = h / 2
        val uB = ByteArray(cw * chh); val vB = ByteArray(cw * chh)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val px = argb[row + x]
                val r = (px shr 16) and 0xFF; val g = (px shr 8) and 0xFF; val b = px and 0xFF
                yB[row + x] = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).toByte()
            }
        }
        for (y in 0 until chh) for (x in 0 until cw) {
            val p0 = argb[(2 * y) * w + 2 * x]; val p1 = argb[(2 * y) * w + 2 * x + 1]
            val p2 = argb[(2 * y + 1) * w + 2 * x]; val p3 = argb[(2 * y + 1) * w + 2 * x + 1]
            val r = (((p0 shr 16) and 0xFF) + ((p1 shr 16) and 0xFF) + ((p2 shr 16) and 0xFF) + ((p3 shr 16) and 0xFF)) shr 2
            val g = (((p0 shr 8) and 0xFF) + ((p1 shr 8) and 0xFF) + ((p2 shr 8) and 0xFF) + ((p3 shr 8) and 0xFF)) shr 2
            val b = ((p0 and 0xFF) + (p1 and 0xFF) + (p2 and 0xFF) + (p3 and 0xFF)) shr 2
            uB[y * cw + x] = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).toByte()
            vB[y * cw + x] = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).toByte()
        }
        if (img != null) {
            val planes = img.planes
            putPlane(planes[0].buffer, planes[0].rowStride, planes[0].pixelStride, yB, w, h)
            putPlane(planes[1].buffer, planes[1].rowStride, planes[1].pixelStride, uB, cw, chh)
            putPlane(planes[2].buffer, planes[2].rowStride, planes[2].pixelStride, vB, cw, chh)
            img.close()
        } else {
            // very old encoders: plain NV12 in the raw buffer
            val buf = codec.getInputBuffer(idx)!!
            buf.clear(); buf.put(yB)
            val uv = ByteArray(cw * chh * 2)
            for (i in 0 until cw * chh) { uv[2 * i] = uB[i]; uv[2 * i + 1] = vB[i] }
            buf.put(uv)
        }
    }

    private fun putPlane(buf: java.nio.ByteBuffer, rowStride: Int, pixelStride: Int, src: ByteArray, w: Int, h: Int) {
        if (pixelStride == 1 && rowStride == w) { buf.position(0); buf.put(src, 0, w * h); return }
        for (y in 0 until h) {
            if (pixelStride == 1) { buf.position(y * rowStride); buf.put(src, y * w, w) }
            else for (x in 0 until w) buf.put(y * rowStride + x * pixelStride, src[y * w + x])
        }
    }

    // ------------------------------------------------------------------------------------------ output
    private fun saveVideo(ctx: Context, file: File, name: String): Uri {
        if (Build.VERSION.SDK_INT >= 29) {
            val cv = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SUDOPLAY")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv) ?: error("Could not create the file")
            ctx.contentResolver.openOutputStream(uri)!!.use { o -> file.inputStream().use { it.copyTo(o) } }
            ctx.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            return uri
        }
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "SUDOPLAY").apply { mkdirs() }
        val dest = File(dir, name)
        file.copyTo(dest, overwrite = true)
        return Uri.fromFile(dest)
    }

    fun saveImage(ctx: Context, bmp: Bitmap, name: String): Uri {
        if (Build.VERSION.SDK_INT >= 29) {
            val cv = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SUDOPLAY")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: error("Could not create the file")
            ctx.contentResolver.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            ctx.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            return uri
        }
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "SUDOPLAY").apply { mkdirs() }
        val dest = File(dir, name)
        dest.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(dest)
    }
}

/** 2048-point spectrum → 32 log-spaced band levels (0..1), the same mapping the live visualiser uses. */
class Spectrum(private val sr: Int) {
    private val n = 2048
    private val re = FloatArray(n)
    private val im = FloatArray(n)
    private val win = FloatArray(n) { (0.5 - 0.5 * cos(2.0 * PI * it / (n - 1))).toFloat() }

    fun levels(mono: FloatArray, center: Int): FloatArray {
        val start = center - n / 2
        for (i in 0 until n) { val k = start + i; re[i] = (if (k in mono.indices) mono[k] else 0f) * win[i]; im[i] = 0f }
        fft()
        val bands = 32
        val res = FloatArray(bands)
        val binHz = sr / n.toFloat()
        for (b in 0 until bands) {
            val f0 = 40f * (16000f / 40f).pow(b / bands.toFloat())
            val f1 = 40f * (16000f / 40f).pow((b + 1) / bands.toFloat())
            val i0 = (f0 / binHz).toInt().coerceIn(1, n / 2 - 1)
            val i1 = (f1 / binHz).toInt().coerceIn(i0, n / 2 - 1)
            var m = 0f
            for (i in i0..i1) { val mag = sqrt(re[i] * re[i] + im[i] * im[i]); if (mag > m) m = mag }
            val db = 20f * log10(m / 512f + 1e-6f) + b * 0.55f
            res[b] = ((db + 72f) / 60f).coerceIn(0f, 1f)
        }
        return res
    }

    private fun fft() {
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val tr = re[i]; re[i] = re[j]; re[j] = tr; val ti = im[i]; im[i] = im[j]; im[j] = ti }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wlr = cos(ang).toFloat(); val wli = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var wr = 1f; var wi = 0f
                val half = len / 2
                for (k in 0 until half) {
                    val a = i + k; val b = a + half
                    val vr = re[b] * wr - im[b] * wi
                    val vi = re[b] * wi + im[b] * wr
                    re[b] = re[a] - vr; im[b] = im[a] - vi
                    re[a] += vr; im[a] += vi
                    val nwr = wr * wlr - wi * wli
                    wi = wr * wli + wi * wlr
                    wr = nwr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
