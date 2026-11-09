# Mandatory updates (9.11.2026)

**The request:** "when there is an update, it should jump on the screen and force updating".

## What changes
1. **Every new official version is mandatory.** The popup comes up by itself on top of everything. The only exception is the internal player: the popup appears once the player closes.
   - Its only button is "Update now". "Later" and "Skip this version" are removed.
   - Back does not close it.
2. **The download starts by itself** as soon as the update is found. The viewer only confirms the install on Android's screen.
3. **Checks are more frequent:**
   - when SCARY starts (after 30 seconds);
   - on every return to SCARY;
   - every so often while SCARY stays on screen.

   All of these at most once an hour (it was every six hours).
4. **Cancelling on Android's screen** brings the popup straight back with "Update now".

## What does not change (and why)
- **Android always asks for confirmation.** An ordinary app cannot install silently. Only a device-owner app (enterprise management) or a system app can, and that does not apply here.
- **"Look for updates by itself" stays in settings.** It is the TV owner's choice. While it is on, an update found is mandatory. A manual "Check for updates" is mandatory too.
- **"Online information" off:** nothing is checked.
- **Security is the same:**
  - only from the official repository, over HTTPS;
  - SHA-256 and size checked;
  - same package and a newer version;
  - signed with SCARY's key.

## The one exception: when the update gets stuck
SCARY is the TV's home screen. If the popup could never be closed, a stuck update would lock the whole TV. So "Not now" appears **only when something blocks the update**:
- the download failed (no network, GitHub unreachable);
- the file failed verification;
- Android refused the install, or has no screen for the install permission;
- the install permission is missing (the button to open settings is shown, and "Not now" next to it).

"Not now" hides the popup until the next check, an hour later or on the next return to SCARY. Then it comes back.

## Never over another app
- **Automatic checks run only while SCARY is on screen.**
- **An update that finished downloading after SCARY left** (for example to Stremio) waits. It installs when SCARY comes back, so Android's install screen never appears over a film.

## Files
- **core `Updates.kt`:** `CHECK_INTERVAL_MS` set to 1 hour (test updated).
- **`UpdateManager`:**
  - every check ignores "skip" and "later";
  - an update found opens the popup and starts the download;
  - `notNow()` replaces `later()` and `skip()`;
  - a test hook for the failure state.
- **`UpdateDialog`:** "Update now" only; "Try again" and "Not now" only on a failure; Back blocked during the offer and the download.
- **Strings in 5 languages:** the settings text about how often SCARY checks, "Not now", "Try again", "This update is required".
- **docs/PRIVACY.md:** "at most once an hour", and that an update downloads by itself. **Requires legal review**, like the rest of the document.
- **UI test:** the popup has no "Later" or "Skip", Back does not close it, and "Not now" appears only on a failure.

## Risks
- **Downloading on its own uses about 4.3 MB per version.** It happens only when there is a new version.
- **A version with a bug reaches every TV quickly.** That is why releases stay gated on CI with emulator tests, as they are today.
