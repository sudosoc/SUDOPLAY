package com.sudosoc.sudoplay.data

import android.content.Context
import android.os.Build
import java.io.File

/** Keeps the last crashes in a small file, so "Report a problem" can send them. Nothing leaves the phone unless the user shares it. */
object CrashLog {
    private fun file(ctx: Context) = File(ctx.filesDir, "crash.txt")

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val f = file(app)
                val entry = "=== ${java.util.Date()} • ${deviceLine(app)}\n${e.stackTraceToString().take(6000)}\n\n"
                f.writeText((entry + (if (f.exists()) f.readText() else "")).take(20_000))
            }
            old?.uncaughtException(t, e)
        }
    }

    fun deviceLine(ctx: Context): String {
        val v = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
        return "SUDOPLAY $v • Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) • ${Build.MANUFACTURER} ${Build.MODEL}"
    }
    fun read(ctx: Context): String? = file(ctx).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }
    fun clear(ctx: Context) { file(ctx).delete() }
}
