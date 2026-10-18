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

ls -la "$OUT"
