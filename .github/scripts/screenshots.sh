#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Powered by SCARY · Open source · https://github.com/diamondrubinny/Scary
# Copyright 2026 diamondrubinny. See the LICENSE and NOTICE files.
# Installs the debug APK on the running TV emulator and captures each main screen.
set -euo pipefail

PKG=io.github.diamondrubinny.scary
ACT="$PKG/.MainActivity"
OUT=screenshots
mkdir -p "$OUT"

shot() { sleep "${2:-3}"; adb exec-out screencap -p > "$OUT/$1.png"; echo "captured $1"; }
key() { adb shell input keyevent "$@"; }
open() { adb shell am start -n "$ACT" "$@" > /dev/null; }

adb install -r dist/scary.apk
# Keep the screen awake for the whole run.
adb shell svc power stayon true || true

# UI tests first, on a clean install: home, search, a movie page and My list, the mood row, family night.
# System animations are off while they run (the usual setup for UI tests) and back on for the screenshots.
if [ -f dist/scary-tests.apk ]; then
  adb install -r dist/scary-tests.apk
  for s in window_animation_scale transition_animation_scale animator_duration_scale; do adb shell settings put global "$s" 0; done
  # Raw mode (-r) reports each test's status, so tests skipped by an assumption (code -4) are counted, not hidden.
  # The app logs each OK press it handles under this tag (off by default); printed below if a test fails.
  adb shell setprop log.tag.ScaryKeys DEBUG
  adb logcat -c || true
  result=$(adb shell am instrument -w -r "$PKG.test/androidx.test.runner.AndroidJUnitRunner" | tr -d '\r')
  adb shell setprop log.tag.ScaryKeys INFO
  for s in window_animation_scale transition_animation_scale animator_duration_scale; do adb shell settings put global "$s" 1; done
  skipped=$(echo "$result" | grep -B6 'INSTRUMENTATION_STATUS_CODE: -4' | grep 'INSTRUMENTATION_STATUS: test=' | sed 's/.*test=//' | sort -u | tr '\n' ' ' || true)
  {
    echo "$result" | grep -E '^(OK \(|FAILURES|Tests run:)' || echo "No test summary"
    echo "Skipped by an assumption: ${skipped:-none}"
  } > "$OUT/ui-tests.txt"
  echo "$result"
  cat "$OUT/ui-tests.txt"
  echo "OK key events the app handled during the tests (last 200):"
  adb logcat -d -s ScaryKeys:D | tail -200 || true
  if ! echo "$result" | grep -q '^OK ('; then
    echo "UI tests failed."
    exit 1
  fi
fi

# Trailers that play by themselves: the question asked before the first one. Then trailers are turned off, as if
# answered, so the question never pops up over a later screenshot and no shot catches a trailer half loaded.
open --es scary.debug.open trailer-intro
shot 59-trailer-intro 8
open --es scary.debug.open trailers:off
sleep 2

open
shot 01-home 25

key KEYCODE_DPAD_DOWN
shot 02-apps-row 3

key KEYCODE_DPAD_DOWN
key KEYCODE_DPAD_LEFT
key KEYCODE_DPAD_LEFT
shot 03-browsing-classics 3

key KEYCODE_DPAD_DOWN
key KEYCODE_DPAD_DOWN
shot 04-psychological-rows 3

open --es scary.debug.open details:hereditary
shot 05-details 8

open --es scary.debug.open search --es scary.debug.query fincher
shot 06-search 8

open --es scary.debug.open apps
shot 07-all-apps 4

open --es scary.debug.open settings
shot 08-settings 4
# The bottom of settings: the About text, the notice on where movies come from, and the credit.
for _ in $(seq 20); do key KEYCODE_DPAD_DOWN; done
shot 38-settings-about 3

open --es scary.debug.open pin
shot 09-parental-pin 4

key KEYCODE_BACK
open --es scary.debug.open tmdb
shot 10-tmdb-key 4

# The same key screens in English, Portuguese and Russian (Hebrew is the default run above).
n=11
for lang in en pt ru; do
  open --es scary.debug.open "language:$lang"
  sleep 6
  open
  shot "$n-home-$lang" 12; n=$((n+1))
  open --es scary.debug.open details:the_shining
  shot "$n-details-$lang" 6; n=$((n+1))
  open --es scary.debug.open settings
  shot "$n-settings-$lang" 4; n=$((n+1))
