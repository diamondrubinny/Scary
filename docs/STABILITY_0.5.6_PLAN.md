# Stability 0.5.6: no running focus, lighter trailers, fuller catalogs (15.11.2026)

**The request:** "leave it as it is, just improve the system more", after the report: "the app freezes and runs by itself between the rows, starts a bit slowly, titles don't tremble, no new films, the catalogs are sparse". Version 0.5.5 already fixed the start, the safe mode that turned effects off, and added diagnostics. This round fixes three causes found in the code.

## 1. Focus running by itself between rows
**Cause:**
- When the main thread is stuck for a moment, the remote's presses (and a held key's repeats) wait in Android's queue.
- When the screen frees up, they all arrive at once, and the focus jumps several rows "by itself".

**Fix:**
- A press that arrives late (more than 300 ms after it was made) is delivered only if it is the first of the backlog.
- The presses that piled up behind it, and every late repeat of a held key, are dropped.
- Only the four arrows are affected; OK, Back and the rest are never dropped.
- The decision is pure logic in `core` (`KeyBacklog`), with tests.

## 2. Trailers that load the TV
**Cause:**
- With trailer autoplay on, every movie the focus rests on creates a new WebView.
- Every move to another movie destroys that WebView and deletes its storage, all on the main thread.
- On a weak TV this is exactly the moment the presses pile up (see 1).

**Fix:**
- One WebView is reused for every trailer: it is emptied (about:blank) between movies and destroyed only when the screen goes away.
- YouTube's storage is still deleted when the player closes for good, as before.

## 3. Sparse online catalogs
**Cause:**
- A daily refresh in which most row pages failed (a weak network, timeouts) was still counted as a success.
- The rows that came out empty stayed missing for a whole day.
- The same applied to the categories' pool.

**Fix:**
- A row whose pages failed keeps yesterday's version.
- A refresh with failed pages is marked "incomplete" and tried again with the usual backoff (1 minute, then 2, 4…) until it completes.
- **Diagnostics** gets a line: the last refresh, with how many pages loaded out of how many (for example "rows 28/30 · categories 70/70").

## 4. The remote's microphone key (owner's report: "acts outside the app, not inside")
**Cause:**
- On many remotes the microphone key goes straight to the TV's assistant. Android hands it to the system before any app, so it opens Google Assistant over SCARY. **Requires verification on the owner's remote:** which key code it sends.
- Remotes that do hand the key to the app (KEYCODE_SEARCH, sometimes KEYCODE_VOICE_ASSIST) only opened the search screen, without listening.

**Fix:**
- On such remotes, the key starts dictation right away:
  - into Ask SCARY when it is open;
  - into search when it is open;
  - otherwise it opens search and starts dictation.
- The text goes in and the search (or the question) runs by itself.
- **Through the TV's own speech recognizer, as with the 🎤 button on screen:** SCARY itself never records audio, and no new permission is needed.
- **When the remote's key goes to the assistant anyway:**
  - the 🎤 button next to the line works;
  - so does the keyboard's own option ("press and hold the microphone to speak"), which holds the remote's key down.

## 5. "Chat" can't be reached either (owner's report)
**Cause (verified in the code of Compose foundation 1.12.1):**
- A text field moves the focus out with the arrows only when the key comes from a device that reports itself as a D-pad.
- Many TV remotes report a keyboard. The field then kept every arrow for its cursor, and the focus was trapped in it.
- So "Chat", the 🎤 button, "Send" and "Change" could not be reached. The same applied to the search line and to the key dialogs.

**Fix:**
- `ScaryTextField`, a single shared field for all five lines:
  - up and down always leave the line;
  - left and right move the cursor, and leave the line at the edge of the text, in the text's direction (Hebrew included);
  - OK opens the keyboard.
- The rule is pure logic in `core` (`TextFieldExit`), with tests.
- **A UI test reproduces the report:** its keys are injected as a keyboard, like those remotes. It goes from the line to "Chat" and opens the chat with the arrows only.

## Privacy and security
- Nothing new leaves the TV. The diagnostics line holds only counts and a time, and stays on the TV.

## Accessibility
- The diagnostics line is plain text, read by screen readers. Strings in 5 languages.

## Checks
- **core:** unit tests for `KeyBacklog` and for the incomplete-refresh merge.
- **app:** lint, compiling the UI tests, and CI (TV and phone emulators).

## Implementation notes (15.11.2026)
- **`KeyBacklog`** (core):
  - Repeats from remotes that stamp every repeat with the press's own time are always delivered, so holding a key keeps scrolling.
  - The release of a dropped press is dropped with it. The release of a press whose repeats were dropped is not.
  - The phone remote injects its keys past this filter (`superDispatchKeyEvent`), so it is never affected.
- **`TrailerPool`:**
  - Belongs to the screen (`ScaryRoot`, through a CompositionLocal) rather than a static object, so the WebView never outlives the screen (lint: StaticFieldLeak).
  - It is destroyed when SCARY leaves the screen (ON_STOP), when its renderer stopped, or when another one is already waiting.
- **Refresh:**
  - When everything failed (offline), the cache stays exactly as it was.
  - A film whose details failed keeps the old details.
  - A retry within the day fetches only the rows and the details of films that have none.
  - The categories refresh with each day's rows, or while they are missing or short.
- **Checks:** `:core:test` (KeyBacklogTest 6, CinemetaRefreshTest 4), `:app:lintDebug` with no findings, `:app:compileDebugAndroidTestKotlin`, and `check-credit.sh`.

## Follow-up (15.11.2026): a hidden keyboard that swallows the arrows
- **What happened:** the CI test that reaches "Chat" with arrows only failed in 2 of 5 runs, both on main, always at the same point. The arrow "up" didn't leave the line, and the cursor didn't move either.
- **The cause:**
  - when the line takes focus, the on-screen keyboard opens and connects to it;
  - when the keyboard is hidden from outside (the test does this with the insets controller), it stays "connected" for a moment;
  - Android hands keys to the keyboard **before** the app, so the arrow never reaches SCARY. The same can happen on TVs with keyboards that behave this way.
- **The fix:** `ScaryTextField` also catches the arrows **before** the keyboard (`onInterceptKeyBeforeSoftKeyboard`), but only when the keyboard isn't showing.
  - Visibility is read from the window's real insets, not from Compose, whose state can always say "hidden" in screens that don't follow insets.
  - While the keyboard is showing, the arrows stay its own, for moving between its keys.
- **Requires checking in CI:** the test reproduces exactly this situation (a keyboard opened and hidden from outside). A pass in several runs in a row will confirm the cause.
- **First CI run of the fix:** "up" did leave the line and the focus reached "Chat", but OK on "Chat" did nothing. The hidden-but-connected keyboard took OK too, even after the focus had left the line, so a fix at the line alone isn't enough.
- **The full fix (`RemoteKeys.kt`, at the root of the screens):**
  - whenever no keyboard is showing, the remote's arrows and OK are taken before the keyboard and handed to the activity as usual, so its KeyBacklog filter still applies;
  - while the keyboard is showing, they stay its own;
  - this probably also explains the original report ("you can't get into the chat"): with Gboard on the TV, OK on "Chat" could get lost the same way. **Requires checking on the TV.**

