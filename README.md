# SUDOPLAY

Android music player — Kotlin, Jetpack Compose, Media3 (ExoPlayer).

## Done (v0.1)
- Local library from MediaStore (songs / albums / artists), search, sort, shuffle
- Background playback + notification/lock-screen controls + artwork, audio focus, headset unplug pause
- Queue (play next, add to queue, jump, remove), shuffle, repeat, speed 0.5x–2x
- Favorites + playlists (JSON store), sleep timer (runs inside the service)
- Theme engine: 17 themes across Solid / Glass / Artwork-blur styles + Material You

## Roadmap
- v0.2: equalizer + bass boost, gapless/crossfade, ReplayGain, synced lyrics (.lrc), tag editor, folders view
- v0.3: streaming without API keys — NewPipeExtractor (YouTube/SoundCloud), internet radio (radio-browser), direct URLs
- v0.4: Listen Together over LAN, smart playlists, car mode, stats, widget, Android Auto
- More themes: just add an entry in `ui/Theme.kt`

## Build
`./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
