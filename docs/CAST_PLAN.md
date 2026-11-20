# A film's cast, and an actor's films (20.11.2026)

**The request:** "Show the actors of a film, and let me press one to see the list of their films."

## What exists
A film's page shows one line of text, "Starring: A, B, C" (up to four names). The names cannot be pressed. SCARY holds a cast (up to six names) for films whose details came from Cinemeta: the library's films once their details are fetched, and every film of the online rows. The bundled catalog itself carries no cast, so with online data off there are no names to show.

## What is built (version 0.6.1)
1. **On a film's page:** a row "Cast" under the action buttons, each actor a word that takes the focus. OK opens that actor's films.
2. **"Films with <actor>":** a screen with a grid of posters.
   - **Without a TMDB key:** the films SCARY holds whose cast names the actor (the library, the rows, the films kept). Worked out on the TV; nothing is asked online.
   - **With the viewer's TMDB key:** the actor's whole filmography from TMDB is added, limited to SCARY's shelves (horror, thriller, mystery), newest first. The screen says which of the two it is showing.
3. **The same rules as everywhere:** films the parental lock covers show locked; on family night only the library is searched and TMDB is not asked; films kept off the shelves (the origin rule) stay off.

## What is asked of TMDB (only with the viewer's own key)
All in TMDB's official API description:
- `/3/search/person?query=<name>`: the actor's name, to find their id. Only a result whose name is the same, letter for letter (case and accents aside), is taken; with none, nothing is guessed.
- `/3/person/{id}/movie_credits`: their films.
- `/3/movie/{id}/external_ids` for each film this TV has not asked about before (kept on the TV afterwards, as with the other TMDB rows).

Nothing about the viewer is sent: a name that is printed on the film's own page, and ids of films.

## Threat model (short)
| What | Risk | Control |
|---|---|---|
| The actor's name in a request | Injection into the address | The name is checked (2 to 80 characters, no control characters) and URL-encoded; the host is fixed to TMDB's |
| TMDB's answer | A malformed or hostile answer | Parsed without trusting its shape; adult titles left out; off-theme films left out; at most 60 films |
| A namesake | Another person's films under this name | Only an exact name match, and among those the best known actor. A wrong match is possible and is said in the limits |

## Privacy
- Without a key nothing new leaves the TV.
- With a key, TMDB sees which actor's name was looked up, from the TV's IP address, under the viewer's own key. It is added to the privacy document's data inventory.
- No photographs of actors are shown in this version (they would be images of real people loaded from TMDB; left for a later decision).

## Accessibility
- Each name is a button with the actor's name as its label; arrows and OK; the same focus outline as everywhere.
- The actor's screen has a heading, a status line read politely when the list changes, and an empty state that says what to do.
- Names are shown as written (Latin or otherwise) with text direction taken from the name itself.

## Files
- **core:** `Similar.byActor` (Curation.kt), `Tmdb.personSearchUrl`, `parsePersonId`, `personCreditsUrl`, `parsePersonCredits`; tests in `CurationTest` and `TmdbTest`.
- **app:** `TmdbRepository.personFilms`, `HomeViewModel.openPerson` and `person`, `ui/person/PersonScreen.kt`, the cast row on `DetailsScreen`, `Screen.Person`, strings in five languages, a UI test.

## Limits
- **Without a key the list is only what SCARY holds:** an actor with one film in the library shows one film.
- **Names, not identities:** two actors with the same name are one name here; with a key, the best known one is taken.
- **Written from TMDB's API description,** not from a live answer (no key in the workspace): to verify on a TV with a key.
