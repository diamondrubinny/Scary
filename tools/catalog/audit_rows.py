#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
# Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
# Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.
"""
Quality control for the hand-picked rows (the online rows are checked inside the app, by core's RowAudit). Asks
Cinemeta for the countries of every film in app/src/main/assets/catalog.json and series.json and reports what the
rules the app uses would not let into the same row:

  Problems (exit code 1):
  * a film whose first country is Arab or Indian (core OriginFilter: kept off every shelf);
  * in a row about a place (European, Spanish-language, Asian horror, giallo): an American-led film, one whose first
    country is the United States (The Grudge, 2004, an American remake, was in Asian horror);
  * a film in a row twice.
  Notes (printed, exit code 0):
  * in a row about a place: a film whose first country is another one (the library's European row has British films on
    purpose), and a film that lists the United States, unless it is in REVIEWED: the app tells these by original
    language, which Cinemeta does not give, so a person looks once and writes down why it stays;
  * a film in no row at all (still found by search).

The country lists are read from core's Origin.kt and OriginFilter.kt, so there is one list. Run on a developer machine:

    python3 -I tools/catalog/audit_rows.py           # prints a report, exits 1 when something is wrong
"""
import json
import os
import re
import sys
import urllib.request
from concurrent.futures import ThreadPoolExecutor

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CORE = os.path.join(ROOT, "core/src/main/kotlin/io/github/diamondrubinny/scary/core/catalog")
ASSETS = os.path.join(ROOT, "app/src/main/assets")
CINEMETA = "https://v3-cinemeta.strem.io/meta/{}/{}.json"
USER_AGENT = "SCARY-catalog-audit/1.0 (https://github.com/diamondrubinny/Scary)"
PLACE_ROWS = {"european_horror": "EUROPE", "spanish_horror": "SPANISH", "asian_horror": "ASIA", "giallo": "ITALY"}
# Films that list the United States in a row about a place and belong there, looked at by hand (16.11.2026): (row, title).
REVIEWED = {
    ("european_horror", "Lamb"): "Icelandic, by Valdimar Jóhannsson",
    ("asian_horror", "Ringu"): "Japanese, by Hideo Nakata",
    ("asian_horror", "Thirst"): "Korean, by Park Chan-wook",
    ("spanish_horror", "When Evil Lurks"): "Spanish-language, by Demián Rugna",
}


def strings(block):
    return set(re.findall(r'"([^"]+)"', block))


def load_rules():
    origin = open(os.path.join(CORE, "Origin.kt"), encoding="utf-8").read()
    regions = {}
    for name in ("EUROPE", "SPANISH", "ASIA", "ITALY"):
        m = re.search(name + r"\(\s*countries = setOf\((.*?)\)\s*,\s*languages", origin, re.S)
        regions[name] = strings(m.group(1))
    hidden = open(os.path.join(CORE, "OriginFilter.kt"), encoding="utf-8").read()
    m = re.search(r"private val COUNTRIES = setOf\((.*?)\n    \)", hidden, re.S)
    return regions, strings(m.group(1))


def countries_of(kind, imdb):
    for _ in range(3):
        try:
            request = urllib.request.Request(CINEMETA.format(kind, imdb), headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(request, timeout=30) as response:
                text = (json.load(response).get("meta") or {}).get("country") or ""
            return [c.strip().lower() for c in text.split(",") if c.strip()]
        except Exception:
            pass
    return None


def main():
    regions, hidden = load_rules()
    problems = []
    notes = []
    films = []
    for file, kind, key in (("catalog.json", "movie", "movies"), ("series.json", "series", "movies")):
        with open(os.path.join(ASSETS, file), encoding="utf-8") as handle:
            data = json.load(handle)
        by_id = {m["id"]: m for m in data[key]}
        in_rows = {}
        for row in data["collections"]:
            seen = set()
            for movie_id in row["movieIds"]:
                if movie_id in seen:
                    problems.append(f"{row['id']}: {by_id[movie_id]['title']} is listed twice")
                seen.add(movie_id)
                in_rows.setdefault(movie_id, []).append(row["id"])
                films.append((kind, by_id[movie_id], row["id"]))
        for movie_id, movie in by_id.items():
            if movie_id not in in_rows:
                notes.append(f"{file}: {movie['title']} is in no row")
    unique = sorted({(kind, m["imdbId"]) for kind, m, _ in films})
    with ThreadPoolExecutor(max_workers=6) as pool:
        countries = dict(zip(unique, pool.map(lambda k: countries_of(*k), unique)))
    unknown = [k for k, v in countries.items() if v is None]
    for kind, movie, row in films:
        found = countries.get((kind, movie["imdbId"]))
        if not found:
            continue
        label = f"{row}: {movie['title']} ({movie.get('year')}) [{', '.join(found)}]"
        if found[0] in hidden:
            problems.append(f"{label} first country is Arab or Indian (kept off every shelf)")
        region = PLACE_ROWS.get(row)
        if region and found[0] == "united states":
            problems.append(f"{label} is American-led, in a row about a place")
        elif region and found[0] not in regions[region]:
            notes.append(f"{label} first country is not in {region}")
        elif region and "united states" in found and (row, movie["title"]) not in REVIEWED:
            notes.append(f"{label} lists the United States: check it by hand, then add it to REVIEWED")
    print(f"{len(unique)} films checked; Cinemeta did not answer for {len(unknown)}", file=sys.stderr)
    for note in notes:
        print("note: " + note)
    for problem in problems:
        print("PROBLEM: " + problem)
    print(f"{len(problems)} problems, {len(notes)} notes")
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
