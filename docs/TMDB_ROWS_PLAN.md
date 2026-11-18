# More films from TMDB, with the viewer's own key (18.11.2026)

**The request:** "Pull from TMDB." Read as: the wider and fresher choice of films should come from TMDB (the viewer's own key), not only from Cinemeta.

## What existed (checked in the code)
- With a key: **4 rows** from TMDB's discover (New horror, Top rated horror, Thrillers and mysteries, Coming soon), **20 films each**, read once a day; each goes on by itself while the viewer scrolls.
- TMDB's rows already come **first** on the home screen, before Cinemeta's; a film shows once, in the first row that reaches it.
- TMDB's lists carry no IMDb id, which SCARY needs for every film (its page, Stremio, subtitles): one more request per film, remembered on the TV afterwards.

## What was done (version 0.5.15)
- **Nine more rows**, each a fixed filter that TMDB documents (checked against its official API description: `with_genres`, `primary_release_date.gte/lte`, `vote_count.gte/lte`, `vote_average.gte`, `sort_by`):
  - Just released (the last two months), Most popular horror, New thrillers, Hidden gems (rated 6.8 and up by 150 to 1,500 voters);
  - Horror of the 70s, 80s, 90s, 2000s and 2010s (the best known first).
- **Three pages of every row** at a refresh: 60 films a row instead of 20; up to about 780 films in 13 rows before any scrolling (fewer after films in Arabic or Indian languages are left out, and those shown once).
- **Fresher:** the rows are read three times a day and from Settings → "Refresh films now", as Cinemeta's; the library's translations stay once a day.
- **Steadier:**
  - a row whose page failed keeps its previous films;
  - the id lookups share one queue of four requests, so a first refresh (hundreds of lookups) is not answered "too many requests" with films going missing;
  - ids are written to the TV once per page, not once per film, and scrolling a row uses the ids already known (up to 20,000 kept).
- A cache of the older shape is read again at once after the update (rows version 3).

## Not done, and why
- **Rows by subject** (witches, zombies, found footage…) need TMDB's keyword ids, which cannot be verified without a key; those rows go on from Cinemeta, as before.
- **"Trending this week":** TMDB's trending list mixes every genre and its documented form has no pages; after keeping horror, a handful of films would be left.
- **Series:** TMDB has no horror genre for television.
- **Not run against TMDB from here** (no key): the addresses are tested against the documented filters; what each row returns was not seen.

## Privacy and security
- To TMDB, with the viewer's key: the fixed filters of each row (a genre, dates such as "the last two months" or a decade, vote counts), the language and the IP. Nothing the viewer typed. More requests than before: about 39 pages per refresh, and one lookup for each film not seen before. docs/PRIVACY.md updated.
- The key stays encrypted on the TV and goes only to TMDB, as before.

## Tests
- `TmdbTest`: the new rows' addresses and filters, a title in every language, the rows' own age. All core tests.