done
# Arabic, the fifth language: right to left with its own fonts (Rakkas and Cairo).
open --es scary.debug.open language:ar
sleep 6
open
shot 50-ar-home 12
open --es scary.debug.open details:the_shining
shot 51-ar-details 6
open --es scary.debug.open settings
shot 52-ar-settings 4
open --es scary.debug.open search --es scary.debug.query shining
shot 53-ar-search 14
open --es scary.debug.open pin
shot 54-ar-pin 4
key KEYCODE_BACK
open --es scary.debug.open language:he
sleep 6

# Privacy notice and accessibility statement, reached from settings.
open --es scary.debug.open privacy
shot 20-privacy 5
open --es scary.debug.open accessibility
shot 21-accessibility 4

# My list: add The Shining from its page (right to left: Watch, Trailer, then My list), then find the row at home.
open --es scary.debug.open details:the_shining
sleep 6
key KEYCODE_DPAD_LEFT
key KEYCODE_DPAD_LEFT
key KEYCODE_DPAD_CENTER
shot 22-details-my-list 3
open --es scary.debug.open home
sleep 3
key KEYCODE_DPAD_DOWN
key KEYCODE_DPAD_DOWN
shot 23-home-my-list 3

# Screensaver, started the way the system does after idle (Somnambulator may be missing on some images).
adb shell settings put secure screensaver_components "$PKG/.dream.ScaryDream" || true
adb shell settings put secure screensaver_enabled 1 || true
adb shell am start -n com.android.systemui/.Somnambulator > /dev/null 2>&1 || echo "Somnambulator not available"
shot 24-screensaver 15
key KEYCODE_BACK

# Web search below the library results, and "Surprise me".
open --es scary.debug.open search --es scary.debug.query conjuring
shot 25-search-web 10
open --es scary.debug.open home
open --es scary.debug.open surprise
shot 26-surprise 6

# The seasonal row (only around its dates: Halloween in October, Friday the 13th) and a chosen mood.
open --es scary.debug.open home
sleep 3
open --es scary.debug.open row:season:halloween
shot 34-seasonal-row 4
open --es scary.debug.open mood:relentless
shot 35-moods 5
open --es scary.debug.open home

# Family night: only gentle movies and a badge in the top bar; then back to normal.
open --es scary.debug.open family
shot 36-family-night 6
open --es scary.debug.open family
sleep 3
open --es scary.debug.open home

# English set for the README gallery (copied to docs/screenshots/en-*.jpg).
open --es scary.debug.open language:en
sleep 6
open --es scary.debug.open home
shot 40-en-home 12
open --es scary.debug.open details:hereditary
shot 41-en-details 8
open --es scary.debug.open search --es scary.debug.query conjuring
shot 42-en-search-web 10
open --es scary.debug.open home
sleep 3
key KEYCODE_DPAD_DOWN
key KEYCODE_DPAD_DOWN
shot 43-en-my-list 4
open --es scary.debug.open home
open --es scary.debug.open surprise
shot 44-en-surprise 6
# English versions of the moods, family night and shiver screens, for the English video ad (tools/promo).
open --es scary.debug.open home
open --es scary.debug.open mood:relentless
shot 55-en-moods 5
open --es scary.debug.open home
open --es scary.debug.open family
shot 56-en-family-night 6
open --es scary.debug.open family
sleep 3
open --es scary.debug.open home
sleep 3
open --es scary.debug.open details:the_shining
sleep 6
key KEYCODE_DPAD_CENTER
shot 58-en-shiver 1
sleep 4
adb shell am force-stop com.android.vending || true
open --es scary.debug.open apps
shot 45-en-all-apps 4
open --es scary.debug.open settings
shot 46-en-settings 4
for _ in $(seq 20); do key KEYCODE_DPAD_DOWN; done
shot 49-en-settings-about 3
open --es scary.debug.open privacy
shot 47-en-privacy 4
adb shell am start -n com.android.systemui/.Somnambulator > /dev/null 2>&1 || true
shot 48-en-screensaver 15
key KEYCODE_BACK
open --es scary.debug.open language:he
sleep 6
open --es scary.debug.open home
sleep 3

