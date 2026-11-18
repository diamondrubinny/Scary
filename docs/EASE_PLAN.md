# Easier to use: settings in sections, icons that say their names (18.11.2026)

**The request:** "Sort out the comfort and ease of the app", after the owner could not find the TMDB key row in Settings.

## What was looked at (the CI screenshots of 0.5.15, and the code)
- **Settings is one list of 44 rows** with three headings. The TMDB key was row 5, language row 11, the parental lock row 24, privacy row 31: reaching the last ones takes about forty presses of the remote, and there is nothing to tell where a setting is. In 0.5.13 a new row ("Refresh films now") went in above the TMDB key, and the owner looked for the key where it used to be.
- **The AI settings sat between the TMDB account and Real-Debrid,** splitting the accounts; "TV settings" sat under the parental rows.
- **The home screen's top bar is six round icons with no words** (bucket list, Ask SCARY, search, surprise, effects, settings). Screen readers hear their names; a viewer has to guess.

## What was done (version 0.5.16)
1. **Settings in eight sections,** each with a heading: Home screen, Films and accounts, Language and effects, Rows and taste, Sound, trailers and sleep, Parents, Ask SCARY (AI), About.
2. **A jump bar under the title:** one button per section. A press moves the focus to that section's first row (the list scrolls with it), so any setting is a few presses away.
3. **Order:** the TMDB key is back right under "Images and info from the internet"; "Refresh films now" comes after the TMDB rows; the AI section comes after the parental rows; "TV settings" is the first row of About.
4. **Icon buttons say their names:** while the remote's focus is on a round icon button (the top bar, the bucket list, search, Ask SCARY), its name shows under it. It takes no room, so nothing moves.

## Not changed
- No setting was removed or renamed, and none changed its meaning.
- The home screen's rows and their order.

## Accessibility
- Section titles are headings for screen readers. The name under an icon is not read twice (the button already carries it). Jump buttons are ordinary buttons, reached with the arrows; RTL as the rest. Strings in 5 languages.

## Tests
- UI test `iconsSayTheirNamesAndSettingsJumpToASection`: the name under a focused icon, the jump to "About" and back to "Films and accounts", a heading for every section.

## Not checked
- On a real TV. The screenshots from CI will show the new Settings.
