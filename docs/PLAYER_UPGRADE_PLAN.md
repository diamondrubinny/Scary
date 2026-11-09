# Upgrading the internal player (9.11.2026)

**Approved:** the whole list; "Continue watching" in every place (after a PIN when the lock is active); up to 20 bookmarks per file.

**Implemented (9.11.2026), version 0.5.3.** Deviations from the plan:
- **Subtitle sync:** delay only (subtitles later, from +0.5 to +5 seconds). Media3 does not hand over a subtitle before its time, so showing subtitles *earlier* is not possible in this player. **Requires verification against the Media3 documentation** for future versions.
- **Night mode:** Android's `DynamicsProcessing` (Android 9 and later). It doesn't apply to audio sent untouched to an amplifier (passthrough). Not tested on a real TV.
- **A file removed from the account:** choosing its card removes it from "Continue watching" with a message, instead of asking.
- **The progress bar always runs left to right,** in Hebrew too, so that left and right on the remote match backwards and forwards.

**The request:** "upgrade the internal player with more capabilities, including an option to mark a spot where you stop, and then come back later to watch".

## What the player has today
- Play/pause.
- Jumps of 10 and 30 seconds.
- Audio and subtitle tracks from the file.
- Subtitles drawn by SCARY.
- Automatic resume from where you stopped (saved every 15 seconds, on the TV only).
- "Watched" set by itself at the end.
- Screen kept on, and the sleep timer.

## What I propose to add

### A. Bookmarks (the core of the request)
1. **"Mark a spot"** button in the player. It saves the current spot with its time ("Bookmark: 1:12:40"). Several bookmarks per file are allowed.
2. **Holding OK while the controls are hidden** marks too (quick, without opening the menu). A short message confirms: "Spot marked at 1:12:40".
3. **"Bookmarks" list in the player:** jump to a bookmark or delete it.
4. **Coming back later:**
   - **"Continue watching" row on the home screen:** files that were stopped in the middle or have bookmarks, ordered by last watched. Each card shows its progress (a bar) and the bookmark time. Choosing one opens the player at the spot.
   - **"Continue watching" section on the Real-Debrid screen** too.
   - **On a film page:** "Continue from 1:12:40" button next to "Play from Real-Debrid".
5. **Open from the start:** when there is a saved spot, choosing a file asks "Continue from 1:12:40" or "From the start".

### B. More player capabilities
6. **Progress bar you can move:** with the bar focused, left/right moves in steps that grow while the button is held (10 seconds, then 30, then a minute), and OK jumps there.
7. **Playback speed:** 0.75, 1, 1.25, 1.5.
8. **Subtitles:**
   - size (small / medium / large);
   - sync: earlier or later in steps of 0.5 seconds, for subtitles that are out of step.
9. **Picture:** fit / fill the screen / stretch (for films with black bars).
10. **Info line:** the file name, time left, and "ends at 23:40" by the TV's clock.
11. **Next file:** in a torrent with several files (a series, or a film in parts), "Next file" at the end, with 10 seconds to cancel.
12. **Night mode for sound:** quiet explosions, louder dialogue (dynamic range compression). **Requires verification against the Media3 documentation** for TV support. If it isn't possible, it is left out.

## Privacy and security
- **Bookmarks and progress are kept on the TV only:**
  - as the file's id in Real-Debrid, the time and the name;
  - **never** the download link or the token (the link is requested again on every play, as today);
  - not backed up, and not sent anywhere;
  - deleted on "Disconnect Real-Debrid", or with a new "Clear watch history" button.
- **Parental lock:**
  - the "Continue watching" row is shown only after entering the PIN while the lock is active (personal files have no fear rating), and not at all on "Family night";
  - choosing a card asks for the PIN, as today.
- **If a file was removed from the Real-Debrid account,** the card says so and offers to delete the bookmark.

## Accessibility
- Every button gets a name for the screen reader.
- The bookmark message is announced.
- The progress bar announces its value ("1:12:40 of 2:05:00").
- Everything works with the arrows and OK only, RTL included.

## Files (estimate)
- **core:**
  - `player/Bookmarks.kt`: model, sorting, limits (up to 20 bookmarks per file, up to 50 files), hostile parsing;
  - `player/SubtitleSync.kt`;
  - tests.
- **data:** `PlayerPositions` grows into a "watch store" (positions, bookmarks, last watched), with a migration from the existing positions.
- **ui:**
  - PlayerScreen: bar, menus, bookmark message;
  - home row;
  - Real-Debrid section;
  - film page button;
  - "continue or start over" window.
- **Strings in 5 languages.**
- **UI tests:**
  - mark a bookmark, then close;
  - the "Continue watching" row appears;
  - choosing a card opens at the spot;
  - deleting a bookmark.
- **Docs:** PRIVACY ("watch history on the TV only").

## Stages
1. **core + watch store + bookmarks in the player** (mark, list, jump, delete) + "continue or start over".
2. **Coming back:** the home row, the Real-Debrid section, the film page button.
3. **Capabilities:** progress bar, speed, subtitle size and sync, picture, info line, next file (and night mode if possible).
4. **Finish:** strings, UI tests, docs, CI, release (0.5.3).

## Risks
- **Some TVs don't support playback speed** for certain files (passthrough audio). The button then shows that it isn't available.
- **Subtitle sync** works only on subtitles that SCARY draws (it does today), not on subtitles burned into the picture.
- **Night mode:** see point 12; it requires verification.
