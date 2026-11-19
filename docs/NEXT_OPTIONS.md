# What else can be upgraded: options to choose from (20.11.2026)

**The request:** "Check what else can be upgraded, and add options."

Nothing here is built yet. Each item was checked against the code as it is in 0.5.17, so "missing" means missing.

## Browsing and finding films
| # | Option | What it adds | Today | Size |
|---|---|---|---|---|
| A | **Choose your home rows** | A screen in Settings: turn each kind of row on or off (apps, moods, "How much time", Ask SCARY, the TMDB rows, the web rows, each library category) | Only the seasonal rows and the Ask SCARY row have a switch | Medium |
| B | **"More from this director"** on a film's page | A second row under "More like this": the director's other films, from the library and the offline index (3,745 films with their director), no network | Only "More like this" | Small |
| C | **Search filters** | Narrow results by decade, fear level, films or series, and "not watched yet" | Text and voice only | Medium |
| D | **"Remind me"** on Coming soon | Keep a film that is not out yet; when its date comes, it shows in a "Now out" row | Coming soon is a row to look at (TMDB key) | Small to medium |
| H | **The whole franchise, in order** | On a film's page, with a TMDB key: "The series" (Halloween 1 to 13 in order), and one button to put them all in the bucket list | None | Medium. TMDB's collection endpoint is documented; what it returns was not seen (no key here) |

## Player and settings
| # | Option | What it adds | Today | Size |
|---|---|---|---|---|
| E | **How subtitles look** | White or yellow, with or without a dark background, higher or lower on the screen; and the delay remembered for each file | Size only; the delay is forgotten when the film closes | Small |
| F | **Backup and restore** | My list, watched, loved, the bucket list and the settings saved to a file through the phone remote page, and read back after a reinstall or on another TV | Everything is lost on a reinstall unless a TMDB or Stremio account holds the lists | Medium. No keys or tokens in the file |
| G | **A switch for the hidden films** | Settings: show or hide Arab and Indian films (hidden for everyone today) | A fixed rule | Small |

## Already written down as open (from earlier plans)
- DTS/TrueHD sound in software (needs a legal review of FFmpeg and the patents first): `docs/FFMPEG_AUDIO_PLAN.md`.
- A film's other titles from TMDB, for recognising files: `docs/NEXT_UPGRADE_PLAN.md`.
- Removing an item from Stremio's own Continue watching (an undocumented interface): `docs/CONTINUE_MENU_PLAN.md`.
- New screenshots for the website: `docs/WEBSITE_UPDATE.md`.
- UI tests in CI that fail now and then on timing.

## How I would order them, if asked
1. **B, E, G**: small, no new network use, visible at once.
2. **A**: the one that changes daily use most.
3. **F**: protects everything the viewer built up.
4. **C, D, H**: nice to have; D and H need the TMDB key.

## Privacy and accessibility (for whichever is chosen)
- A, B, C, E, G: nothing leaves the TV.
- D, H: requests to TMDB with the viewer's own key, about films (ids, a collection id); nothing about the viewer.
- F: a file the viewer makes and keeps; it holds lists of film ids and settings, never keys, tokens or the PIN.
- Every new screen and row: arrows and OK, RTL, screen reader names, 5 languages.

## Chosen (20.11.2026): all eight
Built in stages, the small ones first.

## Stage 1 (version 0.5.18): B, E, G
- **B, "More from this director":**
  - `Similar.byDirector` (core): the director's other films among everything SCARY holds (the library, the rows, the offline index), the best rated first, then the newest; those already watched last; up to 30.
  - A second row on the film's page, under "More like this". Not shown for a locked film or when the director is not known.
- **E, how subtitles look** (in "Audio and subtitles", for every film):
  - colour: white or yellow;
  - dark background: on or off (off draws a black edge around the letters instead);
  - place: at the bottom or higher.
  - **The delay is kept per file** (`TrackMemory.subtitleDelayMs`), so a film whose subtitles run early still does next time.
  - Picture subtitles (Blu-ray, DVD) are drawn by the film itself and are not affected.
- **G, the switch for hidden films** (Settings → Films and accounts → "Arab and Indian films: hidden / shown"):
  - Hidden is how SCARY comes. Turning it to hidden takes effect at once, offline too.
  - Shown needs the films read again, which starts right away (rows and categories).
  - **Limits:** the 117 films taken out of the offline index stay out of it (search on the web still finds them); "Asian horror" still does not count India as Asia.
- **Tests:** `SimilarTest`, `PlayerTracksTest`, `OriginFilterTest`; UI: the director row on a film's page, the colour button, the switch's row.
- **Not checked on a real TV.**

