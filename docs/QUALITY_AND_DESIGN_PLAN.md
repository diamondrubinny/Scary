# Film quality control and a new design language (19.11.2026)

**The request:** "Fix the quality control on the films, and change the design language to something new and comfortable."

## The owner's choices (19.11.2026)
- **Quality rules:** all four proposed, with the rating floor at 5.0.
- **Design:** direction **C · Noir**, every screen in one version.

## Part 1: quality control on the films

### What existed
`RowAudit` checked every film of every online row for: its place (a French row holds French films), the films kept off every shelf (`OriginFilter`), its theme (horror, thriller or mystery; never a documentary or a children's film) and repeats. **Nothing checked the film itself:** how long it is, whether it has a rating, how low that rating is, or whether "thriller" is the only thing that brought it in.

### What was measured (19.11.2026)
16 live catalog pages of Cinemeta, the same ones SCARY reads (popular horror at five depths, top rated, thrillers, mysteries, six years). Of the 486 films that passed the filter and reached the screen:

| Problem | Films | Examples |
|---|---|---|
| Not out yet, no rating, in ordinary rows (the "top rated" row included) | 20 | Resident Evil (2026), Never After Dark (2026), Verity (2026) |
| Shorter than an hour | 9 | "The Death of David Cronenberg" (1 minute), "Backstroke" (11), "The Blair Thumb" (28) |
| Thriller or mystery with no horror in it, and a genre that has nothing to do with fear | 12 | Apocalypse Now (war), Clue (comedy), Mr. Monk's Last Case (comedy), Smooth Talk (romance) |
| A rating between 4.0 and 4.9 | 7 | mostly this year's and last year's films |
| No poster, or no synopsis | 0 | (seen in the raw pages, already left out by other rules) |

About one film in ten was one a viewer would call "why is this here?".

### The rules
1. **No short films in film rows:** a film whose length is known and under 45 minutes is left out. (45 and not 60: "Host", 57 minutes, is a real feature.) A series' length is one episode's, so it is never "too short".
2. **A film with no rating belongs only where "new" is the point:** it stays in this year's rows when it is of this year or later, and leaves the popular rows and "top rated".
3. **A floor under the rating** in the online rows, 5.0: below it a film is still found by search and still opens, it is just not offered.
4. **Fear has to be in it:** a film that is a thriller or a mystery but not horror, and is also a war film, a comedy, a romance, a musical, a biography, a history film, a western or a sports film, is left out. Horror itself is never ruled out by another genre (a horror comedy stays).
5. **A poster and a synopsis are required.**

### As built (version 0.5.22)
- **core `catalog/FilmQuality.kt`:** the five checks (`INCOMPLETE`, `TOO_SHORT`, `UNRATED`, `LOW_RATED`, `NO_FEAR`) in one function. The rows of what is new: `horror_new`, `series_new` and the "New nightmares" category.
- **Where it runs:** the online rows and their later pages (`CinemetaRepository`), every category (`Categories.matches`), the audit (`RowAudit.check`, with the new problems) and every cache read from disk (`CinemetaCache.withoutHidden`), so rows written by an older version are cleaned too.
- **Not touched:** the hand-picked library (its films were chosen by hand), search, and the rows from TMDB, which already ask TMDB for a minimum number of votes.
- **Checked with the real code on the live sample:** 482 films before, 446 after: 13 unrated, 11 without fear, 7 rated low, 5 shorts. Unrated films of this year stay in this year's row.
- **Diagnostics** shows how many films each rule left out at the last refresh (counts only, never a film's name).
- **Tests:** `FilmQualityTest`, on real films from the sample. The sample films of the older tests became whole, rated films, since the new check rightly left the bare ones out.
- **Privacy:** nothing changes. The rules run on the TV over data already fetched.

### Known limits
- **"Vertigo" and "Rebecca"** are thrillers with romance and no horror tag, so they leave the rows (search still finds them).
- **Cinemeta gives a rating but not how many voted,** so a little-known film with a flattering rating passes.
- **The rules read genres as Cinemeta tags them;** a wrong tag is a wrong answer.

## Part 2: a new design language

### What is uncomfortable today (from the current screenshots)
- **Six round buttons with no words** at the top of the home screen: the viewer has to remember which is which.
- **A film's page holds up to twelve outlined buttons** of the same weight; the one that matters ("Watch") is found by its colour only.
- **The very condensed title font** (Karantina) is striking but tiring, and reads as dated next to today's streaming apps.
- **The gold glow around the focus** is wide and soft: where the focus is, is clear; where it ends, is not.
- **A QR code for donations in the header of the home screen,** above tonight's film.
- **Settings is one long list** with a jump bar; everything looks equally important.

### Principles
1. **Words before icons.** Navigation is named: Home, Films, Series, My list, Apps, Search, Settings.
2. **One primary action per screen,** filled; the rest quiet.
3. **The art leads.** A film's own picture fills the screen; text sits on black, never on a box.
4. **A focus that cannot be missed:** a hard outline with a gap (3:1 contrast at least), with no wide glow.
5. **A clean, wide sans for titles** (Rubik, already bundled, covers Hebrew, Russian and Latin; Cairo for Arabic). The dripping logo stays as the brand.
6. **Still SCARY:** black, bone and blood. Nothing turns blue or cheerful.
7. **No slower than today:** no effect that costs frames on a weak TV.

### The three directions shown (mock-ups in `docs/design/`)
| | Direction | Feels like | Cost and risk |
|---|---|---|---|
| A | Cinema (`direction-a.jpg`) | Today's streaming apps: named tabs, the film's picture across the screen, pill buttons | Lowest |
| B | Glass (`direction-b.jpg`) | A side rail, frosted panels, wide cards, a red glow on the focus | Highest: real blur needs Android 12 and costs frames |
| **C** | **Noir (`direction-c.jpg`), chosen** | A film magazine: black, light type, one red line, dimmed posters with the focused one lit | Medium: light type at three metres needs care with contrast |

### Noir, as a specification
- **Colour:** pure black background; bone (#E9E1CF) for text; ash for secondary text, at no less than 4.5:1 on black (today's #A39E92 is 7.6:1; the mock-up's dimmer grey is not used for text); blood red (#D94040) only as an accent: the line under the current tab, the eyebrow, the fear level. No gold.
- **Type:** Rubik for titles in a regular weight at large sizes (a film's title), bold only where small; Assistant for running text; wide letter-spacing on small labels (row titles, eyebrows). The smallest text stays 14 sp.
- **Navigation:** a row of words at the top; the current one is bone with a red line under it, the rest ash. Focus on a word: bone, with the outline.
- **Buttons:** the primary one is a bone rectangle with black text and nearly square corners (4 dp); the others are words, ash, that turn bone and gain the outline in focus.
- **Posters:** nearly square corners (3 dp); the ones not in focus are dimmed to about 55%, the focused one is at full light, a little larger, with a 2 dp bone outline at a 4 dp gap. Dimming is paint only (an alpha), so it costs nothing.
- **Rows:** a small spaced-out title, and a counter ("01 / 150") at the far end.
- **Lines, not boxes:** one hairline separates the hero from the rows; settings rows are separated by hairlines, with the value at the far end.
- **Motion:** the same budget as today (`Ambient`), none of it new; everything stops with "effects off".

### How it is built (one version, in this order inside it)
1. **Tokens and components** (`Theme.kt`, `Basics.kt`, `Cards.kt`, `FocusSurface.kt`): colours, type, shapes, the focus style, buttons, chips, cards.
2. **Home:** the named top bar, the hero, the rows.
3. **A film's page:** one primary action, the secondary ones as words, grouped.
4. **Search, Settings, the remaining screens.**
5. Screenshots in CI in the five languages, RTL, 130% font size and the phone layout; the existing UI tests keep passing (they use tags, not looks).

### Accessibility (kept or improved, never traded)
- Text contrast at least 4.5:1, focus indicators at least 3:1, nothing said by colour alone, the smallest text stays 14 sp, motion stops with "effects off".
- Named navigation replaces icon-only buttons.
- A check against WCAG 2.1 AA and Israeli Standard 5568 by an accessibility consultant is still **needed before any claim of conformance**.

### Risks
- **One large version** is harder to test than stages: every screen is compared in CI screenshots before it ships.
- **Light type** on a TV: sizes and weights are checked on the screenshots at 130% font size and from the emulator's 1080p frame.
- **The owner sees it first on their own TV:** a mock-up is not the product; adjustments after the first build are expected.
