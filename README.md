# SUDOPLAY

Android music player — Kotlin, Jetpack Compose, Media3 (ExoPlayer).

## Done (v0.1)
- Local library from MediaStore (songs / albums / artists), search, sort, shuffle
- Background playback + notification/lock-screen controls + artwork, audio focus, headset unplug pause
- Queue (play next, add to queue, jump, remove), shuffle, repeat, speed 0.5x–2x
- Favorites + playlists (JSON store), sleep timer (runs inside the service)
- Theme engine: 17 themes across Solid / Glass / Artwork-blur styles + Material You

## Done (v0.2)
- Equalizer (device bands, 10 presets) + bass boost, surround, loudness gain — runs in the service, persists
- Lyrics: embedded MP3 (ID3 USLT) or `.lrc` next to the song (needs "all files access"), synced highlight, tap a line to seek
- Browse tab: artists + folders
- Smart lists driven by play stats: recently played, most played, recently added, never played
- Gapless playback comes from ExoPlayer for files with proper gapless metadata

## Roadmap
- v0.2.x: crossfade, ReplayGain, tag editor, lyrics for FLAC/M4A
- v0.3: streaming without API keys — NewPipeExtractor (YouTube/SoundCloud), internet radio (radio-browser), direct URLs
- v0.4: Listen Together over LAN, smart playlists, car mode, stats, widget, Android Auto
- More themes: just add an entry in `ui/Theme.kt`

## Build
`./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
