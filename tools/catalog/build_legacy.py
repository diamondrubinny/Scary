#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
# Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
# Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.
"""
Writes app/src/main/assets/legacy.json from the hand-picked library (catalog.json and series.json), before that
library is removed (docs/CATALOG_REBUILD_PLAN.md). Two things of it outlive it:

  * its ids: viewers' lists (My list, watched, loved, backups) hold them, and the films are "cm_" + IMDb id from now
    on, so each old id needs the IMDb id it stood for;
  * its fear levels (1 to 5), which nothing online gives: the parental lock and family night depend on them.

No titles and no synopses. Run once, on a developer machine:

    python3 -I tools/catalog/build_legacy.py
"""
import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "app/src/main/assets/legacy.json")
IMDB = re.compile(r"^tt\d{7,8}$")
ID = re.compile(r"^[a-z0-9][a-z0-9_-]{0,79}$")


def main():
    films = {}
    for name in ("catalog.json", "series.json"):
        with open(os.path.join(ROOT, "app/src/main/assets", name), encoding="utf-8") as handle:
            for movie in json.load(handle)["movies"]:
                assert ID.match(movie["id"]) and IMDB.match(movie["imdbId"]), movie["id"]
                assert movie["id"] not in films, movie["id"]
                entry = {"imdb": movie["imdbId"]}
                if isinstance(movie.get("fear"), int) and 1 <= movie["fear"] <= 5:
                    entry["fear"] = movie["fear"]
                films[movie["id"]] = entry
    with open(OUT, "w", encoding="utf-8") as handle:
        json.dump({"version": 1, "films": films}, handle, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
        handle.write("\n")
    with_fear = sum(1 for f in films.values() if "fear" in f)
    print(f"Wrote {OUT}: {len(films)} ids, {with_fear} with a fear level")


if __name__ == "__main__":
    main()
