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

## v0.5

- Tapping the media notification (anywhere except its buttons) opens the app straight on the player.
- Setting to hide the gesture badge and keep only the rubber stretch.
- Navigation bar: choose which tabs show and put it at the bottom, top, left or right.
- Gifts: A-B loop, pitch (key shift), skip silence, sleep-timer fade-out, shake to skip, mono switch, "Mix for you" smart list, your stats, share a song, backup / restore of everything.

## v0.6

- **Audio engine**: ReplayGain-style loudness matching (analysed once per song, cached), crossfade (fade dip around song boundaries, not a true overlap), night-mode compressor, balance and channel swap, soft clipper.
- **See the sound**: live spectrum, spectrogram and oscilloscope in the studio; the seek bar can show the song's real waveform.
- **Car mode**: big landscape UI for a head unit (Never / Auto / Always).
- **Video clip maker**: pick a part of a song, get a vertical 9:16 MP4 (720p/1080p) with cover, title, synced lyrics and live spectrum, built with MediaCodec. Also saves a still frame as PNG.
- **Gesture editor and button order**: assign an action to every gesture; drag player buttons into any order with a live preview.
- **Colors from the cover**: the whole app follows the playing song.
- **Widgets**: small, large and square, six themes.
- **Auto lyrics** (opt-in): LRCLIB lookup, with a Network log showing every request.
- **Gifts**: lecture mode (resume position/speed on long audio), sound profile per headphones/speaker, resume when headphones connect, bookmarks, song info, duplicate finder, listening-hours chart, app shortcuts (Shuffle all / Resume / Car mode), clip maker, network log, night mode.

## v0.7

- **Icon styles**: 5 new families — Contour (outline-only), Heavy, Glass (translucent fill + line), Bubble, Ink (sharp, mitered) — 10 in total.
- **Parametric EQ**: 5 peaking bands (frequency / gain / Q) in the built-in DSP, with presets (Clear vocals, Warm, Bright, Cut mud, Quran / speech).
- **Themes**: Sakeenah, Madinah Dawn, Desert Night, Nile, Soft Sand, Soft Slate, Emerald Line, Red Line; 5 new player themes; seek styles Stairs, Zigzag, Tape.
- **Search** ignores Arabic diacritics and alef/yaa/taa-marbuta variants; new sorts (Most played, Longest); friendlier empty state.

## v0.8 — Online (no account, no API key)

New **Online** tab (can be hidden from Appearance → Navigation bar):
- **SoundCloud** search and streaming (HLS AAC) through NewPipeExtractor.
- **YouTube** search through NewPipeExtractor. Playback depends on YouTube accepting the request (it often blocks datacenter IPs and increasingly asks for tokens), so treat it as best-effort; when a stream is refused the app says so.
- **Radio** from the community radio-browser.info directory (empty search = most popular).
- **Link**: paste a YouTube/SoundCloud page or any direct audio / HLS stream.
- Online tracks can be queued (Play next / Add to queue), show their cover in the player and notification, and every request is listed in the Network log. Audio itself streams straight from its source.
- `OnlineLiveTest` (ignored by default) probes the extractors against the live internet.

## v0.9 — one search for everything, and downloads

- **Online tab** is now a single search box: a song, a sheikh, a surah, or a pasted link (YouTube / SoundCloud / archive.org page / any direct audio or HLS link). Results arrive per source as they finish, with a source filter (All, Quran, SoundCloud, YouTube, Archive, Radio), recent searches, per-source retry.
- **Quran** source: every reciter and surah from mp3quran.net (cached, searched locally, Arabic-aware: "الشيخ العفاسي سورة الكهف", "عبد الباسط"). A reciter opens a list of mushafs / surahs with Play all.
- **Archive**: archive.org audio search; an item opens its tracks.
- **Downloads**: pick the quality (every format/bitrate the source offers), optionally put the file in a playlist (existing or new) — or "Download all" for a whole reciter/album. Saved to Music/SUDOPLAY (shows up in the library), 2 at a time, with a foreground notification, progress list and cancel. SoundCloud HLS is stitched into a normal .m4a.
- Unit tests for the Quran search against a copy of the real directory.

## v0.10 — more sources, source filters, smarter results

- **Sources list**: "Sources n/6" opens a checklist (Quran, YouTube, SoundCloud, Audius, Archive, Radio); tap a chip to switch a source on/off, hold a chip to search only that one. Saved between launches. Radio is off by default (noisy for song searches).
- **Audius** added (open network, full-length tracks, no key).
- **Top matches**: the best hit of every source side by side, duplicates removed; each source keeps its own section with "Only <source>".
- **YouTube fallback**: if YouTube refuses a stream (it often blocks anonymous access), the same song is looked up on SoundCloud / Audius (title+artist+length matching) and played from there, with a note saying so.
- **Search suggestions** (opt-in, off by default — it sends the typed text to Google's suggest service; shown in the Network log).
- **Find online** on any library song, **Download** action for the playing online song (gesture/button action), **favorite sheikhs** on the Online page, Quran radio shortcut.

## v0.11 — Sound studio, live themes, tidier settings

- **Sound studio rebuilt**: one live graph (spectrum behind, true combined EQ curve, 10 draggable band dots) and five tabs — EQ, Sound, Space, Dynamics, Output. Parametric EQ now has 10 bands with Bell / Low shelf / High shelf / High-pass / Low-pass / Notch, 0.1 dB gain steps, Q 0.3–20, ±6/12/18 dB view, auto headroom, 16 presets, and a soft-limiter switch. The DSP uses double-precision biquads. Old 5-band settings migrate automatically. The phone's built-in graphic EQ moved to a collapsed card.
- **Live themes**: 14 animated app themes (Aurora, Rain, Fireflies, Neon/Golden Pulse, Lava, Snow, Code rain, Bokeh, Ripples, Embers, Nebula, Sakeenah Lights, Northern Lights) and 14 matching player themes. Setting: Still / Moving / With music (pulses and glows follow the bass).
- **Settings reorganised** into Look & feel · Listening · Your stuff, with a hint under every option: Appearance, Sound & playback, Gestures & feedback, Car & launch, Library & files, Online & privacy, Backup & about.
- Cross-fade between tabs and settings pages, highlighted nav tab, branded settings header.
