#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
# Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
# Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.
"""
Builds app/src/main/assets/index.json: every well-known horror film on Wikidata (CC0), for search, the decade rows,
"More like this" and Ask SCARY. Run on a developer machine, never on the TV:

    python3 -I tools/catalog/build_index.py            # fetch, check, write
    python3 -I tools/catalog/build_index.py --check    # only check the file already there

"Well-known" means at least MIN_SITELINKS Wikipedias have an article on it; IMDb (through Cinemeta) must call it
horror too, which drops the films Wikidata mislabels.
For each film: its IMDb id, its title in SCARY's five languages where Wikidata has one, its year, its director, and
the subgenres SCARY has rows for. No fear level and no synopsis: those are never made up (the app loads the synopsis
and poster from Cinemeta when the film is opened, as for its other online rows). Films already in the hand-picked
catalog or the series list are left out.
"""
import datetime
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "app/src/main/assets/index.json")
CATALOG = os.path.join(ROOT, "app/src/main/assets/catalog.json")
SERIES = os.path.join(ROOT, "app/src/main/assets/series.json")
ENDPOINT = "https://query.wikidata.org/sparql"
CINEMETA = "https://v3-cinemeta.strem.io/meta/movie/{}.json"
CACHE = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".cache", "cinemeta-genres.json")
USER_AGENT = "SCARY-catalog-builder/1.0 (https://github.com/diamondrubinny/Scary)"
MIN_SITELINKS = 5
LANGS = ["en", "he", "ru", "pt", "ar"]
IMDB = re.compile(r"^tt\d{7,8}$")
MAX_TITLE = 120
MAX_NAME = 80
GENRE = re.compile(r"^[A-Za-z][A-Za-z -]{1,23}$")
# Never on SCARY's shelves, as in core's Cinemeta.OFF_THEME_GENRES: documentaries and titles for children.
OFF_THEME = {"Documentary", "Family", "Animation", "Kids"}
EXCLUDED = set()  # IMDb ids of films Wikidata files under horror by mistake; add as they are found.

# Horror genres a film must be filed under directly (checked by label on Wikidata, 8 Nov 2026). Following "subclass of
# horror" instead let in action and science fiction films (Gravity, John Wick) through broad genre chains.
HORROR_GENRES = {
    "Q200092": "horror film", "Q853630": "slasher film", "Q1342372": "monster film", "Q224700": "comedy horror",
    "Q108466999": "comedy horror film", "Q2137852": "vampire film", "Q3072049": "zombie film", "Q31888058": "ghost film",
    "Q10663882": "science fiction horror film", "Q909586": "splatter film", "Q43911809": "supernatural horror film",
    "Q109629396": "psychological horror film", "Q604725": "psychological horror fiction", "Q18355406": "satanic film",
    "Q3272147": "found footage film", "Q1747837": "zombie comedy", "Q109626272": "natural horror film",
    "Q102260466": "body horror film", "Q5258881": "werewolf film", "Q114413232": "gothic horror film",
    "Q1332055": "giallo", "Q1723850": "cannibal film", "Q127738900": "teen horror film", "Q123291714": "religious horror",
    "Q12049743": "romantic horror film", "Q108280930": "horror western",
}

# Wikidata genre or main-subject labels (English, lower case, matched as substrings) -> SCARY subgenre.
SUBGENRES = [
    ("slasher", "slasher"),
    ("giallo", "giallo"),
    ("witch", "witch"),
    ("lovecraft", "cosmic"),
    ("cosmic horror", "cosmic"),
    ("folk horror", "folk"),
    ("found footage", "found_footage"),
    ("zombie", "zombie"),
    ("vampire", "vampire"),
    ("werewolf", "creature"),
    ("monster film", "creature"),
    ("kaiju", "creature"),
    ("natural horror", "animal"),
    ("ghost film", "ghost"),
    ("haunted house", "haunted_house"),
    ("possession", "possession"),
    ("exorcism", "possession"),
    ("body horror", "body"),
    ("science fiction horror", "sci_fi"),
    ("comedy horror", "comedy"),
    ("zombie comedy", "comedy"),
    ("gothic", "gothic"),
    ("home invasion", "home_invasion"),
    ("psychological horror", "psychological"),
    ("psychological thriller", "psychological"),
    ("supernatural horror", "supernatural"),
    ("survival film", "survival"),
    ("crime thriller", "crime"),
    ("mystery film", "mystery"),
]
ALLOWED_SUBGENRES = sorted({s for _, s in SUBGENRES})


