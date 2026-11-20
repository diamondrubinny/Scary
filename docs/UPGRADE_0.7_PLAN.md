# The next visible upgrade: four chosen options (20.11.2026)

**The request:** "תשדרג את האפליקציה". Asked what to build, the owner chose all four options of `docs/UPGRADE_0.6.2_PLAN.md`:
cast with faces, other titles for your own files, "My year in horror", viewer profiles.

**Order (small first, as with the eight options of 0.5.18 to 0.5.21):**

| Stage | Version | What |
|---|---|---|
| 1 | 0.7.0 | Cast with faces and roles; other titles of a film for recognising your own files (both: TMDB key) |
| 2 | 0.7.0, with stage 1 | More in "Your year in fear" (the screen exists; see the correction in its section) |
| 3 | 0.8.0 | Viewer profiles (its own design section is written before it is built) |

**Scope of this upgrade:** nothing here searches for, fetches or lists stream or torrent sources. "Your own files" are the files already in the viewer's Real-Debrid account. (The "sources" feature merged into main meanwhile, PR #49, is a separate piece of work behind a build switch, off in the official APK; this upgrade does not change it.)

## Stage 1 (version 0.7.0)

### 1a. Cast with faces and roles
**Today (0.6.1):** up to six names from Cinemeta, each a button; the actor's films are found on TMDB by an exact name match.

**What is built:**
- **On a film's page, with the viewer's TMDB key and online data on:** the cast row becomes cards: the actor's photo, their name, and the role they play ("Jamie Lee Curtis · Laurie Strode"). Up to 12, in TMDB's billing order.
- **OK on a card** opens "Films with <actor>" by TMDB's own id for that person, so a namesake can no longer be taken for them. The actor's photo shows beside the heading.
- **Without a key, with online data off, on family night, or for a series:** exactly as today (names from Cinemeta).
- **A photo that does not load** shows the actor's initials in its place; nothing moves.

**Asked of TMDB (all in its official API description):**
- `/3/movie/{id}/credits`: the film's cast (`id`, `name`, `character`, `order`, `profile_path`).
- `/3/person/{id}/movie_credits`: as in 0.6.1, now by the id from the cast.
- Photos: `image.tmdb.org/t/p/w185/<file>`, the same image host as the posters of the TMDB rows.

### 1b. Other titles of a film, for the viewer's own files
**Today (0.5.12):** a file is recognised when its name holds one of the film's titles SCARY knows (five languages, with spelling variants). A file named by another title of the film is not.

**What is built:**
- **When a film's page opens** (TMDB key, Real-Debrid signed in, online data on, not family night), SCARY asks TMDB once for the film's other titles and keeps them on the TV. The page then looks through the viewer's files again with them.
- **On the Real-Debrid screen** the same kept titles tell which film a file is.
- **A film's own title wins:** a file that carries film B's own title is never given to film A because of one of A's other titles. Two films that fit equally stay unmatched, and the viewer decides (the correction window of 0.5.12).
- **Stricter than for a film's own title:** by another title, the file's name must carry the film's own year, exactly (by its own title a year within one is enough, as before). Another film may share the title, and SCARY knows other titles only for the films whose pages were opened.

**Asked of TMDB:** `/3/movie/{id}/alternative_titles` (`titles[].title`).

**Kept on the TV:** `alt_titles.json`: for up to 800 films, the IMDb id and up to 40 titles each. The oldest leave first. Removed together with the TMDB key. Not in the backup file.

### Threat model (short)
| What | Risk | Control |
|---|---|---|
| TMDB's answers | Malformed or hostile JSON | Parsed without trusting its shape; counts and lengths capped; control characters refused; adult entries left out |
| Photo addresses | An address pointing somewhere else | Only a TMDB file name (`/<letters and digits>.jpg`) is accepted and joined to the fixed image host |
| Other titles | A generic title claiming the wrong file | A film's own title wins; the year rule; two that fit are none; at most 40 titles a film |
| The kept file | Tampered or damaged | Read through the same checks as an answer; a bad file is ignored and written again |

