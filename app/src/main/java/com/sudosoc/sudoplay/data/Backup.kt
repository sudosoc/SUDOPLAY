package com.sudosoc.sudoplay.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.io.File

/** One JSON file with every setting, scene, play count and playlist (online songs included), so a new phone (or a reinstall) can pick up where you left off. */
object Backup {
    fun export(ctx: Context, prefs: SharedPreferences): String {
        val p = JSONObject()
        for ((k, v) in prefs.all) {
            val o = JSONObject()
            when (v) {
                is Boolean -> o.put("t", "b").put("v", v)
                is Int -> o.put("t", "i").put("v", v)
                is Long -> o.put("t", "l").put("v", v)
                is Float -> o.put("t", "f").put("v", v.toDouble())
                is String -> o.put("t", "s").put("v", v)
                else -> continue
            }
            p.put(k, o)
        }
        AsyncFile.flush()
        val pl = File(ctx.filesDir, "playlists.json").takeIf { it.exists() }?.readText() ?: "[]"
        // online songs inside playlists travel too (the "downloaded as" map does not: file ids differ on another phone)
        val ol = runCatching { File(ctx.filesDir, "onlinelib.json").takeIf { it.exists() }?.readText()?.let { JSONObject(it).put("l", org.json.JSONArray()).toString() } }.getOrNull() ?: ""
        return JSONObject().put("app", "SUDOPLAY").put("version", 1).put("prefs", p).put("playlists", pl).put("onlinelib", ol).toString()
    }

    /** Returns true when the file was a valid SUDOPLAY backup and has been applied (restart the app afterwards). */
    fun import(ctx: Context, prefs: SharedPreferences, json: String): Boolean = runCatching {
        val root = JSONObject(json)
        if (root.optString("app") != "SUDOPLAY") return false
        val p = root.getJSONObject("prefs")
        val e = prefs.edit().clear()
        for (k in p.keys()) {
            val o = p.getJSONObject(k)
            when (o.getString("t")) {
                "b" -> e.putBoolean(k, o.getBoolean("v"))
                "i" -> e.putInt(k, o.getInt("v"))
                "l" -> e.putLong(k, o.getLong("v"))
                "f" -> e.putFloat(k, o.getDouble("v").toFloat())
                "s" -> e.putString(k, o.getString("v"))
            }
        }
        e.commit()
        File(ctx.filesDir, "playlists.json").writeText(root.optString("playlists", "[]"))
        root.optString("onlinelib", "").takeIf { it.isNotBlank() }?.let { File(ctx.filesDir, "onlinelib.json").writeText(it) }
        true
    }.getOrDefault(false)
}
