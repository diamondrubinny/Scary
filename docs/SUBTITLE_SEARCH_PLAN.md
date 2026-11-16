# Internal player: subtitles found online by the film's name (16.11.2026)

**The request:** "Upgrade the internal player so it pulls subtitles by the movie's name."

## What exists today
- The player uses the subtitles inside the file, and subtitle files in the viewer's **own** Real-Debrid torrent (0.5.7).
- The plan for 0.5.7 said, on purpose: *no subtitle search on outside sites*. This request lifts that limit, so it needs a source, a privacy answer and a legal note, below.

## What was checked (16.11.2026, live)
| | Stremio's OpenSubtitles add-on | OpenSubtitles' own API |
|---|---|---|
| Address | `opensubtitles-v3.strem.io/subtitles/movie/<imdbId>.json` | `api.opensubtitles.com/api/v1` |
| Account or key | **None** | **Own API key, plus a username and password to download** (the search answered without a key once, then refused; login: "You cannot consume this service") |
| Answer | A list: language, release name, release format, frame rate, original encoding, and a **ready link** | Search by IMDb id, then a separate download request that needs the login |
| What a download is | `application/x-subrip; charset=utf-8`: **already UTF-8** (Windows-1255 Hebrew checked, 18,059 Hebrew letters, no gibberish) | A link from the download request |
| Coverage seen | *The Conjuring*: 98 subtitles (9 English, 2 Hebrew, 6 Arabic); *Hereditary*: 98 (17 English, 1 Hebrew); *The Shining*: 35 (5 English, **0 Hebrew**); *Alien*: 24 (**0 English**, 0 Hebrew) | Larger (77 English for *The Shining* in the one answer that came back) |
| Status | The add-on protocol is public, but this server is Stremio's, made for Stremio's apps: **using it from SCARY is unofficial and could change or be limited. Requires verification against the terms of Stremio and OpenSubtitles.** | Official and documented; its terms and download quotas **require verification** |

Hebrew is thin on the keyless add-on (0 to 2 per film). The official API probably has more; not measured, since it needs a key.

## What I propose

### A. Finding the right film
1. **Played from a film's page:** the film's IMDb id is already known. That is exact, better than a name.
2. **Played from the Real-Debrid screen** (a file with no film): the file's name is read (`The.Shining.1980.1080p.BluRay.mkv` gives "The Shining", 1980; `Show.S01E02` gives a season and an episode), and Cinemeta's search (already used by SCARY) turns it into an IMDb id. The year must match, or nothing is fetched: a wrong film's subtitles are worse than none.
3. **Series:** by IMDb id, season and episode.

### B. Choosing the best subtitle (core logic, tested)
The service returns many subtitles for one film (35 to 98). They are ranked by:
1. **The requested language** (the player's subtitle language, English unless changed).
2. **Release match:** how many words the subtitle's release name shares with the played file's name (`BluRay`, `1080p`, `x264`, the release group), because subtitles made for another cut drift out of sync.
3. **Frame rate:** a subtitle at the film's own frame rate (23.976 or 25) before one that would drift.
4. **Plain before hearing-impaired and forced**, as in the track window.
5. The service's own order after that.

### C. In the player
- In the **"Audio and subtitles" window**, a section **"Find subtitles online"** lists the languages the service has for this film (with how many), and choosing one fetches the best subtitle of that language. It then appears in the subtitle list like any other track, and is remembered for the file.
- **If the film has no subtitles in the preferred language** (none in the file, none in the torrent), the search can start by itself when the film starts (see the questions), with a short message: "Subtitles found: English".
- **Hebrew in any encoding** is converted to UTF-8 by the same code as the torrent's subtitle files (core `SubtitleFiles`), even if the service's answer were not already UTF-8.

### D. Settings
- **Settings → Real-Debrid / player section:** "Find subtitles online: on/off", with a line saying what leaves the TV.

## Privacy and security (requires legal review before publishing)
- **What leaves the TV:** the film's **IMDb id** (or, for a series, the id, season and episode), the **language**, and the **IP address**, to the subtitle service. Never the file name, the Real-Debrid link or token, the viewer's history, or the TMDB, Stremio or AI keys. With "Find subtitles online" **off**, nothing is sent.
- **A film's name is parsed on the TV;** only Cinemeta (already used for search) sees the title text, when a file has no known film.
- **Every answer is untrusted:** the link must be HTTPS on the service's own host (checked, no redirects to other hosts), the size is limited (2 MB, as for torrent subtitle files), and the text is parsed as text only, never run. A file that is not subtitles (no time lines) is refused.
- **Kept on the TV only:** the fetched subtitle (UTF-8) in the app's cache with the torrent subtitles (up to 30 files), and the choice for the file (as the other track choices). Not backed up.
- **Copyright:** subtitles are made by volunteers and belong to their authors. SCARY does not host or store them in its repository. Legal status of fetching them **requires legal review**, as for the rest of SCARY's third-party services.

## Accessibility
- The new section works with the arrows and OK, RTL included; each language row says its language and how many subtitles it has ("Hebrew, 2 subtitles"); the message when subtitles are found is announced.

## Files (estimate)
- **core:**
  - `player/OnlineSubtitles.kt`: the service's answer parsed hostilely, language-code mapping (`heb` to `he`, `pob` to `pt`), ranking by release and frame rate;
  - `player/FileNames.kt`: title, year, season and episode from a file name;
  - tests on real answers and real file names.
- **app:**
  - `data/SubtitleRepository.kt`: the search and the download, the host checks, the cache;
  - the track window section, the player's automatic search, the setting, strings in 5 languages.
- **UI test** with a fake server; docs (privacy draft, terms).

## Stages
1. **core:** file names, the answer's parsing, ranking, tests.
2. **app:** repository, the section in the window, the automatic search, the setting.
3. **Finish:** strings, UI test, docs, CI, version 0.5.11.

## Risks and limits
- **The keyless service is not meant for third-party apps** (see the table). If it changes, the section says "subtitles could not be found" and the rest of the player is unaffected.
- **Hebrew coverage on the keyless service is thin** (*The Shining*: none). With the official API, more, but it needs the viewer's own key and account.
- **A subtitle can still be out of step** for a rare cut. The existing subtitle delay buttons stay for that.
- **Not measured on a real TV or against a real account.**
