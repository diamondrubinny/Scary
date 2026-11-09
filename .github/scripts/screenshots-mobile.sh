#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
# Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.
# Installs the debug APK on a phone emulator, runs the phone layout tests, then captures the main screens as a phone
# upright, a phone on its side, a tablet upright and a tablet on its side (the display is resized with "wm").
set -euo pipefail

PKG=io.github.diamondrubinny.scary
ACT="$PKG/.MainActivity"
OUT=screenshots-mobile
mkdir -p "$OUT"

shot() { sleep "${2:-3}"; adb exec-out screencap -p > "$OUT/$1.png"; echo "captured $1"; }
open() { adb shell am start -n "$ACT" "$@" > /dev/null; }
restart() { adb shell am force-stop "$PKG"; sleep 1; }
# Scrolls up by about half the screen, whatever its size and orientation.
scroll() {
  local size w h
  size=$(adb shell wm size | tr -d '\r' | awk -F': ' '/Override/ {o=$2} /Physical/ {p=$2} END {print (o != "" ? o : p)}')
  w=${size%x*}; h=${size#*x}
  if [ "$(adb shell settings get system user_rotation | tr -d '\r')" = "1" ]; then local t=$w; w=$h; h=$t; fi
  adb shell input swipe $((w / 2)) $((h * 3 / 4)) $((w / 2)) $((h / 4)) 600
}

adb install -r dist/scary.apk
adb shell svc power stayon true || true
adb shell settings put system accelerometer_rotation 0 || true
adb shell settings put system user_rotation 0 || true

# The app drawer lists SCARY (LAUNCHER), as a phone's launcher would.
# (Read in full first, then searched: "grep -q" ending a pipe early fails it under pipefail.)
drawer=$(adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER | tr -d '\r')
if grep -q "$PKG" <<< "$drawer"; then
  echo "In the app drawer: yes" > "$OUT/ui-tests.txt"
else
  echo "SCARY is not in the app drawer. The drawer lists:"; echo "$drawer"; exit 1
fi

run_tests() {
  local label=$1
  adb install -r dist/scary-tests.apk > /dev/null
  for s in window_animation_scale transition_animation_scale animator_duration_scale; do adb shell settings put global "$s" 0; done
  result=$(adb shell am instrument -w -r -e class "$PKG.PhoneLayoutTest" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" | tr -d '\r')
  for s in window_animation_scale transition_animation_scale animator_duration_scale; do adb shell settings put global "$s" 1; done
  echo "$result"
  { echo "$label:"; echo "$result" | grep -E '^(OK \(|FAILURES|Tests run:)' || echo "No test summary"; } >> "$OUT/ui-tests.txt"
  if ! grep -q '^OK (' <<< "$result"; then echo "Phone layout tests failed ($label)."; exit 1; fi
}

screens() {
  local p=$1
  restart
  # No trailer question over the shots (it would only show on a TV-size screen anyway).
  open --es scary.debug.open trailers:off
  sleep 2
  open --es scary.debug.open home
  shot "$p-01-home" 20
  scroll
  shot "$p-02-home-rows" 3
  open --es scary.debug.open details:hereditary
  shot "$p-03-details" 8
  scroll
  shot "$p-04-details-below" 3
  open --es scary.debug.open search --es scary.debug.query fincher
  shot "$p-05-search" 8
  open --es scary.debug.open settings
  shot "$p-06-settings" 4
  open --es scary.debug.open ask
  shot "$p-07-ask" 5
  open --es scary.debug.open bucket
  shot "$p-08-bucket" 4
  open --es scary.debug.open pin
  shot "$p-09-pin" 4
  adb shell input keyevent KEYCODE_BACK
  open --es scary.debug.open home
}

# A phone upright (the emulator's own size), with its layout tests.
run_tests "Phone upright"
screens phone

# The same phone on its side.
adb shell settings put system user_rotation 1
screens phone-landscape
adb shell settings put system user_rotation 0

# A 10" tablet upright: 800 x 1280 dp, then on its side (1280 x 800 dp, the TV layout).
adb shell wm size 1600x2560
adb shell wm density 320
sleep 3
run_tests "Tablet upright"
screens tablet
adb shell wm size 2560x1600
sleep 3
screens tablet-landscape

adb shell wm size reset
adb shell wm density reset
cat "$OUT/ui-tests.txt"