# The largest system font on Android TV (130%): checks that no text that matters is cut off.
adb shell settings put system font_scale 1.3
open --es scary.debug.open home
shot 29-font130-home 6
key KEYCODE_DPAD_DOWN
shot 32-font130-apps-row 3
open --es scary.debug.open settings
shot 30-font130-settings 4
open --es scary.debug.open details:the_shining
shot 31-font130-details 6
adb shell settings put system font_scale 1.0
open --es scary.debug.open home
sleep 3

# Watch: the shiver screen with the movie's logo, just before Stremio (or Google Play, when it is missing) opens.
open --es scary.debug.open details:the_shining
sleep 6
key KEYCODE_DPAD_CENTER
shot 37-launch-shiver 1
sleep 4
adb shell am force-stop com.android.vending || true
open --es scary.debug.open home
sleep 3

# Release build (R8, not debuggable): installs over the debug build with the same key and keeps its data.
# It must start and navigate without crashing, or this job fails and nothing is published.
if [ -f dist/scary-release.apk ]; then
  adb logcat -b all -c || true
  adb install -r dist/scary-release.apk
  adb shell am force-stop "$PKG"
  open
  shot 27-release-home 20
  # Right to left on the hero: Watch, Trailer, then Details.
  key KEYCODE_DPAD_LEFT
  key KEYCODE_DPAD_LEFT
  key KEYCODE_DPAD_CENTER
  shot 28-release-details 6
  key KEYCODE_BACK
  sleep 2
  if adb logcat -d -b crash | grep -q "$PKG"; then
    echo "The release build crashed:"
    adb logcat -d -b crash
    exit 1
  fi
  adb shell pidof "$PKG" > /dev/null || { echo "The release build is not running."; exit 1; }
  echo "Release build smoke test passed."

  # Cold start of the release build, 3 runs each: as installed, then compiled with its baseline profile the way the
  # TV compiles it in the background (profileinstaller writes the profile on first launch).
  cold_start() {
    for _ in 1 2 3; do
      adb shell am force-stop "$PKG"
      sleep 2
      adb shell am start -W -n "$ACT" | tr -d '\r' | awk -F': ' '/TotalTime/ {print $2}'
      sleep 3
    done | paste -sd ' '
  }
  {
    echo "Release cold start, ms (emulator, API 30 TV, 3 runs)"
    echo "as installed: $(cold_start)"
    adb shell cmd package compile -f -m speed-profile "$PKG" > /dev/null || true
    echo "speed-profile: $(cold_start)"
  } | tee "$OUT/startup.txt"
  adb shell am force-stop "$PKG"
fi

# Crash safety net, on the debug build: three real crashes in a row must bring SCARY back in safe mode, with a message.
adb install -r dist/scary.apk
for _ in 1 2 3; do
  adb shell am force-stop "$PKG"
  adb shell am start -n "$ACT" --es scary.debug.open crash > /dev/null || true
  sleep 4
  # Close the system's "SCARY has stopped" dialog, if the TV shows one.
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null 2>&1 || true
done
adb shell am force-stop "$PKG"
open
shot 33-safe-mode 7
adb shell am force-stop "$PKG"

ls -la "$OUT"

# Real trailers on the emulator: turned on, six movie pages opened in turn, then what YouTube answered for each try
# (the app logs it under ScaryTrailer, with the page's console in debug builds). Turned off again afterwards.
open --es scary.debug.open trailers:on
sleep 2
adb logcat -c || true
open --es scary.debug.open details:hereditary
shot 61-trailer-loading 5
shot 62-trailer-playing 12
for movie in the_shining halloween get_out it_follows scream; do
  open --es scary.debug.open "details:$movie"
  sleep 16
done
shot 63-trailer-last 1
adb logcat -d -s ScaryTrailer:V > "$OUT/trailer-log.txt" 2>/dev/null || true
echo "Trailer log:"; cat "$OUT/trailer-log.txt" || true
open --es scary.debug.open trailers:off
