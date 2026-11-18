package com.sudosoc.sudoplay.playback

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * One decode pass over a song gives us two things:
 *  - a loudness-normalisation gain (ReplayGain-style: how many dB to add so every song plays at a similar level)
 *  - a 160-bin peak envelope for the real waveform seek bar
 * Results are cached in files so each song is only ever analysed once.
 */
object Analyzer {
    const val BINS = 160
    private const val TARGET_DB = -14f      // target mean level (dBFS, RMS) of a normalised song
    private const val MAX_FULL_US = 15 * 60_000_000L

    class Result(val gainDb: Float, val peaks: FloatArray)

    private fun file(ctx: Context, id: Long) = File(File(ctx.filesDir, "analysis").apply { mkdirs() }, "$id.txt")

    fun cached(ctx: Context, id: Long): Result? = runCatching {
        val f = file(ctx, id)
        if (!f.exists()) return null
        val parts = f.readText().split(";")
        val gain = parts[0].toFloat()
        val peaks = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }?.split(",")?.map { it.toInt() / 255f }?.toFloatArray() ?: FloatArray(0)
        Result(gain, peaks)
    }.getOrNull()

    fun analyze(ctx: Context, id: Long, uri: Uri, durationMs: Long): Result? {
        cached(ctx, id)?.let { return it }
        val r = decode(ctx, uri, durationMs) ?: return null
        runCatching { file(ctx, id).writeText("${r.gainDb};" + r.peaks.joinToString(",") { (it * 255).toInt().toString() }) }
        return r
    }

    private fun decode(ctx: Context, uri: Uri, durationMs: Long): Result? {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(ctx, uri, null)
            var track = -1
            for (i in 0 until ex.trackCount) {
                if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { track = i; break }
            }
            if (track < 0) return null
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: return null
            val durUs = (if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else durationMs * 1000L).coerceAtLeast(1L)
            val full = durUs <= MAX_FULL_US
            if (!full) ex.seekTo(durUs / 3, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(fmt, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var sr = fmt.getIntegerOr(MediaFormat.KEY_SAMPLE_RATE, 44100)
            var ch = fmt.getIntegerOr(MediaFormat.KEY_CHANNEL_COUNT, 2)
            var isFloat = false
            val totalFrames = (durUs * sr / 1_000_000L).coerceAtLeast(1L)
            val peaks = FloatArray(BINS)
            var sumSq = 0.0
            var counted = 0L
            var peak = 0f
            var frames = 0L
            var inDone = false
            var outDone = false
            var guard = 0

            while (!outDone && guard++ < 2_000_000) {
                if (!inDone) {
                    val ii = codec.dequeueInputBuffer(4000)
                    if (ii >= 0) {
                        val buf = codec.getInputBuffer(ii) ?: continue
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) { codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                        else { codec.queueInputBuffer(ii, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, 4000)
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val of = codec.outputFormat
                    sr = of.getIntegerOr(MediaFormat.KEY_SAMPLE_RATE, sr)
                    ch = of.getIntegerOr(MediaFormat.KEY_CHANNEL_COUNT, ch)
                    isFloat = of.getIntegerOr(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (oi >= 0) {
                    val out = codec.getOutputBuffer(oi)
                    if (out != null && info.size > 0) {
                        out.position(info.offset); out.limit(info.offset + info.size); out.order(ByteOrder.nativeOrder())
                        val c = ch.coerceAtLeast(1)
                        if (isFloat) {
                            val fb = out.asFloatBuffer()
                            val n = fb.remaining() / c
                            for (f in 0 until n) {
                                var m = 0f; var pk = 0f
                                for (k in 0 until c) { val v = fb.get(); m += v; pk = maxOf(pk, abs(v)) }
                                m /= c
                                if (pk > peak) peak = pk
                                if (abs(m) > 0.001f) { sumSq += (m * m).toDouble(); counted++ }
                                if (full) { val b = ((frames * BINS) / totalFrames).toInt().coerceIn(0, BINS - 1); if (abs(m) > peaks[b]) peaks[b] = abs(m) }
                                frames++
                            }
                        } else {
                            val sb = out.asShortBuffer()
                            val n = sb.remaining() / c
                            for (f in 0 until n) {
                                var m = 0f; var pk = 0f
                                for (k in 0 until c) { val v = sb.get() / 32768f; m += v; pk = maxOf(pk, abs(v)) }
                                m /= c
                                if (pk > peak) peak = pk
                                if (abs(m) > 0.001f) { sumSq += (m * m).toDouble(); counted++ }
                                if (full) { val b = ((frames * BINS) / totalFrames).toInt().coerceIn(0, BINS - 1); if (abs(m) > peaks[b]) peaks[b] = abs(m) }
                                frames++
                            }
                        }
                    }
                    codec.releaseOutputBuffer(oi, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                    if (!full && frames > sr * 30L) outDone = true   // a 30 s sample is plenty for very long files
                }
            }
            if (counted < sr) return null
            val rmsDb = 10f * log10((sumSq / counted).toFloat() + 1e-12f)
            var gain = (TARGET_DB - rmsDb).coerceIn(-12f, 6f)
            if (gain > 0f) gain = minOf(gain, -20f * log10(peak.coerceAtLeast(0.01f)))   // never push the peaks past full scale
            val mx = peaks.maxOrNull() ?: 0f
            val norm = if (full && mx > 0f) FloatArray(BINS) { sqrt(peaks[it] / mx) } else FloatArray(0)
            return Result(gain.coerceIn(-12f, 6f), norm)
        } catch (_: Throwable) {
            return null
        } finally {
            runCatching { codec?.stop() }; runCatching { codec?.release() }; runCatching { ex.release() }
        }
    }

    private fun MediaFormat.getIntegerOr(key: String, def: Int): Int = if (containsKey(key)) getInteger(key) else def
}
