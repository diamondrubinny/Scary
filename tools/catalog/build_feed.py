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
PAGE = 50
YEAR_PAGES = 40  # A year's mixed catalog: 2,000 films, of which a few dozen are horror.
TOP_PAGES = 3
VERSION = 1
LANGS = ["en", "he", "ru", "pt", "ar"]
IMDB = re.compile(r"^tt\d{7,8}$")
GENRE = re.compile(r"^[A-Za-z][A-Za-z -]{1,23}$")
MAX_TITLE = 120
MAX_NAME = 80
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
    return {
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

    missing = [i for i in imdb_ids if i not in cache]
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
    info = cinemeta_details(sorted(set(films) | set(popular)), known)
    added = 0
    for imdb in dict.fromkeys(fresh + popular):
        known_film = info.get(imdb)
        if imdb in films or not known_film or not on_shelf(known_film):
            continue
        name = known_film.get("n")
        if not name or ADULT.search(name) or not known_film.get("y") or not 1895 <= known_film["y"] <= today.year:
            continue
        film = {"imdb": imdb, "t": {"en": name}, "y": known_film["y"], "p": 0}
        if known_film.get("d"):
            film["d"] = known_film["d"]
        films[imdb] = film
        added += 1
    print(f"{len(base['films'])} films from the base, {added} more from Cinemeta's new and popular catalogs", file=sys.stderr)
    out = []
    for imdb, film in films.items():
        known_film = info.get(imdb)
        if known_film is None:
            # Not reachable now: the film stays as the base has it, without a rating.
            known_film = {}
        elif not on_shelf(known_film):
            continue
        other = [g for g in (known_film.get("g") or film.get("g") or []) if g != "Horror" and GENRE.match(g)][:6]
        film.pop("g", None)
        if other:
            film["g"] = other
        if known_film.get("r"):
            film["r"] = known_film["r"]
        if known_film.get("rel"):
            film["rel"] = known_film["rel"]
        out.append(film)
    return out, [i for i in fresh if i in films], [i for i in popular if i in films]


def overrides():
    if not os.path.exists(OVERRIDES):
        return set(), {}
    with open(OVERRIDES, encoding="utf-8") as handle:
        data = json.load(handle)
    everywhere = {i for i in data.get("exclude", []) if IMDB.match(i)}
    per_row = {row: {i for i in ids if IMDB.match(i)} for row, ids in data.get("notInRow", {}).items()}
    return everywhere, per_row


def has(*subgenres):
    return lambda film: bool(set(film.get("s", [])) & set(subgenres))


def known_enough(film, rating=5.5, wikipedias=8):
    """Not the first film of a row unless people know it and did not hate it."""
    return film.get("p", 0) >= wikipedias and film.get("r", 0) >= rating


def by_fame(film):
    return (-film.get("p", 0), -film.get("r", 0), film["imdb"])


# The home rows, in the order the home screen shows them. Each: its id (the app holds its name in five languages), who
# is in it, and the order. A subgenre comes from Wikidata's own labels; where IMDb has a genre for the same thing
# (comedy), both must say so.
ROWS = [
    ("top_rated", lambda f: f.get("r", 0) >= 7.3 and f.get("p", 0) >= 15, lambda f: (-f.get("r", 0), -f.get("p", 0), f["imdb"])),
    ("slashers", lambda f: has("slasher")(f) and known_enough(f), by_fame),
    ("ghosts", lambda f: has("ghost", "supernatural", "possession")(f) and known_enough(f), by_fame),
    ("haunted_houses", lambda f: has("haunted_house")(f) and known_enough(f, wikipedias=5), by_fame),
    ("zombies", lambda f: has("zombie")(f) and known_enough(f), by_fame),
    ("vampires", lambda f: has("vampire")(f) and known_enough(f), by_fame),
    ("creatures", lambda f: has("creature", "animal")(f) and known_enough(f), by_fame),
    ("psychological", lambda f: has("psychological")(f) and known_enough(f, rating=6.0), by_fame),
    ("found_footage", lambda f: has("found_footage")(f) and known_enough(f, wikipedias=5), by_fame),
    ("horror_comedy", lambda f: has("comedy")(f) and "Comedy" in f.get("g", []) and known_enough(f, rating=6.0), by_fame),
    ("classics", lambda f: f["y"] <= 1980 and f.get("r", 0) >= 6.8 and f.get("p", 0) >= 15, by_fame),
]


def build_rows(films, fresh, popular, today):
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

    # New: out in the last twelve months (never one that is not out yet), in Cinemeta's order of popularity.
    new = [i for i in fresh if i in by_id and year_ago <= by_id[i].get("rel", "") <= today.isoformat()]
    add("new", new)
    # Popular now: Cinemeta's order; without it, the best known films of the last five years.
    recent = sorted((f for f in by_id.values() if f["y"] >= today.year - 5 and f.get("r", 0) >= 5.5), key=by_fame)
    add("popular", popular or [f["imdb"] for f in recent])
    for row_id, belongs, order in ROWS:
        add(row_id, [f["imdb"] for f in sorted((f for f in by_id.values() if belongs(f)), key=order)])
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
        assert set(film) <= {"imdb", "t", "y", "p", "g", "d", "s", "r", "rel"}, imdb
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
    films, fresh, popular = gather(base, today)
    films = keep_known(films, previous, {f["imdb"] for f in base["films"]})
    rows, excluded = build_rows(films, fresh, popular, today)
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
