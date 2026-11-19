# A menu on "Continue watching" cards: watched it, and out of the row (19.11.2026)

**The request (as understood):** "An option that when you are watching something, you can stand on the film, mark it as watched and remove it from the watching list."

## What existed (checked in the code)
- **"Continue watching" (the viewer's own files):** holding OK took the card out at once. Marking the film as watched was only on the film's page.
- **"Continue in Stremio":** holding OK added the film to My list. A card could not be taken out of this row at all, and stayed until the film was finished in Stremio.

## What was done (version 0.5.17)
- **Holding OK on a card of either row opens a short menu:**
  - **"Watched it: mark as watched and remove from this row"** (when the card is a film SCARY knows);
  - **"Only remove from this row"**;
  - **"My list: add or remove"**;
  - **Cancel**, and a line saying what the removal does.
- **A file of the viewer's own:** its saved spot is forgotten, as before; the file stays in the account.
- **An item from the Stremio account:** it is hidden in SCARY only (`core/stremio/ContinueDismissals`, kept on the TV, up to 200). **Nothing is written to the Stremio account**: the item stays in Stremio's own "Continue watching". If it is played again in Stremio afterwards, it comes back to SCARY's row.
- A film marked as watched here is marked the same way as on its page (the same setting), so the rows, "Hide watched" and "Your year in fear" follow. The connected TMDB and Stremio accounts read that setting at their next sync; **not checked** against real accounts.

## Why Stremio's own list is not changed
- Taking an item out of Stremio's "Continue watching" means writing to its library state through an interface that is not documented. SCARY's sync writes only what was verified (adding and removing from the library, marking as watched). **Requires verification** before SCARY changes anything else there.

## Privacy
- New on the TV only: which Stremio items were taken out of the row, and when (IMDb id and a time). Not backed up, not sent anywhere. `docs/PRIVACY.md` updated.

## Accessibility
- The menu is a dialog with ordinary buttons (arrows and OK, RTL), the film's title as its heading, and the note read with it. Strings in 5 languages.

## Tests
- `StremioTest`: taken out, back after being played again, damaged storage, the limit.
- UI test (`PlayerTest`): holding a "Continue watching" card opens the menu; a file with no film has no "Watched it"; "Only remove" takes the card out and forgets the spot.

## Not checked
- On a real TV, and with a real Stremio account (the Stremio row's menu is covered by the core test and the shared menu, not by a UI test).
