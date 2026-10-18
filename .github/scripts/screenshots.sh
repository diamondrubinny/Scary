#!/usr/bin/env bash
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
open --es scary.debug.open language:he
sleep 6

# Privacy notice and accessibility statement, reached from settings.
open --es scary.debug.open privacy
shot 20-privacy 5
open --es scary.debug.open accessibility
shot 21-accessibility 4

ls -la "$OUT"