### Privacy
- **Sent to TMDB,** with the viewer's own key, from the TV's IP address: the id of the film whose page is open, and the id of the actor chosen. Nothing about the viewer.
- **Photos of real people** are the publicity photos TMDB serves, loaded from TMDB's image host only while the cast row or the actor's screen is shown; none is stored by SCARY beyond the image cache. TMDB is credited in "About" and on the actor's screen, as its terms ask. **A lawyer's check is advised (דורש בדיקה משפטית)** only if the app is ever distributed commercially.
- **Kept on the TV:** which films' other titles were fetched (so, which pages were opened while Real-Debrid was signed in). Never sent anywhere; removed with the key.
- `docs/PRIVACY.md` gets both rows.

### Accessibility
- Each cast card is one button, read as "<actor>, <role>"; arrows and OK; the Noir focus outline.
- The photo is decoration for a screen reader (the name is read); initials stand in when there is no photo.
- RTL: the row runs right to left; names keep their own direction.

### Files
- **core:** `Tmdb.creditsUrl`, `parseCast`, `alternativeTitlesUrl`, `parseAlternativeTitles`; `realdebrid/AltTitles.kt`; `FileMatch` (other titles, "own title wins"). Tests: `TmdbTest`, `FileMatchTest`, `AltTitlesTest`.
- **app:** `TmdbRepository.cast`, `personFilmsById`, `altTitles`, `loadAlternativeTitles`; `HomeViewModel` (`cast`, `loadCast`, `openPerson` with an id; files matched with other titles); `DetailsScreen` (cast cards); `PersonScreen` (photo); strings in five languages; UI tests.

### Review notes (an independent review of the first build, 20.11.2026)
Found and fixed before the first push:
- **A focused cast card could leave its name below the screen's edge** (only the photo took the focus). The whole card takes it now, and every card is the same height.
- **The page moved under the viewer when TMDB answered late** (a new row pushed the focused poster down). The cards now come in only while the focus is in the header, or nowhere on the page; a viewer already further down gets them when they come back up.
- **Back from an actor's screen landed on "Watch".** It lands on that actor again (their card, or their name).
- **Another title could claim a neighbour's file** ("Zombie.1979" for a 1978 film that has "Zombie" among its titles). By another title the year must now be the film's own.
- **A line break in a TMDB name joined two words; direction marks could turn the text around.** Breaks become spaces; the marks are dropped.
- **Matching ran on the main thread, and every film found to have no other titles rebuilt the whole index.** Off the main thread now; the index is rebuilt only when titles themselves change.
- **An actor opened by name, then by their card, showed the name's films for a moment.** The screen now checks TMDB's id too.
- **Older than this version:** a film's page that came back to the front (Back from another film) had lost its "Play from Real-Debrid" button. It looks for its own files again.

### Limits
- **Films only.** A series' page keeps the names from Cinemeta, and series files are matched by the titles SCARY holds (TMDB's "find" is read for films only today).
- **Written from TMDB's API description,** not from a live answer (no key in the workspace): **to verify on a TV with a key.** The photo size `w185` is the profile size TMDB documents; if it is refused, initials show.
- **Other titles arrive when a film's page is opened,** not for the whole library at once (that would be thousands of requests).
- **Two films of the same year that share another title,** only one of whose pages was opened: the file goes to that one until the other's page is opened; then neither, and the viewer decides. The correction window (hold OK on the file) settles it either way.
- **Not tested by a UI test:** the swap from names to cards on a film's page (it needs TMDB's answer; the cards and the actor's screen are tested by themselves with made-up data).

## Stage 2 (in version 0.7.0, being small): more in "Your year in fear"
**A correction:** when the options were listed, "My year in horror" was written as missing. It is not: **"Your year in fear" has been in Settings for a long time** (films watched, hours, average fear, the scariest film, favourite subgenre and decade, films loved; this year and all time; the day a film is marked as watched is kept). The option was offered without checking the code first.

**What stage 2 adds to the screen that exists,** all worked out on the TV, nothing sent:
- **the favourite director and the actor seen most** (from the films' own details), named only from two watched films on: one film says nothing;
- **how many of the watched are series;**
- **the busiest month,** in this year's card only (across years a month's name says nothing).

**Files:** `FearStats` (core) and its test in `HandyFeaturesTest`; `StatsScreen`; four strings in five languages.

## Stage 3 (version 0.8.0): viewer profiles
"Who is watching?": each person their own list, watched, loved and bucket list, under the same PIN rules. Large: it touches every stored list and the backup file's format. **Its design (what is shared, what is per person, how the PIN and family night apply, how old data moves into the first profile) is written here and shown before the build starts.**
