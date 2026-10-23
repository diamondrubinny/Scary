#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
# Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.
#
# Fails the build when the SCARY credit is missing: from any source file, the NOTICE file, the license, or the one
# place the app reads it from (core Credit.kt). The Apache License 2.0 requires derivative works to keep it.
set -euo pipefail

CREDIT="Powered by SCARY · Open source"
status=0

missing=$(find app/src core/src .github/scripts -type f \( -name '*.kt' -o -name '*.sh' \) -print0 \
  | xargs -0 grep -L "$CREDIT" || true)
for f in build.gradle.kts settings.gradle.kts app/build.gradle.kts core/build.gradle.kts .github/workflows/android.yml; do
  [ -f "$f" ] && ! grep -q "$CREDIT" "$f" && missing="$missing"$'\n'"$f"
done
missing=$(echo "$missing" | sed '/^$/d')
if [ -n "$missing" ]; then
  echo "These files lack the SCARY credit header:"
  echo "$missing"
  status=1
fi

grep -q "$CREDIT · https://github.com/diamondrubinny/Scary" NOTICE || { echo "NOTICE lacks the credit."; status=1; }
grep -q "Apache License" LICENSE || { echo "LICENSE is not the Apache License 2.0."; status=1; }
grep -q "const val LINE = \"$CREDIT · github.com/diamondrubinny/Scary\"" \
  core/src/main/kotlin/io/github/diamondrubinny/scary/core/brand/Credit.kt || { echo "Credit.kt was changed."; status=1; }

[ "$status" -eq 0 ] && echo "SCARY credit present everywhere."
exit "$status"
