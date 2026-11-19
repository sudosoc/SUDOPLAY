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
    /** 0 frozen, 1 animated, 2 animated + reacts to the music */
    val liveMode = IntSetting(prefs, "live_mode", 2)

    // Properties (gestures)
    val doubleTap = BoolSetting(prefs, "g_double_tap", true)
    val swipe = BoolSetting(prefs, "g_swipe", true)
    /** false: swipe right = next, swipe left = previous. true: the opposite. */
    val swipeInvert = BoolSetting(prefs, "g_swipe_invert", false)
    val scrub = BoolSetting(prefs, "g_scrub", true)
    /** Seconds covered by dragging across the full screen width while scrubbing. */
    val scrubSeconds = IntSetting(prefs, "g_scrub_secs", 120)
    /** 0 off, 1 light, 2 medium, 3 strong */
    val hapticLevel = IntSetting(prefs, "g_haptic_level", 2)

    // Player customisation
    val seekStyle = StringSetting(prefs, "seek_style", "classic")
    val iconStyle = StringSetting(prefs, "icon_style", "Rounded")
    val lastSpeedPct = IntSetting(prefs, "last_speed", 125)
    val lastSleepMin = IntSetting(prefs, "last_sleep", 30)
    val btnShuffle = BoolSetting(prefs, "b_shuffle", true)
    val btnPrev = BoolSetting(prefs, "b_prev", true)
    val btnNext = BoolSetting(prefs, "b_next", true)
    val btnRepeat = BoolSetting(prefs, "b_repeat", true)
    val btnFav = BoolSetting(prefs, "b_fav", true)
    val btnPlaylist = BoolSetting(prefs, "b_playlist", true)
    val btnLyrics = BoolSetting(prefs, "b_lyrics", true)
    val btnQueue = BoolSetting(prefs, "b_queue", true)
    val btnSpeed = BoolSetting(prefs, "b_speed", true)
    val btnEq = BoolSetting(prefs, "b_eq", true)
    val btnSleep = BoolSetting(prefs, "b_sleep", true)

    val gestureIcon = BoolSetting(prefs, "g_icon", true)

    // Gesture editor: which action each gesture performs (ids from GestureActions)
    val gDtL = StringSetting(prefs, "ga_dt_l", "playpause")
    val gDtC = StringSetting(prefs, "ga_dt_c", "playpause")
    val gDtR = StringSetting(prefs, "ga_dt_r", "playpause")
    val gSwR = StringSetting(prefs, "ga_sw_r", "next")
    val gSwL = StringSetting(prefs, "ga_sw_l", "prev")
    val gSwU = StringSetting(prefs, "ga_sw_u", "none")
    val gLp = StringSetting(prefs, "ga_lp", "cover")
    val gTwo = StringSetting(prefs, "ga_two", "none")

    // Button order (comma separated ids)
    val orderTop = StringSetting(prefs, "o_top", "lyrics,queue")
    val orderTitle = StringSetting(prefs, "o_title", "fav,add")
    val orderTransport = StringSetting(prefs, "o_transport", "shuffle,prev,play,next,repeat")
    val orderChips = StringSetting(prefs, "o_chips", "speed,eq,sleep")

    // Lecture mode: long recordings remember where you stopped and the speed you used
    val lectureMode = BoolSetting(prefs, "lec_on", true)
    val lectureMinutes = IntSetting(prefs, "lec_min", 10)
    // Smart output
    val deviceEq = BoolSetting(prefs, "dev_eq", true)
    val resumeOnConnect = BoolSetting(prefs, "dev_resume", false)

    val clipCredit = StringSetting(prefs, "clip_credit", "")

    /** dark | light | glass | black | accent | clear */
    val widgetTheme = StringSetting(prefs, "w_theme", "dark")

    /** Tint the whole app with colours taken from the cover of the playing song. */
    val coverColors = BoolSetting(prefs, "a_cover_colors", false)
    val btnAb = BoolSetting(prefs, "b_ab", true)

    // Navigation bar
    val navSongs = BoolSetting(prefs, "n_songs", true)
    val navAlbums = BoolSetting(prefs, "n_albums", true)
    val navBrowse = BoolSetting(prefs, "n_browse", true)
    val navPlaylists = BoolSetting(prefs, "n_playlists", true)
    val navOnline = BoolSetting(prefs, "n_online", true)
    val navDownloads = BoolSetting(prefs, "n_downloads", true)
    /** bottom | top | left | right */
    val navPos = StringSetting(prefs, "n_pos", "bottom")

    // Playback extras
    val skipSilence = BoolSetting(prefs, "p_skip_silence", false)
    val sleepFade = BoolSetting(prefs, "p_sleep_fade", true)
    val shake = BoolSetting(prefs, "p_shake", false)
    val pitchSemi = IntSetting(prefs, "p_pitch", 0)
    /** seconds of fade-out / fade-in around song boundaries; 0 = off */
    val crossfade = IntSetting(prefs, "p_xfade", 0)
    val replayGain = BoolSetting(prefs, "p_rg", true)

    // Library (Extras)
    /** Download missing lyrics from lrclib.net (off by default: it is the only thing that touches the network). */
    val autoLyrics = BoolSetting(prefs, "lib_auto_lyrics", false)
    val minDurationSec = IntSetting(prefs, "lib_min_dur", 20)
    val hiddenFolders = StringSetting(prefs, "lib_hidden", "")

    // Features
    /** never | auto (tablet-sized landscape screens, e.g. a head unit) | always */
    val carStart = StringSetting(prefs, "car_start", "auto")
    /** Start playing as soon as the app opens (handy when a head unit launches it on boot). */
    val autoPlayLaunch = BoolSetting(prefs, "f_autoplay", false)
    val keepScreenOn = BoolSetting(prefs, "f_keep_on", false)
    val lyricsDefault = BoolSetting(prefs, "f_lyrics_default", false)
    val countPlays = BoolSetting(prefs, "f_count_plays", true)

    // Downloads & discovery
    /** Save the cover and (if found) the lyrics together with every downloaded song. */
    val dlExtras = BoolSetting(prefs, "dl_extras", true)
    val dlWifiOnly = BoolSetting(prefs, "dl_wifi_only", false)
    /** Discovery "For you": sends the names of your most-played artists to Deezer to find related music. */
    val discoverPersonal = BoolSetting(prefs, "disc_personal", false)

    /** 0 = original icon, 1..20 = the logo pack (also the launcher icon) */
    val appLogo = IntSetting(prefs, "app_logo", 1)

    // Fonts & colors
    /** font family key (see Fonts.kt); "default" = the system font */
    val fontFamily = StringSetting(prefs, "ui_font", "default")
    /** text size, percent */
    val fontScale = IntSetting(prefs, "ui_font_scale", 100)
    /** "" = the theme's own text color, otherwise #RRGGBB */
    val textColor = StringSetting(prefs, "ui_text_color", "")
    /** "" = the theme's own accent, otherwise #RRGGBB */
    val accentColor = StringSetting(prefs, "ui_accent_color", "")
}
