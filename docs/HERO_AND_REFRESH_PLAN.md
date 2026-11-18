# The hero's title that does not move, and a wider, fresher choice of films (18.11.2026)

**The report (with a photo of the home screen):** "The film's title does not move or load in a scary way. And improve the films' refresh; the choice of films is still limited."

## 1. The title on the home screen
**Checked in the code:**
- The shiver (`fx/Chill.kt`: the title comes out of the dark, trembles, its light dips twice, and a smaller shiver returns every 7 to 11 seconds) was only on the **movie page** and on the screen shown while a film opens. The **home screen's title** (the photo) was plain text: it never had the effect.
- The effect is also off whenever "Fog and film grain" is off, or the TV's own "Remove animations" is on (animation scale 0). **Not known for the owner's TV:** the photo does not show it.

**Done:**
- The home screen's title shivers like the movie page's, again for each new film and each return to the home screen.
- Settings → "Fog and film grain" now says "On, but the TV removes animations" when that is why nothing moves, and Diagnostics has a line with the TV's animation scale.
- **Not overridden:** when the TV removes animations (an accessibility setting), SCARY still stays still.

## 2. The choice of films, measured against Cinemeta (18.11.2026)
| Row | Before | After |
|---|---|---|
| Popular horror right now | 48 films (1 page) | 146 (3 pages) |
| New this year | 51 (3 pages of the year's mixed catalog) | 101 (6 pages) |
| Popular thrillers | 50 | 147 |
| Mysteries everyone is watching | 98 | 147 |
| Last year's scares | 37 | 93 |
| **Horror in the spotlight** (new: Cinemeta's "featured" catalog) | none | 145, of which 83 were in none of six pages of the popular catalog |
| Distinct films in the home's online rows at first load | **343** | **about 730** |

- Every row still goes on loading while the viewer scrolls to its end, as before; this is what is there before any scrolling.
- The series tab the same: three pages of each popular row, six of this year's.
- Cost: about 20 more catalog pages per refresh.

## 3. The refresh
**Checked:** the rows and the library's own films' details were read together, once a day.

**Done:**
- **The rows are read three times a day** (every 8 hours) by themselves; the library's films' details (hundreds of requests) stay once a day, so the service is not asked more than needed.
- **Settings → "Refresh films now"**, with when they were last read. Not more than once in two minutes.
- After the update the rows are read at once (rows version 11), not within a day.

## Privacy and security
- Nothing new leaves the TV: the same catalog pages from Cinemeta, more of them and more often (docs/PRIVACY.md updated).

## Accessibility
- The shiver is the existing one, safe for photosensitive viewers (WCAG 2.3.1): it dims to 55% at most, twice a second at most, and stops with effects off or "Remove animations" on.
- The new settings row and messages are read by screen readers; strings in 5 languages.

## Tests
- `CinemetaTest` (the rows' own age), all core tests; UI test `settingsOfferToRefreshTheFilmsNow` (the row, its two messages, the motion line, the hero's title).

## Not checked
- On a real TV: whether the owner's TV removes animations. If the title still does not move after this version, Settings → "Fog and film grain" and Diagnostics → "Motion" will say why.
- The effect itself was not watched on a device from here; the code path is the movie page's.
