#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
# Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.
#
# Replaces a release: deletes the one under the tag, then creates it again with the arguments given.
#
#   republish.sh <tag> <gh release create arguments...>
#
# GitHub sometimes answers a delete with a server error (build 463: HTTP 502), after which the create fails with "a
# release with the same tag name already exists" and nothing is published. Both steps are tried again a few times, a
# little apart; the job fails only when the release still could not be made.
set -uo pipefail

tag="$1"
shift
for attempt in 1 2 3 4; do
  gh release delete "$tag" --cleanup-tag --yes || true
  if gh release create "$tag" "$@"; then
    exit 0
  fi
  echo "::warning::Publishing the $tag release failed (attempt $attempt); trying again."
  sleep $((attempt * 20))
done
echo "::error::The $tag release could not be published."
exit 1
