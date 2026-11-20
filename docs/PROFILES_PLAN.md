# Viewer profiles: design for approval (20.11.2026)

**Stage 3 of `docs/UPGRADE_0.7_PLAN.md`, version 0.8.0. Nothing here is built.** It changes how every list is stored, so the design is shown first.

## The idea
"Who is watching?": each person in the home has their own lists. Choosing a profile changes what the home screen suggests, what counts as watched, and where "Continue watching" picks up.

## What is checked in the code (0.7.0)
- **Lists live in one settings store** (`SettingsRepository`, DataStore): My list, Recently opened, watched (with the day), Loved, Not for me, the bucket list, reminders.
- **"Continue watching"** (where each file stopped) lives in `WatchStore`.
- **Settings of the TV** are in the same store: effects, sounds, online data, the PIN, family night, apps, the AI choices.
- **Accounts** (TMDB, Stremio, Real-Debrid) and the TMDB key are one per TV, encrypted in `SecretStore`. The TMDB and Stremio sync read and write My list, Loved and watched.
- **The backup file** (`Backup`, version 1) holds one set of lists.

## What I propose

### 1. What belongs to a profile, and what to the TV
| A profile's own | The TV's (shared) |
|---|---|
| My list, Recently opened | The PIN, the lock level, family night |
| Watched and the day; Loved; Not for me | Effects, sounds, trailers, online data |
| The bucket list and its place | Language, apps, home rows shown or hidden |
| Reminders | The TMDB key; the TMDB, Stremio and Real-Debrid accounts |
| "Continue watching" (where each file stopped) | The AI settings |
| "What do you love?" (the taste picker's answers are the Loved list) | Updates |

Suggestions ("Because you loved", "How much time do you have?", "Your year in fear", Surprise me) follow the profile by themselves, since they are worked out from its lists.

### 2. Profiles
- **Up to 5**, each a name (1 to 20 characters) and a colour. No photo, no age, no email: a name typed on the TV is all there is.
- **One profile until another is added:** a TV with one viewer sees no change at all, and no question at start.
- **With two or more:** "Who is watching?" shows when SCARY starts, and from the top bar (the profile's name) at any time.
- **Removing a profile** asks first, says what goes with it (its lists), and needs the PIN when one is set.

### 3. A child's profile (optional switch on a profile)
- A profile can be marked **"Gentle"**: it shows only what family night shows (the same rule: `FamilyNight`), always.
- **Leaving a Gentle profile for another needs the PIN,** when a PIN is set. Without a PIN the switch is free, and the screen that creates the profile says so.
- Family night for the whole TV stays as it is.

### 4. Accounts and sync
- **The TMDB and Stremio accounts sync with the first profile only.** A second person's lists are theirs: they are not sent to the first person's account, and the account's lists do not land in them.
- **Real-Debrid** files are the TV's (one account), shown in every profile that is not Gentle.

### 5. Storage, and moving what exists
- **The first profile is what the TV holds today, untouched:** the same keys, no copy, no migration step that can fail.
- **Each further profile** keeps its lists under the same keys with a suffix (`…@p2`), in the same store; `WatchStore` likewise.
- **The rest of the app reads "the lists" as today** (`LauncherPrefs`): the store hands it the active profile's. Screens and view models do not learn about profiles.
- **Removing a profile** deletes its keys in one write.

### 6. Backup file
- **Version 2** holds every profile (name, colour, Gentle, its lists).
- **A version 1 file** is still read: its lists are merged into the profile that is active, as today.
- No keys, tokens or PIN in the file, as today.

### 7. Phone remote
Unchanged: it acts on the profile that is on the screen. Switching profiles is done on the TV (a Gentle profile must not be left from a phone without the PIN).

## Threat model (short)
| What | Risk | Control |
|---|---|---|
| A child leaving the Gentle profile | Films the parent locked show | The PIN is asked (the existing lockout after wrong tries applies); said plainly when no PIN is set |
| A profile's name | Odd or very long text on screen and in the backup file | 1 to 20 characters, control and direction characters refused; cleaned again when a file is read |
| The backup file (version 2) | A hostile or damaged file | The existing checks per list; at most 5 profiles; unknown fields ignored |
| One person reading another's lists | Privacy inside the home | Profiles are a convenience, not a lock (except Gentle): said in the privacy document. A PIN per profile is not offered in this version |

## Privacy
- **New data:** profile names and colours, on the TV only (and in the backup file the viewer makes). Nothing is sent anywhere.
- **Names of household members are personal data:** kept minimal (a name only), removed with the profile, never logged. `docs/PRIVACY.md` gets a row.
- **A child's profile:** no age or birth date is asked. A profile for a child is still data about a minor in the home: **requires a legal review (דורש בדיקה משפטית)** if SCARY is ever distributed commercially.

## Accessibility
- "Who is watching?": each profile one button, read as its name (and "Gentle" when it is); arrows and OK; the first profile holds the focus; RTL.
- The colour is never the only sign: the name is always written.

## Stages
1. **Core:** `Profiles` (the list, limits, names), key naming, backup version 2. Tests.
2. **Storage:** `SettingsRepository` and `WatchStore` by profile; removing a profile.
3. **Screens:** "Who is watching?", add, rename and remove in Settings; the name in the top bar; the PIN on leaving Gentle.
4. **Sync:** TMDB and Stremio paused outside the first profile. Backup version 2 on the phone page.
5. Review, UI tests (two profiles keep two lists; Gentle needs the PIN to leave), release.

## Decisions I need from the owner
1. **Accounts sync with the first profile only** (recommended), or should each profile be able to connect its own TMDB account? (The second is much larger: several sessions, several syncs.)
2. **The Gentle (child) profile:** in this version (recommended: it is what makes profiles useful to a family), or later?
3. **Home rows shown or hidden:** shared by the TV (recommended, simpler), or per profile?