## Stage 2, built: choose the home screen's rows (19.11.2026), version 0.5.19
- **Settings → Rows and taste → Home screen rows** opens a list of every row of the home screen, each "Shown" or "Hidden"; OK on a row turns it the other way. The list holds the fixed rows (Continue watching, Continue in Stremio, Recently opened, My list, the suggestions from what the viewer loved and watched, Moods, "How much time do you have?") and every row the catalog holds now, films' and series'.
- **A row turned off is not built at all:** its films are free for the other rows (the "no repeats" rule gives them to the next row that fits), and the home screen is shorter, which also means less to draw on a weak TV.
- **"My apps" and the hero are not in the list:** a launcher without its apps row leaves the viewer with no way out to other apps.
- **"Show all the rows"** appears once a row is off, and also clears rows that were turned off and no longer exist in the catalog.
- **Stored on this TV only** (`hidden_home_rows`, at most 300 keys, each checked against a strict pattern before it is kept). Nothing is sent anywhere. Not behind the PIN: it hides rows, it does not unlock anything; family night and the content lock apply as before, whatever is shown.
- **Accessibility:** each row reads "<row name>: Shown" or "…: Hidden"; the state is words, never colour alone; arrows and OK only; RTL follows the app.
- **Files:** core `catalog/HomeRowChoices.kt` (+ `RowsTest`); app `SettingsRepository` (`hiddenRows`), `HomeViewModel` (`buildState` gating, `rowChoices`, `toggleHomeRow`), `ui/settings/HomeRowsScreen.kt`, `SettingsScreen` (the row), `ScaryRoot` (the screen, focus back on the row), strings in 5 languages, `ScaryUiTest.aHomeRowIsTurnedOffAndBackOn`.
- **Limit:** rows are turned off by their kind or their collection id. A collection whose id changes in a later catalog shows again (the old key is simply never matched).

## Stage 3: backup and restore (19.11.2026), version 0.5.20

### Plan
- **What the viewer gets:** on the phone remote's page, "Save a backup on this phone" and "Restore from a backup file". After a reinstall, or on a second TV, the lists come back in two presses.
- **What is in the file:** My list, watched (and the day each was marked), "Loved it", "Not for me", the bucket list, the home rows turned off, and six plain switches (seasonal rows, mark watched automatically, hide watched, hide Arab and Indian films, trailer autoplay, trailer sound). Film ids and yes/no values only.
- **What is never in the file:** the TMDB key, AI keys, the TMDB, Stremio and Real-Debrid accounts, the parental PIN and everything of the lock (family night, the fear level), the language, pinned and hidden apps (they belong to one TV), the internal player's history (it holds the viewer's own file names), and whether online data and AI are on (turning those on is a choice made on the TV).
- **Restoring merges.** Everything the TV holds stays, in its place; the file adds after it. Where the two disagree about a film (loved here, "not for me" in the file) the TV's answer stays. The home rows turned off and the six switches are settings, so they are taken from the file. The same file restored twice changes nothing.

### Threat model
| What | Risk | Control |
|---|---|---|
| The backup crossing the home network | Another device on the Wi-Fi reads what the viewer watched | The answer is encrypted and signed with the pairing's secret (its own keystream and signature, bound to the command's counter); the secret never crosses the network |
| A forged or replayed "give me the backup" | A device without the QR code gets the lists | The command is signed and its counter must rise, as every command; 20 forged commands turn the remote off |
| A crafted file sent to restore | Injection, a huge file, a setting that unlocks something | Signed by the paired phone only; one address reads a long body and takes nothing but a restore; 120,000 characters at most; a strict format and version; every id checked against a pattern; every list capped; only the six named switches; unknown fields ignored; nothing in the file is ever run or shown as markup |
| The file on the phone | Someone else reads it | It holds lists of film ids and six switches, no key, account or PIN; the page says so and it is the viewer's to keep |
| Restore as a way around the parental lock | A child restores a file to change the lock | The lock, the PIN, family night and the fear level are not in the format and are never written by a restore |

