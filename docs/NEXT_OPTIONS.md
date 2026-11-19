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
