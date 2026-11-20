package com.sudosoc.sudoplay.playback

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Replay cache for streamed songs: what you have already listened to plays again instantly, seeks inside it
 * are instant, and it keeps playing through short network drops. Least-recently-used files are dropped at the size limit.
 */
@UnstableApi
object StreamCache {
    private var cache: SimpleCache? = null
    private var limitMb = -1

    private fun dir(ctx: Context) = File(ctx.cacheDir, "stream_cache")

    @Synchronized
    fun get(ctx: Context, mb: Int): SimpleCache? {
        if (mb <= 0) { release(); return null }
        if (cache != null && limitMb == mb) return cache
        release()
        return runCatching {
            SimpleCache(dir(ctx), LeastRecentlyUsedCacheEvictor(mb * 1024L * 1024L), StandaloneDatabaseProvider(ctx)).also { cache = it; limitMb = mb }
        }.getOrNull()
    }

    @Synchronized
    fun release() { runCatching { cache?.release() }; cache = null; limitMb = -1 }

    /** Bytes currently stored. */
    fun usedBytes(ctx: Context): Long = cache?.cacheSpace ?: dir(ctx).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    @Synchronized
    fun clear(ctx: Context) {
        val mb = limitMb
        release()
        runCatching { dir(ctx).deleteRecursively() }
        if (mb > 0) get(ctx, mb)
    }

    /**
     * Data sources for the player: plain for local files, live radio and playlists (no cache key),
     * cached for streamed songs, which the app marks with a stable cache key (their links change every time they are resolved).
     */
    fun factory(ctx: Context, mb: () -> Int): DataSource.Factory {
        val upstream = DefaultDataSource.Factory(ctx)
        return DataSource.Factory {
            val plain = upstream.createDataSource()
            val c = get(ctx, mb())
            if (c == null) plain
            else Selective(plain, CacheDataSource.Factory().setCache(c).setUpstreamDataSourceFactory(upstream).setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR).createDataSource())
        }
    }

    private class Selective(private val plain: DataSource, private val cached: DataSource) : DataSource {
        private var active: DataSource? = null
        override fun addTransferListener(l: TransferListener) { plain.addTransferListener(l); cached.addTransferListener(l) }
        override fun open(dataSpec: DataSpec): Long {
            active = if (dataSpec.key != null && dataSpec.uri.scheme?.startsWith("http") == true) cached else plain
            return active!!.open(dataSpec)
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = active!!.read(buffer, offset, length)
        override fun getUri() = active?.uri
        override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()
        override fun close() { try { active?.close() } finally { active = null } }
    }
}