### As built
- **core:** `backup/Backup.kt` (the format: `encode`, `decode`, `sanitize`, `merge`, `added`), `remote/RemoteProtocol.kt` (commands `backup` and `restore`; `Session.sealAnswer`, `openAnswer`; a large body only for `restore`). Tests: `BackupTest`, `RemoteProtocolTest` (values computed apart, with Python's hmac).
- **app:** `data/BackupRepository.kt`, `SettingsRepository.backupSnapshot` and `applyBackup` (one write), `remote/PhoneRemoteServer.kt` (`POST /api/big`, the sealed answer), `assets/remote` (the page's Backup section in five languages; `openAnswer` in `remote.js`, checked by `tools/remote/check-remote-js.cjs` against the same values), the TV's messages, `ScaryUiTest.theBackupGoesToThePairedPhoneAndAFileIsMergedBack`.
- **After a restore:** films found online that this TV has not kept get their details from Cinemeta by IMDb id (one at a time, only with online data on), so they show in My list and the bucket list.
- **Works whatever app is in front:** the backup needs no screen.

### Limits, to verify on a real phone
- **Saving the file uses the browser's download of a page-made file** (`a download` with a blob). It works in Chrome on Android and Safari on iOS 13 and later by their documentation; **requires a check on the owner's phone**, since the page is served over plain HTTP on the home network.
- **The file is not encrypted on the phone.** It holds no secret, but it does show what was watched. A password on the file was left out: it would need a strong key-derivation function in the page's own JavaScript (no Web Crypto on plain HTTP).
- **Privacy law:** the file is the viewer's own data moved by the viewer between their own devices; SCARY's maker never receives it. **Needs legal review** before it is described as a data-portability feature.

## Stage 4 (19.11.2026), version 0.5.21: C, D, H

### C. Search filters
- **Under the search field:** four buttons. OK on one moves it to its next value and back to "any": films and series / films only / series only; any year / each decade from this one back to the 1970s / before 1970; any fear level / 1–2 / 3 / 4–5; watched too / not watched yet. "Clear filters" shows once one is set.
- **With no text, a filter alone picks the films** (up to 120, the best rated first): "the 1980s, fear 4–5, not watched yet" is a way to browse.
- **A filter narrows before the limit is taken,** so matches are never hidden behind films it left out. The web's results pass the same filter.
- **Limit:** the fear level is known only for the hand-picked library. A film from the web or the offline index has none, so it fits no fear band (the empty state says so).
- **Accessibility:** each button says its value in words; number ranges are kept left-to-right in Hebrew and Arabic; the search key on the keyboard goes straight to the results, past the filters.
- **Files:** core `catalog/SearchFilter.kt`, `Search.Index.query(accept)` and `browse`; app `SearchScreen` (`FilterRow`), `HomeViewModel.searchFilter`. Tests: `SearchTest`, `ScaryUiTest.searchFiltersNarrowTheResultsAndClear`.
- **Privacy:** nothing leaves the TV; the filter is not stored.

### D. "Remind me" on Coming soon
- **On the page of a film that is not out yet:** "Remind me". When its day comes, the film shows in a "Now out" row at the top of the home screen for 45 days, or until it is watched or the reminder is taken off (the same button, on the film's page).
- **A film whose release moved** to a later day waits for the new day (when the catalog still holds it with that date).
- **No notification is scheduled** and nothing runs in the background: the row is worked out when the home screen is built. SCARY is the home screen, so that is where a reminder is seen.
- **Up to 40 reminders,** kept on the TV only (`release_reminders`: film id and day). The film's details are kept with the saved films, so it can be shown after it leaves the coming-soon row. Not in the backup file yet.
- **"Now out" is in Settings → Home screen rows,** like every row.
- **Files:** core `catalog/Reminders.kt`; `Cinemeta.fromMovie` keeps the release date; app `SettingsRepository.toggleReminder`, `HomeViewModel.toggleReminder` and the row in `buildState`, `HomeRow.NowOut`, the button on `DetailsScreen`. Tests: `RemindersTest`, `ScaryUiTest.aRemindedFilmShowsInNowOutAndLeavesWhenTheReminderIsRemoved`.
- **Needs a TMDB key** to see films that are not out yet (the coming-soon row is TMDB's).

### H. The whole series, in order
- **On a film's page, with the viewer's TMDB key:** a row "The whole series: <name>" with every film of the series in the order they came out (announced films without a date last), and one button: "Add them all to the bucket list" (the films that are out, in order, until the list's 30 are full; films the parental lock covers are left out).
- **What is asked of TMDB,** all in the official API description (`/3/find/{external_id}`, `/3/movie/{movie_id}` for `belongs_to_collection`, `/3/collection/{collection_id}`, `/3/movie/{movie_id}/external_ids`): the film's id, the series' id, and the IMDb id of each of its films this TV has not asked about before (kept on the TV afterwards). Nothing about the viewer. Kept in memory for the session.
- **Not on family night** (only the library shows then), not for series, and not without a key or with online data off.
- **Files:** core `Tmdb.parseCollectionId`, `collectionUrl`, `parseCollection`; app `TmdbRepository.franchise`, `HomeViewModel.loadFranchise` and `addFranchiseToBucket`, the row on `DetailsScreen`. Tests: `TmdbTest` (answers written from the API description).
- **Requires verification on the TV with a key:** the answers were read from TMDB's API description, not from a live request (there is no key in this workspace). The description names a part's title `name`; live answers are known to use `title`. Both are read.
