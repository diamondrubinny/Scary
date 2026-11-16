# Expanding the catalogs: every row endless, "New nightmares" always up to date (16.11.2026)

**Approved (16.11.2026):**
- "New nightmares" shows **only new films**. The curated films appear only when nothing could be read online, so the row never disappears.
- TMDB first, Cinemeta as backup.
- "Fear level 5" goes on with top-rated horror after the curated films.

**The request:** "Expand all the catalogs, including the catalog of new horror films; there's something 'new fears' that isn't updated. And make infinite scroll that keeps pulling films from the personal TMDB."

## What exists today (checked in the code)
- **The library has 30 curated rows. 18 are already endless:** when the viewer reaches the end of a row, more films load from Cinemeta (zombies, vampires, slashers…).
- **12 rows are fixed** and never update:
  - **"New nightmares" (30 films)**, the row from the report;
  - Stephen King, giallo, witches, cosmic horror, classic monsters, nature attacks;
  - Spanish, Israeli and European horror;
  - horror comedy, and "Fear level 5".
- **The TMDB rows** ("New horror", "Top rated", "Thrillers", "Coming soon") are already endless with the viewer's key.
- **What was checked:**
  - TMDB's discover endpoint, against its official spec: genres combine with AND/OR, plus original language, release date and sorting. Keywords like "witch" need internal ids that I can't verify without a key, so I don't rely on them.
  - Cinemeta's catalogs: every film carries screenwriter and country (e.g. Carrie → "Stephen King").

## What I propose

### A. "New nightmares" always up to date
- The newest horror films (from the last 12 months), most popular first, then the curated films.
- **With a TMDB key:** TMDB's discover with horror from the last 12 months, Hebrew titles and synopses, endless.
- **Without a key:** Cinemeta's catalogs of this year and last year (horror only), endless back through the years.

### B. Every fixed row becomes endless (curated films first, as in every category)
| Row | With a TMDB key | Without a key (Cinemeta, a rule like the other categories) |
|---|---|---|
| Stephen King | — | screenwriter "Stephen King" |
| Giallo | Italian, horror/thriller/mystery, up to 1985 | Italy, the same genres, up to 1985 |
| Classic monsters | horror up to 1965, most voted first | horror up to 1965 |
| Horror comedy | horror AND comedy | the Horror and Comedy genres |
| Spanish horror | original language Spanish, horror | Spain/Mexico/Argentina/Chile/Colombia, horror |
| Israeli horror | original language Hebrew, horror/thriller | Israel |
| European horror | — | European countries (not the UK, which has its own), horror |
| Witches | — | words: witch, coven, sorceress… |
| Cosmic horror | — | words: Lovecraft, cosmic, eldritch, ancient god… |
| Nature attacks | — | words: shark, crocodile, piranha, killer snakes or spiders… |
| Fear level 5 | stays curated (see question) | |

### C. "Infinite scroll from the personal TMDB"
- In every row where TMDB has a fitting filter (marked above), the scroll keeps loading from TMDB with your key: up to 500 pages, with Hebrew titles and synopses.
- When TMDB doesn't answer (no network, a key revoked), the row continues from Cinemeta, so it never gets stuck.

## Privacy and security
- **To TMDB:** only the fixed discover parameters (genre, language, dates), your key and IP, as in the existing TMDB rows. No query from the viewer.
- **To Cinemeta:** catalog pages, as today.
- The rules are pure logic in `core`, tested on Cinemeta's real catalogs (as the existing categories were).

## Accessibility
- No new UI: the same rows, now endless.
- A film loaded by scrolling gets a full description as today.

## Files (estimate)
- **core:**
  - `catalog/Categories.kt`: new rules (screenwriter, country, era, genres, words);
  - `CinemetaMeta.writer`;
  - `tmdb/Tmdb.kt`: a discover filter for each row;
  - tests.
- **app:**
  - `CinemetaRepository` (the `writer` field, the "New nightmares" row);
  - `TmdbRepository.loadCategoryPage`;
  - `HomeViewModel.onNearRowEnd` (TMDB first when there's a key, otherwise Cinemeta).
- **Version 0.5.8; docs.**

## Risks
- **Word rules make mistakes now and then** (a film about a "witch hunt" that isn't horror). I'll tune them on the real catalogs, as was done for the existing categories.
- **Israeli horror is a small genre:** the row will grow, but not by much.
- **TMDB requests while scrolling:** at most one page per scroll, under TMDB's limit.

## Implementation notes (16.11.2026), version 0.5.8
- **core:**
  - `Categories`: 12 new rules (writer, first country, years, genres, rating, words), and `replacesCurated` for "New nightmares".
  - `CinemetaMeta.writer`.
  - `Tmdb.discoverUrl(TmdbCategory…)` with 7 filters.
  - `ROWS_VERSION` 9, so the categories fill right after the update rather than within a day.
- **Rules checked on Cinemeta's 1,000 most popular horror films:**
  - "coven" alone also caught "Alien: Covenant", so it's now `covens?\b`;
  - "otherworldly", "another dimension" and "the void" caught alien films, so they were taken out, and aliens are excluded from cosmic horror.
  - Witches: 16 real films. Animal attacks: Jaws, Crawl, Deep Blue Sea and Dangerous Animals. Stephen King: 12 films by screenwriter.
- **"New nightmares" in Cinemeta reads only the catalogs of this year and last year**, not back through the decades for nothing.
- **Endless scroll:**
  - with a key and a TMDB filter for the row, it continues from TMDB (`tcat:<id>`);
  - when TMDB has no more pages, or doesn't answer, the row continues from Cinemeta (`cat:<id>`) until restart.
- **Requires verification:** the TMDB genre id for "Comedy" (35) was not checked against the API (that needs a key). Without it, the "horror comedy" row from TMDB would be empty and would fall back to Cinemeta.
- **Tests:** `NewCategoriesTest` (4) and `TmdbCategoryTest` (2), plus all the existing ones.

