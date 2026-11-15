# Internal player: audio and subtitle tracks (15.11.2026)

**Approved (15.11.2026):**
- Everything, including external subtitles from the torrent with the Hebrew encoding fix (stage D).
- **Default language: English.** Interpreted as English audio when the file has it, otherwise the file's default; English subtitles when they exist, otherwise off. Changeable in Settings → Player.
- **DTS/TrueHD in software (FFmpeg):** a separate plan after this upgrade, with size, licensing and build costs, before any code.

**The request:** "upgrade the internal player with options to control soundtrack languages in mkv files etc."

## What exists today (checked in the code)
- **Audio:** an "Audio" button that cycles through the tracks one press at a time. It shows only the language or title, and appears only when there are 2 tracks or more.
- **Subtitles:** a similar button (off → track 1 → track 2…), plus size and delay.
- **Tracks the TV can't decode** (DTS on many TVs, TrueHD) disappear from the list without explanation. When they are the only audio, the film plays silent with no message.
- **Picture subtitles** (PGS/VobSub, common in Blu-ray MKVs) don't show at all: the player draws only text subtitles.
- **Nothing is remembered between files:** every film starts with the file's own default track.

## What I propose

### A. A track menu instead of cycling
1. **An "Audio and subtitles" window** with two lists, chosen with the arrows and OK. Each track is shown with:
   - its language in the viewer's language ("English", "עברית"), and its title from the file ("Director's commentary");
   - **the format:** Dolby Digital (AC3), Dolby Digital Plus, DTS, TrueHD, AAC, Opus…, and **channels** (stereo / 5.1 / 7.1);
   - **marks:** default, commentary, audio description, forced, hearing impaired (SDH).
2. **A track the TV can't play** appears grayed out, with the reason ("this TV can't decode DTS"). It doesn't just vanish.
3. **A file with only unsupported audio:** a clear message instead of silence.
4. **The current track** is marked, and is also shown on the info line ("English · 5.1 · Dolby Digital Plus").

### B. Subtitles that actually show
5. **Picture subtitles (PGS/VobSub)** are drawn too, with the same position and the same delay as text subtitles.
6. **Forced subtitles** (translation of foreign-language passages only) come on by themselves when subtitles are off.

### C. Preferences that are remembered
7. **In Settings → Player** (and in the window itself):
   - **preferred audio language:** "the film's original" (the default), Hebrew, English…;
   - **preferred subtitle language:** off / Hebrew / English…
   - They apply to every file, with a fallback to the next one when the language is missing.
8. **Per file:** the tracks chosen for a file come back when you return to it (with "Continue watching"). They are saved by language and title, on the TV only.

### D. Separate subtitle files from the same torrent (optional, see questions)
9. **`.srt`/`.ass`/`.vtt` files next to the film** in the Real-Debrid torrent appear in the subtitle list ("Hebrew (file)").
10. **Encoding detection for Hebrew:** many Hebrew SRT files are in Windows-1255 and come out as gibberish. SCARY detects the encoding (UTF-8 / Windows-1255 / ISO-8859-8), converts to UTF-8, and only then hands the file to the player.
   - Logic in `core`, with tests.
   - A size limit of 2 MB per file.
   - Only files from the viewer's own Real-Debrid account. **No subtitle search on outside sites.**

## What is not included (and why)
- **Decoding DTS/TrueHD in software:** this needs Media3's FFmpeg extension.
  - It isn't published as a ready library; you have to build it yourself in C.
  - Its licensing (LGPL/GPL, depending on the decoders) **requires legal review**.
  - It enlarges the APK by several MB.
  - What is possible without it: passthrough to an amplifier or soundbar that supports these formats. Media3 does this by itself when the TV reports such support.
  - I can explore this as a separate stage.
- **Audio delay (lip-sync):** Media3 has no simple built-in way. **Requires verification against the Media3 documentation.**

## Privacy and security
- **Language preferences and per-file choices** are kept on the TV only (WatchStore), not backed up and not sent anywhere.
- **External subtitles:**
  - downloaded through the existing link from Real-Debrid;
  - checked for size, type and encoding;
  - parsed as text only, never run.
  - The link and token are not saved.

## Accessibility
- The track window works with the arrows and OK, RTL included, and every track has a full description for the screen reader ("English, 5.1, Dolby Digital Plus, default").
- A grayed-out track is read out together with the reason.

## Files (estimate)
- **core:**
  - `player/TrackInfo.kt`: format and channel names, marks, sorting by preference, the hostile-input rules;
  - `player/SubtitleCharset.kt`: encoding detection (stage D);
  - tests.
- **app:**
  - `ui/player/TrackPicker.kt`: the window;
  - PlayerScreen: drawing picture subtitles, applying preferences (`setPreferredAudioLanguages`, `setPreferredTextLanguages`), the info line;
  - WatchStore: preferences and choices per file;
  - Settings → Player;
  - RealDebridRepository: external subtitles (stage D).
- **Strings in 5 languages; UI test of the window** (with a test file that has 2 audio tracks and a subtitle track); docs (PRIVACY).

## Stages
1. **core** (track info, preferences) + **the track window** + grayed-out tracks + the message for unsupported audio.
2. **Picture subtitles and forced subtitles.**
3. **Saved preferences and per-file choices,** plus Settings → Player.
4. **(If approved)** external subtitles from the torrent + Hebrew encoding detection.
5. **Finish:** strings, UI tests, docs, CI, version 0.5.7.

## Risks
- **Format support differs between TVs:** the grayed-out list will show what this TV actually can't play.
- **Picture subtitles in large MKV files** load as the file plays; on a slow connection they may come late.
- **External subtitle files of unusual size or encoding** are rejected with a message, never shown as gibberish.

## Implementation notes (15.11.2026), version 0.5.7
- **core:**
  - `player/TrackInfo.kt`: the track, format and channel names, and language normalization ("eng", "heb", "iw", "fre" → "en", "he", "fr"). Some Java and Android versions return the old code "iw" from their language table; it is normalized too.
  - `player/TrackChoice.kt`: audio and subtitle choice, the "off with forced only" rule, and storage of per-file choices (up to 60 files).
  - `player/SubtitleFiles.kt`: matching subtitle files to a video (by name, by episode, or a film alone in its torrent), and encoding detection (BOM, strict UTF-8, Windows-1255/1256/1251/… by language or by letter frequency).
  - Tests: `PlayerTracksTest` (12).
- **Track key:** a file's own track is known by language, title, format and channels, so a choice comes back after a reload or another day. A subtitle file is known by its place in the torrent.
- **Subtitles parsed during extraction** (Media3 1.4+) arrive as `application/x-media3-cues`, with their original format in `codecs`. That is what is shown ("SRT", "PGS").
- **Subtitle files** get the id `scary-sub-<index>` in the player, so their track is recognized once loaded. A file that loaded but that the player didn't accept is reported once, not fetched again in a loop.
- **The converted (HLS) stream:** after switching to it, the language preferences choose again among its tracks.
- **"For every film":** in the track window itself and on the Real-Debrid screen, rather than in the main Settings screen.
- **UI test** (`PlayerTest.theTrackWindowSwitchesAudioAndSubtitlesAndRemembersThem`): a 6-second MKV with English stereo and Hebrew 5.1 audio and two subtitle tracks, plus a Hebrew subtitle file in Windows-1255. The test checks:
  - the automatic choice (English);
  - switching to the Hebrew dub;
  - the subtitle file being loaded and shown in Hebrew;
  - "Off";
  - Back closing the window and not the film;
  - the choice being saved for the file.