def sparql(query, attempts=4):
    data = urllib.parse.urlencode({"query": query, "format": "json"}).encode()
    for attempt in range(attempts):
        try:
            request = urllib.request.Request(ENDPOINT, data=data, headers={"User-Agent": USER_AGENT, "Accept": "application/sparql-results+json"})
            with urllib.request.urlopen(request, timeout=180) as response:
                return json.load(response)["results"]["bindings"]
        except Exception as error:  # Wikidata's query service times out or rate-limits now and then.
            if attempt == attempts - 1:
                raise
            print(f"  retrying after {error}", file=sys.stderr)
            time.sleep(5 * (attempt + 1))


def qid(uri):
    return uri.rsplit("/", 1)[-1]


def clean(text, limit):
    """Plain one-line text: no control or bidi-override characters, trimmed, or None."""
    if not text:
        return None
    text = "".join(c for c in text if c.isprintable() and c not in "‪‫‬‭‮⁦⁧⁨⁩")
    text = " ".join(text.split())
    return text if 0 < len(text) <= limit else None


# "King Kong (film, 1976)", "קינג קונג (סרט, 1976)": Wikipedia's disambiguation, not part of the title.
DISAMBIGUATION = re.compile(r"\s*\((?:[^()]*\b(?:film|movie|סרט|фильм|filme|فيلم)\b[^()]*|\d{4})\)$", re.IGNORECASE)


def title_of(text):
    title = clean(text, MAX_TITLE)
    if title:
        title = DISAMBIGUATION.sub("", title).strip() or None
    return title


def chunks(items, size):
    for i in range(0, len(items), size):
        yield items[i:i + size]


def fetch():
    films = {}
    rows = sparql(f"""
        SELECT ?f ?imdb ?s (MIN(YEAR(?date)) AS ?year) WHERE {{
          VALUES ?genre {{ {" ".join("wd:" + q for q in HORROR_GENRES)} }}
          ?f wdt:P31 wd:Q11424; wdt:P136 ?genre; wdt:P345 ?imdb; wikibase:sitelinks ?s.
          FILTER(?s >= {MIN_SITELINKS})
          OPTIONAL {{ ?f wdt:P577 ?date. }}
        }} GROUP BY ?f ?imdb ?s""")
    for row in rows:
        imdb = row["imdb"]["value"]
        if not IMDB.match(imdb):
            continue
        year = int(row["year"]["value"]) if "year" in row else None
        key = qid(row["f"]["value"])
        # A film with two IMDb ids (a re-release) keeps the first; the most-linked one wins duplicates below.
        films.setdefault(key, {"imdb": imdb, "p": int(row["s"]["value"]), "y": year, "t": {}, "d": None, "s": set()})
    print(f"{len(films)} films with at least {MIN_SITELINKS} Wikipedias", file=sys.stderr)
    keys = list(films)
    lang_filter = ", ".join(f'"{lang}"' for lang in LANGS + ["pt-br"])
    for batch in chunks(keys, 250):
        values = " ".join(f"wd:{k}" for k in batch)
        for row in sparql(f"""
            SELECT ?f ?l (LANG(?l) AS ?lang) WHERE {{
              VALUES ?f {{ {values} }} ?f rdfs:label ?l. FILTER(LANG(?l) IN ({lang_filter}))
            }}"""):
            lang = row["lang"]["value"]
            lang = "pt" if lang == "pt-br" else lang
            film = films[qid(row["f"]["value"])]
            title = title_of(row["l"]["value"])
            # pt-br is preferred over pt where both exist (SCARY speaks Brazilian Portuguese).
            if title and (lang not in film["t"] or row["lang"]["value"] == "pt-br"):
                film["t"][lang] = title
        for row in sparql(f"""
            SELECT ?f (SAMPLE(?dl) AS ?director) WHERE {{
              VALUES ?f {{ {values} }} ?f wdt:P57 ?d. ?d rdfs:label ?dl. FILTER(LANG(?dl) = "en")
            }} GROUP BY ?f"""):
            films[qid(row["f"]["value"])]["d"] = clean(row["director"]["value"], MAX_NAME)
        for row in sparql(f"""
            SELECT ?f ?gl WHERE {{
              VALUES ?f {{ {values} }} {{ ?f wdt:P136 ?g. }} UNION {{ ?f wdt:P921 ?g. }}
              ?g rdfs:label ?gl. FILTER(LANG(?gl) = "en")
            }}"""):
            label = row["gl"]["value"].lower()
            for needle, subgenre in SUBGENRES:
                if needle in label:
                    films[qid(row["f"]["value"])]["s"].add(subgenre)
        time.sleep(1)
    return films


