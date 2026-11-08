#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
# Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
# Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.
#
# Writes update.json for the "stable" release: what the app reads before offering an update (core/update/Updates.kt
# checks every field). Usage: update-manifest.py <apk> <versionCode> <versionName> <notes.json> <out.json>
import hashlib
import json
import os
import sys

apk, version_code, version_name, notes_path, out = sys.argv[1:6]
digest = hashlib.sha256()
with open(apk, "rb") as f:
    for chunk in iter(lambda: f.read(1 << 20), b""):
        digest.update(chunk)
with open(notes_path, encoding="utf-8") as f:
    notes = {k: v for k, v in json.load(f).items() if isinstance(v, str) and len(k) == 2}
manifest = {
    "repository": "diamondrubinny/Scary",
    "channel": "stable",
    "package": "io.github.diamondrubinny.scary",
    "versionCode": int(version_code),
    "versionName": version_name,
    "sha256": digest.hexdigest(),
    "size": os.path.getsize(apk),
    "minSdk": 26,
    "notes": notes,
}
with open(out, "w", encoding="utf-8") as f:
    json.dump(manifest, f, ensure_ascii=False, indent=2)
print(json.dumps({k: manifest[k] for k in ("versionCode", "versionName", "sha256", "size")}))
