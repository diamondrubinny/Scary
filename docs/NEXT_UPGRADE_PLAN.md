# Next upgrade: a lighter hand-off to Stremio, better file recognition in the player, general polish (17.11.2026)

**The request:** "A lighter way into Stremio with your add-ons and Real-Debrid; better recognition of which of your files is which film; general polish: speed, stability, design."

**Scope line (unchanged):** SCARY does not search for, fetch or list sources. Stremio's add-ons stay inside Stremio. Everything below works on what the viewer already has: their own Real-Debrid files and the Stremio app.

## What exists today (checked in the code, 17.11.2026)
- **Stremio hand-off:** "Watch" opens `stremio:///detail/movie/<imdb>/<imdb>` (a series: `stremio:///detail/series/<imdb>`), straight on the film's page. The link is not officially documented ("to verify on the device"); when it fails, Stremio just opens.
- **Which file is which film (`FileMatch`):** the file name's title must equal the film's title word for word (any language SCARY knows), the year within one, and episodes (`S01E02`) are never a film.
  - **Series never match.** An episode file's parsed title still holds the episode marker, so a series page never lists the viewer's own episodes.
  - **Files with a title SCARY doesn't hold** (an alternative English title, a Hebrew title, "Alien³" vs "Alien 3") are not recognised.
  - **On the Real-Debrid screen** a file is just a name; SCARY doesn't say which film it is, so "Continue watching" and subtitles depend on the name alone.
- **Polish:** no measured list yet. What I know: weak TVs are slow at the first screen and while trailers load (fixed in 0.5.5 and 0.5.6), and the owner's reports have been about focus and keys. A real list needs the owner's reports.

## What I propose

### A. Hand-off to Stremio
1. **A series opens on the episode,** not the series page, when SCARY knows where the viewer stopped (from "Continue watching" or the last episode played in the internal player). Needs the episode link format of Stremio, **to verify on the device** (it isn't documented).
2. **"Continue in Stremio"** on a film or series SCARY knows the viewer started there (the Stremio account sync already reads this): one press from the home screen, no detour through the details page.
3. **A clear message** when Stremio isn't installed or the link isn't taken ("opened Stremio, search for <title>"), as exists, but with the title ready to read.

### B. Recognising the viewer's files
1. **Series:** episode files are matched to the series (title and the season and episode from the name). The series page lists the viewer's episodes by season, plays one, and the player goes on to the next (`nextInTorrent` exists).
2. **Alternative titles:** a file also matches when its title is any title Cinemeta or TMDB gives for the film (already fetched for the screen), plus the usual spelling variants (roman numerals, "Part 2", "²", leading "The").
3. **On the Real-Debrid screen,** each file shows the film SCARY thinks it is (poster and title), and the viewer can correct it with one press. A correction is kept on the TV only, and fixes "Continue watching" and subtitles for that file.
4. **Never guess:** two films with the same title and different years, or a year too far off, stay unmatched and are offered to the viewer instead.

### C. General polish
Needs the owner's picks (see questions). Candidates, with what I'd measure first:
- **Start and first screen:** time to the first row, on a weak TV, with a startup trace in Diagnostics.
- **Scrolling and focus:** the remaining places where focus jumps or lags (the owner's reports so far).
- **Design:** consistent focus rings and spacing in the newest screens (the track window, the Real-Debrid screen), RTL checks in the 5 languages.
- **Stability:** crash and "not responding" paths found by the existing health log.

## Privacy and security
- **Nothing new leaves the TV** for A and B (2 uses Cinemeta and TMDB titles already fetched for the screen). Corrections (B3) are stored on the TV only, not backed up.
- File names stay on the TV; they are never sent anywhere new.

## Accessibility
- The corrected-film control and the episode list work with arrows and OK, RTL included, and read out in full ("Dark, season 1, episode 2, from your Real-Debrid").

## Files (estimate)
- **core:** `realdebrid/FileMatch.kt` (series, alternative titles, variants), `player/FileNames.kt` (already reads season and episode), `realdebrid/RdMatches.kt` (the viewer's corrections), tests on real file names.
- **app:** the details page (episode list), the Real-Debrid screen (film per file), `Launcher.openInStremio` (episode link), `WatchStore` (corrections), strings in 5 languages, UI tests.

## Stages
1. **B1 and B2:** recognition in core, with tests, then the series page.
2. **B3 and B4:** the Real-Debrid screen.
3. **A:** the hand-off, after verifying Stremio's links on the device.
4. **C:** the polish the owner picks.

## Risks and limits
- **Stremio's links aren't documented** and may differ between versions of its Android TV app; every Stremio link here needs a check on the owner's device.
- **Matching by name will always miss some files** (a release named only by a code). B3's correction is the answer.
- **Polish without measurements is guessing.** Hence the questions.

## Approved answers (17.11.2026)
- **Stremio:** "too many clicks until the film plays".
- **Files:** series episodes on the series page, alternative titles, manual correction on the Real-Debrid screen.
- **Polish:** start and launch speed, focus jumps and scrolling, design and RTL, crashes and freezes.

## Implementation notes (17.11.2026), version 0.5.12
- **B1, series:** `FileMatch.forSeries` reads the season and episode from the name (`S01E02`, `1x02`) and matches the show's title; a year in the name must fall in the show's run. The series page has "Play from Real-Debrid" open an episode picker (seasons as buttons, an episode per row, the biggest file of an episode). Films and series never match each other.
- **B2, titles:** spelling variants are one title: `Alien³` and `Alien 3`, `Part II` and `Part 2`, `&` and `and`, a leading "The". **Real alternative titles ("akas") are not built:** that needs TMDB's alternative-titles endpoint and a key; left for later, with the correction window as the answer meanwhile.
- **B3 and B4, the Real-Debrid screen:** each file shows the film SCARY took it for (the one film its name fits; two that fit are none). Holding OK opens "Which film is this file?": the nearest titles, "Not a film from the library", "Let SCARY guess again". The answer is kept on this TV (`RdMatches`, up to 300 files). A file played from this screen now belongs to its film, so its subtitles are searched by IMDb id and "Continue watching" finds it.
- **A, Stremio:**
  - A series the viewer stopped in the middle of in Stremio opens on that episode (`stremio:///detail/series/<imdb>/<imdb>:<season>:<episode>`, from the library's `video_id`), and falls back to the series page, then the app.
  - **Requires verification on the device:** the episode link is Stremio's own form but not documented.
  - **Not done:** nothing in SCARY can make Stremio start a source by itself; choosing the source stays in Stremio.
- **C, polish:** Diagnostics shows the time from opening to the first rows (the last and the average of the last 5 starts). The other items (focus, scrolling, design, freezes) wait for what the owner's TV shows; the existing freeze watch records them.
- **Tests:** `RealDebridTest` (series, variants, index, corrections), `LinksTest`, `StremioTest`, and `RealDebridFilmsTest` (the correction window, composed on its own).
