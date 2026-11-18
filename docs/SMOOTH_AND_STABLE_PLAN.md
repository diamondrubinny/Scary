# Faster and steadier, from what was measured and read in the code (18.11.2026)

**The request:** "Upgrade the app according to research: what can make everything snappy, and stable."

## What was checked
| Checked | Found |
|---|---|
| Cold start of the release build (CI, TV emulator, 3 runs) | 1.27 s to the first frame, with the baseline profile. Not the problem. |
| Release build | Minified and shrunk (R8), all of SCARY's code compiled ahead of time on install (baseline profile). Already in place. |
| Which thread builds the catalog and the home rows | Background threads (`flowOn(Dispatchers.Default)`), bundled data read off the main thread. Already in place. |
| **The background effects** (`fx/Backdrop.kt`, `fx/Atmosphere.kt`) | **The main finding.** The art's slow drift ran on the display's clock (60 times a second) and the fog and grain 30 times a second, each step redrawing the **whole screen**: with nothing happening, the TV drew the full scene all the time. Seven gradient brushes and the vignette's were also made anew for every frame. |
| The same effects during a film | They kept running **under the internal player**, which covers them: the screen was redrawn while a film played, for nothing. |
| The lists (`LazyColumn` of rows, `LazyRow` of posters) | A row or a poster was built in the very frame it scrolled into view; the rows did not say which kind they are, so one that left could not be reused for the next. |
| What happens to posters on screen when the catalog is rebuilt (a page loaded at the end of a row, a refresh) | `:core`'s `Movie` is built without the Compose compiler, so Compose could not tell that a film had not changed: every card on screen was drawn again, at the very moment the viewer is scrolling. |
| Image memory (Coil) | Already limited by itself (a share of the memory, less on low-memory TVs) and trimmed when the system asks. Left as it is. |

## What was done (version 0.5.14)
1. **One slow clock for the background** (`fx/Ambient.kt`): fog, grain and the art's drift step 12 times a second (they move a few pixels a second). The screen is redrawn a fifth as often when nothing happens.
2. **The background is one finished picture** (an offscreen layer): moving through the rows above it costs the TV one picture per frame, not sixteen full-screen gradients. The fog's brushes are made once per screen size.
3. **Nothing is drawn under the player.** The background is not there while a film plays.
4. **The lists prepare ahead** (Compose's cache window): the next row down and the next posters along are built in the idle time between frames. Rows say their kind, so they are reused.
5. **Unchanged films are not drawn again** (`compose-stability.conf`): `:core`'s classes and Kotlin's collections count as stable.
6. **A measurement in CI** (`.github/scripts/screenshots.sh`, "Frames" in the run's summary): frames drawn in 10 idle seconds on the home screen, and frame times while moving through it. So the next change that makes it heavier shows.

## Measured (CI, release build, TV emulator)
| | Before (0.5.13) | After (0.5.14) |
|---|---|---|
| Frames drawn in 10 idle seconds on the home screen | see below | see below |
| Janky frames while moving (10 down, 20 right, 10 up) | see below | see below |

An emulator draws with the computer's processor, not a TV's graphics chip: its numbers compare one build with another and say nothing of what a TV shows.

## Stability
- Nothing was found in the code that crashes; the crash guard and the freeze watch from 0.5.5 stay. **What the owner's TV recorded is not known:** a photo of Settings → About → Diagnostics (crash, freeze, start time, motion) is what the next fix should come from.
- The UI tests in CI fail now and then on timing (a different old test each time, passing on a second run). Not a fault of the app as far as was seen; worth steadying separately.

## Not done, and why
- **No change to the image cache or the memory handling:** nothing measured says they are a problem.
- **No new libraries.**
- **Not checked on a real TV.** The effects look the same by design (slower steps of a slow motion); if the fog looks steppy on the owner's TV, the step count is one constant (`AMBIENT_STEPS_PER_SECOND`).

## Privacy, security, accessibility
- Nothing leaves the TV; no data is added. The effects still stop with "Fog and film grain" off or the TV's "Remove animations" on.
