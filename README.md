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

## Done (v0.3)
- Settings tab (Appearance / Extras / Properties / Features) replaces the Style tab
- 68 app themes in 6 styles (Glass, Gradient, Artwork blur, Solid, Soft, Outline) + 17 player-page themes (vinyl spin, halo ring, full bleed, polaroid, card stack, neon glow, animated aurora, ...)
- Player gestures: double-tap play/pause, swipe next/previous, long-press + drag to seek, long-press the cover to change it
- Cover editor: 18 built-in covers or any photo from the device, pan/zoom to position, per song or whole album
- Sleep timer shows a live countdown on its button, +5 min while running

## Roadmap
- v0.3.x: crossfade, ReplayGain, tag editor, lyrics for FLAC/M4A
- v0.3: streaming without API keys — NewPipeExtractor (YouTube/SoundCloud), internet radio (radio-browser), direct URLs
- v0.4: Listen Together over LAN, smart playlists, car mode, stats, widget, Android Auto
- More themes: just add an entry in `ui/Theme.kt`

## Build
`./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`

## v0.4

- **Sound studio**: tap the EQ chip to switch the whole sound engine on/off, long-press it to open the studio. Live spectrum of what is playing with a draggable EQ curve, 10 scenes + your own saved scenes, preamp / bass / loudness, virtual surround (auto / headphones / speakers), stereo width, reverb (room / damping / mix) and echo (delay / feedback / mix). Width, reverb and echo run in a built-in DSP processor, so they sound the same on every phone.
- **Player page**: drag down to close (follows the finger), drag the mini player up to open it, swipe the mini player sideways for next / previous. Rubber-band stretch while swiping or scrubbing, themed gesture badge.
- **Speed & Sleep chips**: tap = on/off, hold and slide = pick a value from a wide range, hold and release = menu. Sleep has "End of song".
- **Appearance**: 37 player themes shown as live previews of the real page, 13 seek-bar styles, 5 icon families, choose which player buttons are visible. Popup menus share one themed look.
- **Real haptics** through the system Vibrator with adjustable strength and a test button.
- **Extras**: minimum track length, hidden folders, rescan, .lrc access, covers reset. Library refreshes by itself; the queue and position are restored on launch.
