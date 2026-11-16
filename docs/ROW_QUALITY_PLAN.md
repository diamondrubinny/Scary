# Quality control for the rows: American films out of the European row (16.11.2026)

**The request:** "In the European movies there are lots of American movies. Create a quality-control module and smarter sorting." Built in version 0.5.10.

## What was wrong (measured on Cinemeta's 1,705 most popular horror films)
- The online part of "European horror" took every film whose **first** country Cinemeta lists is European. 276 films qualified; **42 of them list the United States too**, and most of those are American.
- Cinemeta's country order is no reliable sign of who made a film. A German fund paying for part of a film puts "Germany" first: *Constantine*, *The Cell*, *Blade II*, *Jeepers Creepers*, *The Devil's Rejects*, *Hide and Seek* and the new *Resident Evil* are all "Germany, United States". *Land of the Dead*, *Mindhunters*, *Silent Hill*, *Quarantine* and *Suspiria* (2018) are the same story with France, the Netherlands, Spain or Italy.
- The same rule fed the Spanish-language, Asian and giallo rows. *The Grudge* (2004), an American remake, was also in the hand-picked Asian row.
- **A rule on countries alone cannot separate the two groups.** *Blood Red Sky* ("Germany, United States") is German; *Constantine* ("Germany, United States") is not. Both have the same two countries in the same order. What separates them is the language: *Lamb* ("Iceland, Sweden, Poland, United States") is in Icelandic, *Infested* in French, *Dead Snow 2* and *The Trip* in Norwegian.

## What was built

### 1. Origin (`core/catalog/Origin.kt`): placing a film by country and language
- The first country **proposes** a film for a row about a place (European, Spanish-language, Asian horror, giallo).
- **No American producer listed:** the film stays, as before.
- **The United States is listed too:** the film's **original language** decides. A European language puts it in the European row, English takes it out. The same holds for the Spanish, Asian and Italian rows.
- **Language not known yet:** the film is **held back**, never guessed at (`Verdict.UNKNOWN`).
- India was dropped from "Asian horror" earlier (hidden everywhere).

### 2. The language lookup (the app)
- Cinemeta's catalogs carry the TMDB id of 1,693 of those 1,705 films (`moviedb_id`, now read into `CinemetaMeta.tmdbId`).
- With the viewer's TMDB key, the app asks TMDB (`GET /movie/{id}`, the field `original_language` was checked against TMDB's official OpenAPI spec) for **only** the films the check cannot place: 69 of the 1,705.
- Each answer is **kept on the TV for good** (`origin_languages.json`, up to 6,000 films), since a film's language does not change. A film TMDB does not know is kept as unknown. A request that failed is asked again later.
- It happens in the daily refresh of the category pool, and for each page of an endless row about a place, so scrolling gets the same check.
- **Without a TMDB key** no language is known, so American-listed films wait outside these rows. The cost: a few real European co-productions (*Lamb*, *Infested*) are missing until a key is added. The row is never wrong, only shorter.

### 3. RowAudit (`core/catalog/RowAudit.kt`): the quality-control module
One check, used for both reporting and cleaning, covering every rule the rows are built from:
- **AMERICAN_LED:** an American film in a row about a place.
- **ORIGIN_UNCLEAR:** American too, language not known yet.
- **OFF_REGION:** its first country is not in the row's place.
- **HIDDEN_ORIGIN:** an Arab or Indian film (the earlier decision).
- **OFF_THEME:** a documentary, a children's film, or not horror, thriller or mystery.
- **DUPLICATE:** the same film twice in a row.

`audit` reports; `clean` takes the offenders out. The cache read at start-up goes through `clean`, so **a cache written by 0.5.9, with American films in the European row, is cleaned before the first frame** (and rows version 10 refreshes it at once).

### 4. Smarter sorting
- A row about a place lists the films its **countries alone** say first (no American producer), then those that only the **language** says (*Lamb*, *Infested*). The surest films lead.
- The rest of the order is Cinemeta's popularity, as before.

### 5. Visible on the TV
Settings → About → Diagnostics has a new line: `Origin check: <films kept out of the rows about a place> · <films waiting for a language>`. Counts only, never a film's name.

### 6. The hand-picked rows: `tools/catalog/audit_rows.py`
The same rules, run against the 490 films of `catalog.json` and `series.json` with Cinemeta's countries. The country lists are read from `Origin.kt` and `OriginFilter.kt`, so there is one list. Problems (exit code 1) and notes (exit code 0).
- **Found and fixed:** *The Grudge* (2004), an American-led remake, in "Asian horror". It stays in "Haunted houses".
- **Looked at by hand and kept** (written in `REVIEWED`, with the reason): *Lamb* (Icelandic), *Ringu* (Japanese), *Thirst* (Korean), *When Evil Lurks* (Spanish-language).
- **Notes, not problems:** the library's European row holds six British or Australian-British films on purpose (*Severance*, *Dog Soldiers*, *Eden Lake*, *Kill List*, *The Ritual*, *You Won't Be Alone*), and three films are in no row (*Rabies*, *Children of the Fall*, *Cats on a Pedal Boat*, left by the Israeli row's removal).
- Needs the network, so it runs by hand, not in CI. A core test (`CuratedRowsQualityTest`) keeps what it found fixed.

## Privacy and security
- **To TMDB, new:** the TMDB id of a film whose origin is unclear (a film's id, nothing about the viewer), with the viewer's own key, as in the existing TMDB requests. Nothing is asked without a key.
- **On the TV:** `origin_languages.json` holds TMDB ids and language codes, no personal data, and is not backed up. The privacy draft has the new row (**requires legal review**, like the rest).
- A TMDB answer is untrusted: only a two- or three-letter lower-case code is accepted as a language.

## Accessibility
- No new screen: the same rows, with the right films. The diagnostics line is plain text, read by screen readers, in five languages.

## Risks and limits
- **A language is only as good as TMDB's.** A film TMDB files as English that is really European (*Marrowbone*, a Spanish director's English-language film) is out. The rule favours "never an American film" over "every European one".
- **Films with no US listed** are not checked by language: an American film that Cinemeta lists with no American country at all would still be proposed by its first country. None stood out in the part of that list that was read through (the 234 European-first films with no American country), but the check does not cover them.
- **The first run after the update** looks up the languages of the unclear films (an estimate: 69 in the horror films read, perhaps twice that in the whole pool; a few seconds with a key) before the new rows fill.

## Checks
- **core:** `OriginQualityTest` (11 tests, on real films: eleven American films with a European country first, four European films that list the United States, two with no American producer) and `CuratedRowsQualityTest` (2).
- **app:** lint, UI tests compile, and CI on the TV and phone emulators.
