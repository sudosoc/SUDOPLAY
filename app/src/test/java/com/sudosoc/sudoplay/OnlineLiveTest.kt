package com.sudosoc.sudoplay

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** Live probe (needs internet): does the extractor find and resolve audio right now? */
class OnlineLiveTest {
    private val http = OkHttpClient()
    private val dl = object : Downloader() {
        override fun execute(request: Request): Response {
            val b = okhttp3.Request.Builder().url(request.url())
            val d = request.dataToSend()
            b.method(request.httpMethod(), if (d != null) d.toRequestBody() else if (request.httpMethod() == "POST") ByteArray(0).toRequestBody() else null)
            b.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0")
            for ((k, v) in request.headers()) { b.removeHeader(k); v.forEach { b.addHeader(k, it) } }
            http.newCall(b.build()).execute().use { r -> return Response(r.code, r.message, r.headers.toMultimap(), r.body?.string() ?: "", r.request.url.toString()) }
        }
    }

    private fun probe(name: String, svc: org.schabi.newpipe.extractor.StreamingService, filter: String, q: String) {
        try {
            val info = SearchInfo.getInfo(svc, svc.searchQHFactory.fromQuery(q, listOf(filter), ""))
            val items = info.relatedItems.filterIsInstance<StreamInfoItem>().filter { it.duration in 60..900 }
            println("PROBE $name search: ${items.size} usable of ${info.relatedItems.size}; durations=${info.relatedItems.filterIsInstance<StreamInfoItem>().take(8).map { it.duration }}; first=${items.firstOrNull()?.name}")
            var ok = 0
            for (first in items.take(4)) {
                try {
                    val si = StreamInfo.getInfo(first.url)
                    val a = si.audioStreams.filter { it.isUrl }
                    val best = a.maxByOrNull { it.averageBitrate }
                    val cand = best?.content ?: si.videoStreams.firstOrNull { it.isUrl }?.content
                    if (cand != null) {
                        for (ua in listOf("Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0", "com.google.android.youtube/19.09.37 (Linux; U; Android 14) gzip", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36")) {
                            val r = http.newCall(okhttp3.Request.Builder().url(cand).header("User-Agent", ua).build()).execute()
                            r.use { println("PROBE $name fetch ua=${ua.take(22)} code=${it.code} type=${it.header("Content-Type")}") }
                        }
                    }
                    println("PROBE $name resolve '${first.name.take(30)}': audio=${si.audioStreams.size} url-streams=${a.size} best=${best?.let { "${it.format} ${it.averageBitrate}kbps method=${it.deliveryMethod} ${it.content.take(70)}" }}")
                    if (best != null) { ok++; break }
                } catch (e: Throwable) { println("PROBE $name resolve '${first.name.take(30)}' FAILED: ${generateSequence<Throwable>(e) { it.cause }.joinToString(" <- ") { it.javaClass.simpleName + ":" + it.message?.take(100) }}") }
            }
            println("PROBE $name RESULT ok=$ok")
        } catch (e: Throwable) { println("PROBE $name FAILED: ${e.javaClass.simpleName}: ${e.message?.take(200)}") }
    }

    @org.junit.Ignore("needs internet; run by hand to see whether YouTube/SoundCloud extraction still works")
    @Test fun live() {
        NewPipe.init(dl, Localization.DEFAULT)
        probe("YouTube", ServiceList.YouTube, "videos", "adele hello official audio")
        probe("SoundCloud", ServiceList.SoundCloud, "tracks", "chill beats")
    }
}
