# Connecting a Stremio account (13.11.2026)

**Approved:** everything, including "My list". Add-ons are left out (explained to the owner), and watching stays in Stremio.

**Implemented (13.11.2026), version 0.5.4.**
- **The Link path is verified live:** `https://link.stremio.com/api/v2/create?type=Create` returns `{"result":{"code","link","qrcode"}}`. Until approval, `read` returns `{"error":{"code":101}}`. An invalid key on `api.strem.io` returns `{"error":{"code":1}}`, with HTTP 200.
- **Only films SCARY knows take part in the sync.** A comedy in the Stremio library doesn't enter "My list", and isn't removed from Stremio either.
- **Watched:** from Stremio to SCARY, every film watched there. From SCARY to Stremio, only films already in the Stremio library.
- **Not tested against a real account:** the exact response for an approved code, and the response to `getUser`. The connection is accepted when Stremio doesn't return an error.

**The request:** "add login to Stremio in the app".

## What was checked (stremio-core, Stremio's open-source code, MIT license)
- **Login without a password on the TV ("Link"):**
  - `GET https://link.stremio.com/…/v2/create?type=Create` returns `code`, `link` and `qrcode`.
  - The viewer opens the link on their phone (or scans the code) and approves.
  - `…/v2/read?type=Read&code=…` returns `authKey`.
  - **Requires verification:** the exact path prefix (`/api/` or not). I will confirm it with one live request before building.
- **Account and library** (`https://api.strem.io/api/…`, POST with the `authKey`):
  - `getUser`: the user's details.
  - `logout`: invalidates the key on Stremio's side.
  - `datastoreGet`, collection `libraryItem`: the library. Each item has:
    - `_id` (the IMDb id, e.g. `tt0081505`), `name`, `type` (movie or series), and `removed`;
    - `state.timeOffset` and `state.duration`: where the viewer stopped and the length;
    - `state.timesWatched` and `flaggedWatched`: whether it was watched;
    - `_mtime`: the last change.
  - `datastorePut`: adds to the library or updates it (for example adding a film, or marking it watched).
- **Unofficial:** this is not a published public API; it is what Stremio's own apps use. **Requires verification against Stremio's terms of use** before publishing outside the private repository.

## What I propose to build
1. **Connecting in Settings → Stremio**, like Real-Debrid and TMDB:
   - a code and a QR; the viewer approves on the phone;
   - "Disconnect" invalidates the key on Stremio's side and deletes it from the TV.
2. **"Continue in Stremio" on the home screen:**
   - films and series that were stopped in the middle in Stremio, with a progress bar;
   - OK opens the film's page in SCARY, and "Watch in Stremio" continues it there (Stremio resumes it itself).
3. **Two-way sync:**
   - "Watched" in Stremio is marked as watched in SCARY;
   - **"My list" ↔ the Stremio library:** adding a film to "My list" in SCARY adds it to the Stremio library, and the reverse. *(Optional. Choose below.)*
4. **On a film's page:** "In your Stremio library", and where the viewer stopped there (for example "Stopped at 1:12:40").

## What I don't propose (and why)
- **Add-ons, streams and torrents:** SCARY will not read the account's add-ons and will not fetch links to play from them. That would turn SCARY into a source search for copyrighted films, which is the same line drawn for Real-Debrid. Playback stays in Stremio, as today.
- **Email and password:** not typed into SCARY. Only the link login, so the password never passes through the TV.

## Security and privacy
- **The `authKey`:**
  - is stored in the existing SecretStore (Android Keystore);
  - is never logged and not backed up;
  - is deleted on disconnect.
- **Requests only to** `api.strem.io` and `link.stremio.com`, over HTTPS. Every response is checked as untrusted:
  - IMDb ids are validated;
  - names are cleaned;
  - limits apply (up to 2,000 items per library).
- **What leaves the TV:**
  - to Stremio: the `authKey`, the IP address, and the library changes the viewer makes ("My list" and watched);
  - to SCARY: nothing.
- **Parental lock:** the "Continue in Stremio" row is subject to the lock, like every film (by fear rating, and with a PIN when a film has no rating). It doesn't appear on Family night.
- **Documents:** the privacy policy gets a Stremio row (**requires legal review**), and the terms of use mention connecting to a third-party service.

## Accessibility
- The code is shown large and read out letter by letter.
- The QR has a description for screen readers.
- The progress on a card is part of the card's description ("Stopped at 1:12:40 of 2:05:00").
- Everything works with the remote, RTL included.

## Technology
- **Hand-written code, like the Real-Debrid and TMDB clients:**
  - `core/stremio/StremioApi.kt`: request building, hostile parsing, and conversion to SCARY's `Movie` by IMDb id (Cinemeta);
  - `data/StremioRepository.kt`: login, polling, the library, a 5-minute cache, and a rate limit.
- **No new library** (OkHttp and kotlinx.serialization already exist).

## Stages
1. **Verification and core:** one live check of the Link path; the client; parsing and tests with fake responses.
2. **App:** QR login, encrypted storage, the "Continue in Stremio" row, watched sync, and film page details.
3. **"My list" ↔ library** (if approved).
4. **Finish:** strings in 5 languages, documents, UI tests with a fake server, CI, and version 0.5.4.

## Risks
- **The API is unofficial:** Stremio can change it without notice. If something breaks, SCARY falls back to "not connected" and keeps working as today.
- **Terms of use:** whether using the API outside the official apps is allowed **requires verification against Stremio's terms**.
- **Many changes to the library at once:** syncing could overwrite changes made in Stremio. The rule: the latest change wins, by `_mtime`.
