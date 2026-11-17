package com.sudosoc.sudoplay.data

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class BoolSetting(private val prefs: SharedPreferences, private val key: String, default: Boolean) {
    var value by mutableStateOf(prefs.getBoolean(key, default)); private set
    fun set(v: Boolean) { value = v; prefs.edit().putBoolean(key, v).apply() }
}

class IntSetting(private val prefs: SharedPreferences, private val key: String, default: Int) {
    var value by mutableStateOf(prefs.getInt(key, default)); private set
    fun set(v: Int) { value = v; prefs.edit().putInt(key, v).apply() }
}

class StringSetting(private val prefs: SharedPreferences, private val key: String, default: String) {
    var value by mutableStateOf(prefs.getString(key, default) ?: default); private set
    fun set(v: String) { value = v; prefs.edit().putString(key, v).apply() }
}

/** Every user-facing option, persisted in SharedPreferences. */
class AppSettings(prefs: SharedPreferences) {
    // Appearance
    val theme = StringSetting(prefs, "theme", "aurora")
    val playerTheme = StringSetting(prefs, "player_theme", "classic")

    // Properties (gestures)
    val doubleTap = BoolSetting(prefs, "g_double_tap", true)
    val swipe = BoolSetting(prefs, "g_swipe", true)
    /** false: swipe right = next, swipe left = previous. true: the opposite. */
    val swipeInvert = BoolSetting(prefs, "g_swipe_invert", false)
    val scrub = BoolSetting(prefs, "g_scrub", true)
    /** Seconds covered by dragging across the full screen width while scrubbing. */
    val scrubSeconds = IntSetting(prefs, "g_scrub_secs", 120)
    val haptics = BoolSetting(prefs, "g_haptics", true)

    // Features
    val keepScreenOn = BoolSetting(prefs, "f_keep_on", false)
    val lyricsDefault = BoolSetting(prefs, "f_lyrics_default", false)
    val countPlays = BoolSetting(prefs, "f_count_plays", true)
}
