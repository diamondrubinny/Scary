#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
# Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
# Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.
"""
Builds SCARY's film feed (docs/CATALOG_REBUILD_PLAN.md): every film and every home row, from public data only, with
nothing written by hand. A weekly job runs it and publishes the file; the TV downloads it, so the library changes
without an app update. Run on a developer machine or in CI, never on the TV:

    python3 -I tools/catalog/build_index.py --with-curated --out BASE.json    # every well-known horror film (Wikidata)
    python3 -I tools/catalog/build_feed.py --base BASE.json                   # the feed, from that and Cinemeta
    python3 -I tools/catalog/build_feed.py --check                            # only check the file already there
    ... --previous OLD.json   # the feed published before: nothing leaves quietly, and a shrunken feed is refused

Where films come from:
  * the base: well-known horror films on Wikidata (CC0) that IMDb calls horror too (build_index.py);
  * new films: Cinemeta's catalogs of this year and last year. A film a few weeks old has no five Wikipedia articles
    yet, which is why the Wikidata list alone never showed anything new;
  * popular now: Cinemeta's own horror catalog, in its order.
For each film Cinemeta is asked once for IMDb's rating, release date and country (the same public service the app
uses; no key). No synopsis and no fear level: the app loads the synopsis when the film is opened, and a fear level is
never made up.

A row is a rule over those films, not a list anyone keeps. A film is in a row only when the sources agree (see ROWS),
and tools/catalog/overrides.json takes a film out of one row or all of them when a rule gets it wrong.
"""
import datetime
import importlib.util
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(ROOT, "app/src/main/assets/feed.json")
OVERRIDES = os.path.join(HERE, "overrides.json")
CACHE = os.path.join(HERE, ".cache", "cinemeta-feed.json")
USER_AGENT = "SCARY-catalog-builder/1.0 (https://github.com/diamondrubinny/Scary)"
META = "https://v3-cinemeta.strem.io/meta/movie/{}.json"
TOP = "https://v3-cinemeta.strem.io/catalog/movie/top/genre=Horror{}.json"
YEAR = "https://cinemeta-catalogs.strem.io/year/catalog/movie/year/genre={}{}.json"
# Beside horror films: the most popular thrillers, and horror series, each in a row of its own.
THRILLERS = "https://v3-cinemeta.strem.io/catalog/movie/top/genre=Thriller{}.json"
SERIES = "https://v3-cinemeta.strem.io/catalog/series/top/genre=Horror{}.json"
THRILLER_PAGES = 30
SERIES_PAGES = 8
PAGE = 50
YEAR_PAGES = 40  # A year's mixed catalog: 2,000 films, of which a few dozen are horror.
# Cinemeta's own horror catalog, most popular first: all of it (it ends near 1,750 films) joins the feed, since
# Wikidata's horror list alone misses famous films; its first pages are the "popular now" row.
TOP_PAGES = 45
POPULAR_ROW = 150
VERSION = 1
LANGS = ["en", "he", "ru", "pt", "ar"]
IMDB = re.compile(r"^tt\d{7,8}$")
GENRE = re.compile(r"^[A-Za-z][A-Za-z -]{1,23}$")
MAX_TITLE = 120
MAX_NAME = 80
MAX_ABOUT = 900
MAX_ROW = 80
MIN_ROW = 12
MAX_FILMS = 12_000
# The feed may not shrink by more than this against the one published before: a bad day at a source is not published.
MAX_DROP = 0.05
ADULT = re.compile(r"(?<![^\W_])(sex|sexy|porn|porno|xxx|erotic|erotica|erotik|érotique|hentai)(?![^\W_])", re.IGNORECASE)


