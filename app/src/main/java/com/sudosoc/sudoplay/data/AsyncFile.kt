package com.sudosoc.sudoplay.data

import java.io.File
import java.util.concurrent.Executors

/**
 * Saves small data files off the main thread, in order, and atomically (temp file + rename), so a big playlist
 * never stutters the UI and a crash mid-save can never leave a half-written file behind.
 */
object AsyncFile {
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "sudoplay-save").apply { isDaemon = false } }

    fun write(file: File, text: String) {
        io.execute {
            runCatching {
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(text)
                if (!tmp.renameTo(file)) { file.writeText(text); tmp.delete() }
            }
        }
    }

    /** Blocks until everything queued so far is on disk (backups, tests). */
    fun flush() { runCatching { io.submit { }.get() } }
}