def cinemeta_genres(imdb_ids):
    """
    IMDb's genres for each film, through Cinemeta (the same public service the app uses). Wikidata files some
    well-known non-horror films under horror (Gravity, John Wick: Chapter 2); a film stays only if IMDb calls it
    horror too. Also proves each IMDb id exists. Cached between runs (tools/catalog/.cache, not committed).
    """
    cache = {}
    if os.path.exists(CACHE):
        with open(CACHE, encoding="utf-8") as handle:
            cache = json.load(handle)

    def one(imdb):
        for attempt in range(3):
            try:
                request = urllib.request.Request(CINEMETA.format(imdb), headers={"User-Agent": USER_AGENT})
                with urllib.request.urlopen(request, timeout=30) as response:
                    meta = json.load(response).get("meta") or {}
                genres = meta.get("genre") or meta.get("genres") or []
                return imdb, [g for g in genres if isinstance(g, str)][:10]
            except urllib.error.HTTPError as error:
                if error.code == 404:
                    return imdb, []
                time.sleep(2 * (attempt + 1))
            except Exception:
                time.sleep(2 * (attempt + 1))
        return imdb, None  # Not reachable now: not cached, so the next run asks again.

    missing = [i for i in imdb_ids if i not in cache]
    print(f"Cinemeta: {len(imdb_ids) - len(missing)} cached, {len(missing)} to ask", file=sys.stderr)
    with ThreadPoolExecutor(max_workers=6) as pool:
        for done, (imdb, genres) in enumerate(pool.map(one, missing), 1):
            if genres is not None:
                cache[imdb] = genres
            if done % 500 == 0:
                print(f"  {done}/{len(missing)}", file=sys.stderr)
    os.makedirs(os.path.dirname(CACHE), exist_ok=True)
    with open(CACHE, "w", encoding="utf-8") as handle:
        json.dump(cache, handle)
    return cache


def curated_ids():
    ids = set()
    for path in (CATALOG, SERIES):
        with open(path, encoding="utf-8") as handle:
            data = json.load(handle)
        for item in data.get("movies", data.get("series", [])):
            if item.get("imdbId"):
                ids.add(item["imdbId"])
    return ids