def load_index_tools():
    """build_index.py's lists and helpers (one copy of the hidden countries and the off-theme genres)."""
    spec = importlib.util.spec_from_file_location("build_index", os.path.join(HERE, "build_index.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


INDEX = load_index_tools()


def get_json(url, attempts=3):
    for attempt in range(attempts):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(request, timeout=40) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            if error.code == 404:
                return {}
            time.sleep(2 * (attempt + 1))
        except Exception:
            time.sleep(2 * (attempt + 1))
    return None


def text_list(value):
    """Cinemeta gives a list, or (in its year catalog) a list written out as text."""
    if isinstance(value, list):
        return [v for v in value if isinstance(v, str)]
    if isinstance(value, str) and value.startswith("["):
        return re.findall(r"'([^']+)'|\"([^\"]+)\"", value) and [a or b for a, b in re.findall(r"'([^']+)'|\"([^\"]+)\"", value)]
    return [value] if isinstance(value, str) and value else []


def details(meta):
    """What the feed keeps of one Cinemeta film; every field is checked, since the answer is not SCARY's."""
    genres = [g for g in text_list(meta.get("genre") or meta.get("genres")) if GENRE.match(g)][:10]
    rating = None
    try:
        value = float(meta.get("imdbRating") or 0)
        rating = round(value, 1) if 0 < value <= 10 else None
    except (TypeError, ValueError):
        pass
    released = meta.get("released") if isinstance(meta.get("released"), str) else ""
    released = released[:10] if re.match(r"^\d{4}-\d{2}-\d{2}", released) else ""
    year = None
    match = re.match(r"^(\d{4})", str(meta.get("year") or meta.get("releaseInfo") or ""))
    if match:
        year = int(match.group(1))
    directors = text_list(meta.get("director"))
    country = meta.get("country") if isinstance(meta.get("country"), str) else ""
    name = INDEX.title_of(meta.get("name")) if isinstance(meta.get("name"), str) else None
    description = meta.get("description") if isinstance(meta.get("description"), str) else ""
    minutes = re.match(r"^(\d{1,3})\s*min", str(meta.get("runtime") or ""))
    return {
        # The film's length in minutes ("112 min"), 0 when Cinemeta does not say.
        "m": int(minutes.group(1)) if minutes and 1 <= int(minutes.group(1)) <= 600 else 0,
        # What the film is about, for the rows' word rules only: it is never written to the feed.
        "o": " ".join(description.split())[:MAX_ABOUT],
        "g": genres, "r": rating, "rel": released, "y": year, "c": country[:120], "n": name,
        "d": INDEX.clean(directors[0], MAX_NAME) if directors else None,
    }


def cinemeta_details(imdb_ids, known):
    """Rating, release date and country for each film; cached between runs (tools/catalog/.cache, not committed)."""
    cache = {}
    if os.path.exists(CACHE):
        with open(CACHE, encoding="utf-8") as handle:
            cache = json.load(handle)
    cache.update(known)

    def one(imdb):
        answer = get_json(META.format(imdb))
        return imdb, None if answer is None else details(answer.get("meta") or {})

    missing = [i for i in imdb_ids if "m" not in cache.get(i, {})]
    print(f"Cinemeta details: {len(imdb_ids) - len(missing)} known, {len(missing)} to ask", file=sys.stderr)
    with ThreadPoolExecutor(max_workers=6) as pool:
        for done, (imdb, info) in enumerate(pool.map(one, missing), 1):
            if info is not None:
                cache[imdb] = info
            if done % 500 == 0:
                print(f"  {done}/{len(missing)}", file=sys.stderr)
    os.makedirs(os.path.dirname(CACHE), exist_ok=True)
    with open(CACHE, "w", encoding="utf-8") as handle:
        json.dump(cache, handle)
    return cache


def catalog(url_of, pages):
    """A Cinemeta catalog's films, in its own order, until it runs out. None when its first page could not be read."""
    out = []
    for page in range(pages):
        answer = get_json(url_of("" if page == 0 else f"&skip={page * PAGE}"))
        if answer is None:
            return None if page == 0 else out
        metas = answer.get("metas") or []
        # A page may come a little short of 50 (the popular catalog's first has 48): only an empty one is the end.
        if not metas:
            break
        out.extend(m for m in metas if isinstance(m, dict))
    return out


def on_shelf(info):
    """The app's own rules for an online film: horror, not for children, not a documentary, not a hidden country."""
    genres = set(info.get("g") or [])
    return "Horror" in genres and not genres & INDEX.OFF_THEME and not INDEX.hidden(info.get("c"))


def gather(base, today):
    """The films: the base, plus this year's and last year's horror from Cinemeta, plus what is popular now."""
    known = {}
    fresh, popular = [], []
    for year in (today.year, today.year - 1):
        metas = catalog(lambda extra, y=year: YEAR.format(y, extra), YEAR_PAGES)
        if metas is None:
            sys.exit(f"Cinemeta's {year} catalog did not answer; run again later.")
        for meta in metas:
            imdb = meta.get("imdb_id") or meta.get("id")
            if isinstance(imdb, str) and IMDB.match(imdb):
                known[imdb] = details(meta)
                fresh.append(imdb)
    top = catalog(lambda extra: TOP.format(extra), TOP_PAGES)
    if top is None:
        print("Cinemeta's popular horror catalog did not answer: that row is built from the base instead", file=sys.stderr)
    for meta in top or []:
        imdb = meta.get("imdb_id") or meta.get("id")
        if isinstance(imdb, str) and IMDB.match(imdb):
            known.setdefault(imdb, details(meta))
            popular.append(imdb)
    films = {film["imdb"]: dict(film) for film in base["films"]}
    # Films no rule finds and SCARY wants (overrides.json): Cinemeta lists three genres at most, and for Alien none of
    # them is horror.
    always = included()
    info = cinemeta_details(sorted(set(films) | set(popular) | always), known)
    wanted = []
    for imdb in dict.fromkeys(popular + fresh + sorted(always)):
        known_film = info.get(imdb)
        if imdb in films or not known_film or not (on_shelf(known_film) or imdb in always):
            continue
        name = known_film.get("n")
        if not name or ADULT.search(name) or not known_film.get("y") or not 1895 <= known_film["y"] <= today.year:
            continue
        wanted.append(imdb)
    # What Wikidata knows of them: titles in the other languages, how many Wikipedias, the director, the subgenres.
    described = {film["imdb"]: film for film in INDEX.by_imdb(wanted).values()}
    added = 0
    for imdb in wanted:
        known_film = info[imdb]
        wikidata = described.get(imdb, {})
        titles = {lang: title for lang, title in wikidata.get("t", {}).items() if lang in LANGS}
        # IMDb's own title, as Cinemeta has it, is the English one.
        titles["en"] = known_film["n"]
        film = {"imdb": imdb, "t": titles, "y": known_film["y"], "p": min(wikidata.get("p", 0), 500)}
        director = wikidata.get("d") or known_film.get("d")
        if director:
            film["d"] = director
        if wikidata.get("s"):
            film["s"] = sorted(wikidata["s"])
        films[imdb] = film
        added += 1
    print(f"{len(base['films'])} films from the base, {added} more from Cinemeta's horror and new-film catalogs", file=sys.stderr)
    out = []
    for imdb, film in films.items():
        known_film = info.get(imdb)
        if known_film is None:
            # Not reachable now: the film stays as the base has it, without a rating.
            known_film = {}
        elif not on_shelf(known_film) and imdb not in always:
            continue
        other = [g for g in (known_film.get("g") or film.get("g") or []) if g != "Horror" and GENRE.match(g)][:6]
        film.pop("g", None)
        if other:
            film["g"] = other
        if known_film.get("r"):
            film["r"] = known_film["r"]
        if known_film.get("rel"):
            film["rel"] = known_film["rel"]
        if known_film.get("m"):
            film["m"] = known_film["m"]
        out.append(film)
    about = {imdb: (info.get(imdb) or {}).get("o", "") for imdb in films}
    return out, [i for i in fresh if i in films], [i for i in popular if i in films], about


# A thriller for SCARY is a dark one: crime, mystery or drama, not a spy film or a caper (Casino Royale and Tenet led the
# first trial's row).
NOT_A_DARK_THRILLER = {"Action", "Adventure", "Comedy", "Sci-Fi", "Fantasy", "Romance", "Musical", "War", "Western", "Sport"}


def thriller_ok(info, today):
    genres = set(info.get("g") or [])
    return (
        "Thriller" in genres and "Horror" not in genres and genres & {"Crime", "Mystery", "Drama"}
        and not genres & NOT_A_DARK_THRILLER and not genres & INDEX.OFF_THEME and not INDEX.hidden(info.get("c"))
        and (info.get("r") or 0) >= 6.5
    )


def series_ok(info, today):
    # A series so new that nobody rated it yet is let in; an older one needs a fair rating.
    return on_shelf(info) and ((info.get("r") or 0) >= 6.5 or (not info.get("r") and (info.get("y") or 0) >= today.year - 1))


def extras(today, taken):
    """
    Thrillers and horror series: Cinemeta's most popular of each, in its order, with what Wikidata knows of them.
    Each film says what it is ("k"), so nothing mistakes a thriller or a series for a horror film. Returns the films
    and, for each kind, its row.
    """
    picked, rows = [], {}
    for kind, url, pages, fits in (("thriller", THRILLERS, THRILLER_PAGES, thriller_ok), ("series", SERIES, SERIES_PAGES, series_ok)):
        metas = catalog(lambda extra, u=url: u.format(extra), pages)
        if metas is None:
            print(f"Cinemeta's {kind} catalog did not answer: none this time", file=sys.stderr)
            continue
        rows[kind] = []
        for meta in metas:
            imdb = meta.get("imdb_id") or meta.get("id")
            if not isinstance(imdb, str) or not IMDB.match(imdb) or imdb in taken:
                continue
            info = details(meta)
            if not fits(info, today) or not info.get("n") or ADULT.search(info["n"]) or not info.get("y") or not 1895 <= info["y"] <= today.year:
                continue
            span = re.match(r"^\d{4}\s*[–-]\s*(\d{4})?", str(meta.get("releaseInfo") or "")) if kind == "series" else None
            taken.add(imdb)
            picked.append((kind, imdb, info, span))
            rows[kind].append(imdb)
    described = {film["imdb"]: film for film in INDEX.by_imdb([imdb for _, imdb, _, _ in picked]).values()}
    films = []
    for kind, imdb, info, span in picked:
        wikidata = described.get(imdb, {})
        titles = {lang: title for lang, title in wikidata.get("t", {}).items() if lang in LANGS}
        titles["en"] = info["n"]
        film = {"imdb": imdb, "t": titles, "y": info["y"], "p": min(wikidata.get("p", 0), 500), "k": kind}
        other = [g for g in info.get("g") or [] if g not in ("Horror", "Thriller") and GENRE.match(g)][:6]
        if other:
            film["g"] = other
        director = wikidata.get("d") or info.get("d")
        if director:
            film["d"] = director
        if info.get("r"):
            film["r"] = info["r"]
        if info.get("m"):
            film["m"] = info["m"]
        if span is not None:
            end = int(span.group(1)) if span.group(1) else None
            if end and info["y"] <= end <= 2100:
                film["e"] = end
            elif not end:
                film["on"] = True
        films.append(film)
    print(f"{len(rows.get('thriller', []))} thrillers and {len(rows.get('series', []))} series from Cinemeta", file=sys.stderr)
    return films, rows


def included():
    """IMDb ids overrides.json puts in the feed whatever the sources say of their genre."""
    if not os.path.exists(OVERRIDES):
        return set()
    with open(OVERRIDES, encoding="utf-8") as handle:
        return {i for i in json.load(handle).get("include", []) if IMDB.match(i)}


def overrides():
    if not os.path.exists(OVERRIDES):
        return set(), {}
    with open(OVERRIDES, encoding="utf-8") as handle:
        data = json.load(handle)
    everywhere = {i for i in data.get("exclude", []) if IMDB.match(i)}
    per_row = {row: {i for i in ids if IMDB.match(i)} for row, ids in data.get("notInRow", {}).items()}
    return everywhere, per_row


def known_enough(film, rating=5.5, wikipedias=5):
    """Not in a row unless people know the film and did not hate it."""
    return film.get("p", 0) >= wikipedias and film.get("r", 0) >= rating


def by_fame(film):
    return (-film.get("p", 0), -film.get("r", 0), film["imdb"])


def words(*parts):
    return re.compile(r"\b(?:" + "|".join(parts) + ")", re.IGNORECASE)


# The word rules, on a film's title and description: the same ones the app applies to its online rows (core
# Categories.kt, tuned there on Cinemeta's real catalogs). Keep the two in step.
SUPERNATURAL = [
    "ghosts?\\b", "ghostly", "demon", "possess", "exorcis", "poltergeist", "evil spirit", "supernatural", "satan", "the devil",
    "curse[ds]?", "occult", "malevolent (?:spirit|entity|force)", "evil entity",
]
HAUNTED = [
    "haunted (?:house|home|mansion|manor|hotel|estate)",
    "(?:house|home|mansion|manor|estate)\\b.{0,60}\\b(?:ghost|haunt|presence|spirit|paranormal|poltergeist)",
    "(?:ghost|haunt|presence|spirit|paranormal|poltergeist)\\w*\\b.{0,60}\\b(?:house|home|mansion|manor)",
]
# "Infected" and its kin are the feed's own: 28 Weeks Later never says "zombie".
ZOMBIES = ["zombie", "undead", "walking dead", "flesh-eating", "the dead (?:rise|return|walk|come back)", "infected", "rage virus", "outbreak"]
VAMPIRES = ["vampir", "dracula", "nosferatu", "bloodsuck", "blood-suck", "blood sucking"]
CREATURES = [
    "creature", "monster", "alien", "shark", "beast", "werewol", "kaiju", "crocodile", "alligator",
    "giant (?:spider|snake|insect|ant|worm)", "mutant", "sea creature", "predator\\b",
]
MONSTERS = words("werewol", "vampir", "zombie", "ghosts?\\b", "demon", "alien", "creature", "monster", "possess")


class Rule:
    """
    Who is in a subgenre row. Two sources are asked: Wikidata's subgenre labels ([labels]) and the words of the film's
    description ([says], unless [not_says]). A film both name comes first. A film only one names is in the row when
    that source has proved right for this row ([trust_labels], [trust_words]): checked row by row on the films
    themselves, 24 Nov 2026 (Wikidata files Rosemary's Baby and Carrie under monster films, but its vampire and
    found-footage labels are right; descriptions rarely say "found footage", but they do say "shark").
    """

    def __init__(self, labels, says, not_says=None, trust_labels=False, trust_words=False, rating=5.5, never_labels=(), never_genres=()):
        self.labels, self.says, self.not_says = set(labels), says, not_says
        self.trust_labels, self.trust_words, self.rating = trust_labels, trust_words, rating
        self.never_labels, self.never_genres = set(never_labels), set(never_genres)

    def films(self, films, about):
        both, one = [], []
        for film in films:
            if not known_enough(film, self.rating):
                continue
            labels = set(film.get("s", []))
            if labels & self.never_labels or set(film.get("g", [])) & self.never_genres:
                continue
            text = film["t"]["en"] + ". " + about.get(film["imdb"], "")
            labelled = bool(labels & self.labels)
            said = bool(self.says.search(text)) and not (self.not_says and self.not_says.search(text))
            if labelled and said:
                both.append(film)
            elif (labelled and self.trust_labels) or (said and self.trust_words):
                one.append(film)
        return [f["imdb"] for f in sorted(both, key=by_fame) + sorted(one, key=by_fame)]


# The home rows after the new and the popular films, in the order the home screen shows them. The app holds each
# row's name, in five languages, by its id (core FeedCatalog). A row keeps the id the app's own category of the same
# kind has (core Categories), so the app goes on filling it from Cinemeta when the viewer reaches its end.
SUBGENRE_ROWS = [
    ("slashers", Rule(
        ["slasher"],
        words("masked", "slasher", "machete", "summer camp", "serial killer", "killer (?:stalks|picks off|targets|terrori)",
              "picks? (?:them|off)", "stalked by", "knife-wielding", "murderous (?:psycho|maniac)", "maniac"),
        words(*SUPERNATURAL, "werewol", "vampir", "zombie", "alien", "creature", "monster"),
        trust_labels=True, never_genres=["Fantasy", "Musical", "Sci-Fi"])),
    ("supernatural", Rule(["ghost", "supernatural", "possession"], words(*SUPERNATURAL))),
    ("haunted_houses", Rule(["haunted_house"], words(*HAUNTED), words(*VAMPIRES), trust_labels=True, trust_words=True, never_labels=["vampire", "comedy"])),
    ("zombies", Rule(["zombie"], words(*ZOMBIES), words(*VAMPIRES))),
    ("vampires", Rule(["vampire"], words(*VAMPIRES), trust_labels=True)),
    ("creatures", Rule(["creature", "animal"], words(*CREATURES), words("vampir", "zombie"), trust_words=True, never_labels=["vampire", "zombie"])),
    ("psychological_horror", Rule(
        ["psychological"],
        words("psycholog", "grief", "griev", "madness", "sanity", "insanity", "paranoi", "delusion", "hallucinat", "trauma",
              "unravel", "her mind", "his mind", "losing (?:her|his) mind"),
        MONSTERS, rating=6.0)),
    ("found_footage", Rule(
        ["found_footage"],
        words("found footage", "footage", "camcorder", "video camera", "livestream", "live-stream", "webcam", "vlog", "documentary crew"),
        trust_labels=True)),
]


def build_rows(films, fresh, popular, about, today, more=None):
    everywhere, per_row = overrides()
    by_id = {f["imdb"]: f for f in films if f["imdb"] not in everywhere}
    year_ago = (today - datetime.timedelta(days=365)).isoformat()
    rows = []

    def add(row_id, ids):
        ids = [i for i in dict.fromkeys(ids) if i in by_id and i not in per_row.get(row_id, set())][:MAX_ROW]
        if len(ids) >= MIN_ROW:
            rows.append({"id": row_id, "films": ids})
        else:
            print(f"Row {row_id} has only {len(ids)} films and is left out", file=sys.stderr)

    # The horror films: a thriller or a series is in its own row only.
    everyone = [f for f in by_id.values() if "k" not in f]
    # New: out in the last twelve months (never one that is not out yet), in Cinemeta's order of popularity.
    add("new_nightmares", [i for i in fresh if i in by_id and year_ago <= by_id[i].get("rel", "") <= today.isoformat()])
    # Popular now: Cinemeta's order; without it, the best known films of the last five years.
    recent = sorted((f for f in everyone if f["y"] >= today.year - 5 and f.get("r", 0) >= 5.5), key=by_fame)
    add("popular", popular[:POPULAR_ROW] or [f["imdb"] for f in recent])
    best = [f for f in everyone if f.get("r", 0) >= 7.3 and f.get("p", 0) >= 15]
    add("max_fear", [f["imdb"] for f in sorted(best, key=lambda f: (-f.get("r", 0), -f.get("p", 0), f["imdb"]))])
    for row_id, rule in SUBGENRE_ROWS:
        add(row_id, rule.films(everyone, about))
    # Comedy: Wikidata's label and IMDb's own genre must both say so.
    funny = [f for f in everyone if "comedy" in f.get("s", []) and "Comedy" in f.get("g", []) and known_enough(f, 6.0)]
    add("horror_comedy", [f["imdb"] for f in sorted(funny, key=by_fame)])
    classics = [f for f in everyone if f["y"] <= 1980 and f.get("r", 0) >= 6.8 and f.get("p", 0) >= 15]
    add("classics", [f["imdb"] for f in sorted(classics, key=by_fame)])
    add("thrillers", (more or {}).get("thriller", []))
    add("series_must", (more or {}).get("series", []))
    return rows, everywhere


def keep_known(films, previous, base_ids):
    """
    Nothing leaves quietly: a film the feed published before held, and no source lists any more, stays as it was
    (Wikidata's genre statements are edited all the time). A film leaves only by overrides.json or the shelf rules,
    which were applied when it was first let in and are applied to it again by the next full base.
    """
    have = {f["imdb"] for f in films}
    kept = [f for f in previous.get("films", []) if f.get("imdb") not in have and f.get("imdb") not in base_ids]
    if kept:
        print(f"Kept {len(kept)} films no source lists any more: {', '.join(f['t']['en'] for f in kept[:8])}", file=sys.stderr)
    return films + kept


def check(feed):
    """The same rules core's FeedParser enforces, so a bad file is never published."""
    films, rows = feed["films"], feed["rows"]
    assert feed["version"] == VERSION and 1000 <= len(films) <= MAX_FILMS, f"{len(films)} films"
    assert re.match(r"^\d{4}-\d{2}-\d{2}$", feed["generated"]), feed["generated"]
    this_year = datetime.date.today().year
    seen = set()
    for film in films:
        imdb = film["imdb"]
        assert IMDB.match(imdb) and imdb not in seen, imdb
        seen.add(imdb)
        assert set(film["t"]) <= set(LANGS) and film["t"].get("en"), imdb
        assert all(INDEX.title_of(t) == t for t in film["t"].values()), imdb
        assert 1895 <= film["y"] <= this_year and 0 <= film["p"] <= 500, imdb
        assert "r" not in film or 0 < film["r"] <= 10, imdb
        assert "rel" not in film or re.match(r"^\d{4}-\d{2}-\d{2}$", film["rel"]), imdb
        assert "d" not in film or INDEX.clean(film["d"], MAX_NAME) == film["d"], imdb
        assert all(GENRE.match(g) and g != "Horror" for g in film.get("g", [])), imdb
        assert set(film.get("s", [])) <= {s for _, s in INDEX.SUBGENRES}, imdb
        assert film.get("k") in (None, "thriller", "series"), imdb
        assert "e" not in film or (film.get("k") == "series" and film["y"] <= film["e"] <= 2100), imdb
        assert film.get("on") in (None, True), imdb
        assert "m" not in film or 1 <= film["m"] <= 600, imdb
        assert set(film) <= {"imdb", "t", "y", "p", "g", "d", "s", "r", "rel", "m", "k", "e", "on"}, imdb
    assert 3 <= len(rows) <= 40, f"{len(rows)} rows"
    ids = [row["id"] for row in rows]
    assert len(set(ids)) == len(ids) and all(re.match(r"^[a-z][a-z_]{1,31}$", i) for i in ids), ids
    for row in rows:
        assert MIN_ROW <= len(row["films"]) <= MAX_ROW and len(set(row["films"])) == len(row["films"]), row["id"]
        assert set(row["films"]) <= seen, row["id"]
    print(f"OK: {len(films)} films, {len(rows)} rows: " + ", ".join(f"{r['id']} {len(r['films'])}" for r in rows), file=sys.stderr)


def report(feed, previous):
    """What changed since the feed published before, for a person to read in the job's log."""
    before = {f["imdb"]: f for f in previous.get("films", [])}
    after = {f["imdb"]: f for f in feed["films"]}
    added = [after[i] for i in after if i not in before]
    removed = [before[i] for i in before if i not in after]
    print(f"Since {previous.get('generated', 'never')}: {len(added)} films added, {len(removed)} removed", file=sys.stderr)
    for label, group in (("+", added), ("-", removed)):
        for film in sorted(group, key=by_fame)[:40]:
            print(f"  {label} {film['t']['en']} ({film['y']})", file=sys.stderr)
    old_rows = {r["id"]: r["films"] for r in previous.get("rows", [])}
    for row in feed["rows"]:
        new = [i for i in row["films"] if i not in old_rows.get(row["id"], [])]
        if old_rows and new:
            print(f"  row {row['id']}: {len(new)} new, first: {', '.join(after[i]['t']['en'] for i in new[:5])}", file=sys.stderr)


def argument(name):
    return sys.argv[sys.argv.index(name) + 1] if name in sys.argv else None


def main():
    out = argument("--out") or OUT
    if "--check" in sys.argv:
        with open(out, encoding="utf-8") as handle:
            check(json.load(handle))
        return
    base_path = argument("--base")
    if not base_path:
        sys.exit("Give --base BASE.json (from build_index.py --with-curated --out BASE.json).")
    with open(base_path, encoding="utf-8") as handle:
        base = json.load(handle)
    previous = {}
    previous_path = argument("--previous") or (out if os.path.exists(out) else None)
    if previous_path and os.path.exists(previous_path):
        with open(previous_path, encoding="utf-8") as handle:
            previous = json.load(handle)
    today = datetime.date.today()
    films, fresh, popular, about = gather(base, today)
    beside, more = extras(today, {f["imdb"] for f in films})
    films = keep_known(films + beside, previous, {f["imdb"] for f in base["films"]})
    rows, excluded = build_rows(films, fresh, popular, about, today, more)
    films = sorted((f for f in films if f["imdb"] not in excluded), key=by_fame)
    feed = {
        "version": VERSION,
        "source": "Wikidata (CC0 1.0), https://www.wikidata.org; Cinemeta, https://www.strem.io",
        "generated": today.isoformat(),
        "films": films,
        "rows": rows,
    }
    check(feed)
    if previous.get("films") and len(films) < len(previous["films"]) * (1 - MAX_DROP):
        sys.exit(f"The feed shrank from {len(previous['films'])} to {len(films)} films: not written. Look at the sources.")
    report(feed, previous)
    with open(out, "w", encoding="utf-8") as handle:
        json.dump(feed, handle, ensure_ascii=False, separators=(",", ":"))
        handle.write("\n")
    print(f"Wrote {out} ({os.path.getsize(out) // 1024} KB)", file=sys.stderr)


if __name__ == "__main__":
    main()
