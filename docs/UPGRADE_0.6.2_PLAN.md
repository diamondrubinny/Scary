# Upgrade: 0.6.2 (tools and libraries), and what to build next (20.11.2026)

**The request:** "תשדרג את האפליקציה" ("upgrade the app"), with no detail.

**How I read it (an assumption, written down):** two parts.
1. **What needs no decision:** bring the build tools and libraries up to date. Two Dependabot pull requests (#33, #4) have been open since 16.11. This is version 0.6.2.
2. **What does need the owner's choice:** the next visible upgrade (0.7.0). Options are listed below; nothing of part 2 is built.

## Part 1: version 0.6.2 (built on `claude/upgrade-toolchain`)

| What | From | To | Why |
|---|---|---|---|
| Android Gradle Plugin | 9.4.1 | 9.5.1 | Build tool; newer lint |
| Gradle | 9.8.2 | 9.9.1 | Build tool |
| kotlinx-serialization | 1.12.0 | 1.13.0 | Reads every JSON answer (Cinemeta, TMDB, the catalog) |
| ZXing core | 3.5.3 | 3.5.4 | Draws the QR codes |
| GitHub Actions | checkout 4, setup-java 4, setup-gradle 4, cache 4, upload-artifact 4, download-artifact 4 | 7, 5, 6, 6, 7, 8 | The CI's own steps |

- **Every other library is already the newest** Dependabot knows (Kotlin 2.4.21, Compose BOM 2026.09.00, Media3 1.11.1, Coil 3.6.3, coroutines 1.11.0).
- **Supply chain:**
  - The Gradle wrapper jar was made here with Gradle's own `wrapper` task and equals Gradle's published checksum for 9.9.1 (`621008af…cdaf8`), and Dependabot's copy.
  - New: `distributionSha256Sum` in `gradle-wrapper.properties`, so a tampered Gradle download is refused.
- **Found by the newer lint:** three fonts the app has not drawn since the Noir design (0.6.0).
  - Karantina and Oswald moved to `tools/promo/fonts` (only the promo films use them); Rakkas removed (nothing uses the file; the website embeds its own copy).
  - The download is about 246 KB smaller.
  - "About" in Settings named seven fonts; it now names the four the app ships (Assistant, Rubik, Rubik Wet Paint, Cairo), in the five languages.
- **Nothing the viewer sees changes** except that line in "About".
- **Privacy, security, accessibility:** no new data, no new network use, no new screen.

### Checks
- Here: `:core:test`, `:app:lintDebug` ("No issues found"), `:app:assembleDebug`, `:app:compileDebugAndroidTestKotlin`, the credit check, the phone remote's JS check.
- In CI (the branch's push run): the TV emulator's UI tests and the screenshots, which is also the real test of the six newer Actions.
- **Risk:** the Actions jump several major versions at once. If the CI run breaks on one of them, that one is fixed or kept at its old version, and this file says which.
- **First CI run (371):** build green on the new tools; all 62 UI tests passed, yet the job was red. The cause was in `screenshots.sh`, not in the upgrade: `echo "$result" | grep -q '^OK ('` fails now and then under `pipefail` (grep ends at the first match while echo is still writing). `screenshots-mobile.sh` already avoided it; the TV script now does too, in both places it had the pattern (the second one could have hidden a crash of the release build).

## Part 2: options for the next visible upgrade (not built; the owner chooses)

| # | Option | What it adds | Today | Size |
|---|---|---|---|---|
| 1 | **Cast with faces and roles** | With a TMDB key: each actor's photo and the role they play, and the actor's page opens by TMDB's own id (no guessing by name) | Names only, from Cinemeta; the actor's films are found by an exact name match | Small to medium. `/3/movie/{id}/credits` is in TMDB's official spec (`profile_path`, `character`, `id`); what it returns was not seen (no key here) |
| 2 | **Other titles of a film, for your own files** | With a TMDB key: a file named by another title of the film (a foreign or re-release title) is recognised as that film | Spelling variants only (0.5.12); the correction window otherwise | Small. `/3/movie/{id}/alternative_titles` is in the spec; left open in `docs/NEXT_UPGRADE_PLAN.md` |
| 3 | **More in "Your year in fear"** | The favourite director, the actor seen most, the busiest month; kept on the TV only | **Corrected 20.11:** the screen exists (films watched, hours, average fear, the scariest, favourite subgenre and decade). It was first listed here as missing, without checking the code | Small |
| 4 | **Viewer profiles** | "Who is watching?": each person their own list, watched, loved and bucket list, under the same PIN rules | One viewer per TV | Large. Touches every stored list and the backup file |

**Scope of these options:** none of them searches for, fetches or lists stream or torrent sources. (While this was being built, a separate piece of work was merged into main, PR #49, `docs/TORRENT_SCRAPER_PLAN.md`: a "sources" feature behind a build switch, off in the official APK. It is not part of this upgrade and is not changed by it.)

**Privacy, for whichever is chosen:** 1 and 2 send a film's id to TMDB with the viewer's own key (nothing about the viewer); 3 and 4 never leave the TV (4 adds profile names to the backup file the viewer makes).