def build(films):
    skip = curated_ids() | EXCLUDED
    this_year = datetime.date.today().year
    by_imdb = {}
    for film in films.values():
        if film["imdb"] in skip or "en" not in film["t"] or not film["y"] or not 1895 <= film["y"] <= this_year:
            continue
        current = by_imdb.get(film["imdb"])
        if current is None or film["p"] > current["p"]:
            by_imdb[film["imdb"]] = film
    genres = cinemeta_genres(sorted(by_imdb))
    unknown = [i for i in by_imdb if i not in genres]
    if len(unknown) > len(by_imdb) // 20:
        sys.exit(f"Cinemeta did not answer for {len(unknown)} films; run again later.")
    not_horror = [i for i in by_imdb if i in genres and "Horror" not in genres[i]]
    print(f"Left out {len(not_horror)} films IMDb does not call horror, {len(unknown)} not checked", file=sys.stderr)
    off_theme = [i for i in by_imdb if set(genres.get(i, [])) & OFF_THEME]
    print(f"Left out {len(off_theme)} for children or documentaries (as the app's online rows do)", file=sys.stderr)
    out = []
    for film in sorted(by_imdb.values(), key=lambda f: (-f["p"], f["imdb"])):
        imdb_genres = genres.get(film["imdb"], [])
        if "Horror" not in imdb_genres or set(imdb_genres) & OFF_THEME:
            continue
        entry = {"imdb": film["imdb"], "t": {lang: film["t"][lang] for lang in LANGS if lang in film["t"]}, "y": film["y"], "p": film["p"]}
        # IMDb's other genres ("Mystery", "Sci-Fi"), as Cinemeta's rows carry them: "More like this" reads them.
        other = [g for g in imdb_genres if g != "Horror" and GENRE.match(g)][:6]
        if other:
            entry["g"] = other
        if film["d"]:
            entry["d"] = film["d"]
        if film["s"]:
            entry["s"] = sorted(film["s"])
        out.append(entry)
    return {
        "version": 1,
        "source": "Wikidata (CC0 1.0), https://www.wikidata.org",
        "generated": datetime.date.today().isoformat(),
        "minSitelinks": MIN_SITELINKS,
        "films": out,
    }


def check(index):
    """The same rules core's IndexParser enforces, so a bad file never ships."""
    films = index["films"]
    assert index["version"] == 1 and len(films) >= 1000, "too few films"
    seen = set()
    skip = curated_ids()
    for film in films:
        imdb = film["imdb"]
        assert IMDB.match(imdb), imdb
        assert imdb not in seen, f"duplicate {imdb}"
        assert imdb not in skip, f"{imdb} is already hand-picked"
        seen.add(imdb)
        assert set(film["t"]) <= set(LANGS) and film["t"].get("en"), imdb
        assert all(title_of(t) == t for t in film["t"].values()), imdb
        assert 1895 <= film["y"] <= datetime.date.today().year, imdb
        assert "d" not in film or clean(film["d"], MAX_NAME) == film["d"], imdb
        assert set(film.get("s", [])) <= set(ALLOWED_SUBGENRES), imdb
        assert all(GENRE.match(g) and g not in OFF_THEME for g in film.get("g", [])), imdb
    counts = {lang: sum(lang in f["t"] for f in films) for lang in LANGS}
    subgenres = {s: sum(s in f.get("s", []) for f in films) for s in ALLOWED_SUBGENRES}
    decades = {}
    for f in films:
        decades[f["y"] // 10 * 10] = decades.get(f["y"] // 10 * 10, 0) + 1
    print(f"OK: {len(films)} films. Titles: {counts}", file=sys.stderr)
    print(f"Subgenres: {subgenres}", file=sys.stderr)
    print(f"Decades: {dict(sorted(decades.items()))}", file=sys.stderr)


def main():
    if "--check" in sys.argv:
        with open(OUT, encoding="utf-8") as handle:
            check(json.load(handle))
        return
    index = build(fetch())
    check(index)
    with open(OUT, "w", encoding="utf-8") as handle:
        json.dump(index, handle, ensure_ascii=False, separators=(",", ":"))
        handle.write("\n")
    print(f"Wrote {OUT} ({os.path.getsize(OUT) // 1024} KB)", file=sys.stderr)


if __name__ == "__main__":
    main()
