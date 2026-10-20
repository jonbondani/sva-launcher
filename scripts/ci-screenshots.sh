#!/usr/bin/env bash
# Runs inside the Android TV emulator job: installs the debug APK, opens Sva's screens and
# saves screenshots plus any crash found in the log to ./shots, so a change can be checked
# visually before it reaches a real TV.
set -u
OUT=shots
mkdir -p "$OUT"
shot() { adb exec-out screencap -p > "$OUT/$1.png"; echo "shot $1"; }
key()  { adb shell input keyevent "$1"; sleep "${2:-1.2}"; }

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell am start -n com.sva.launcher/.MainActivity
sleep 15
shot 01-home
key KEYCODE_DPAD_RIGHT; shot 02-home-focus-second-tile
key KEYCODE_DPAD_RIGHT; key KEYCODE_DPAD_RIGHT; shot 03-home-focus-fourth-tile
key KEYCODE_DPAD_UP 1.5; shot 04-home-up
key KEYCODE_DPAD_UP 1.5; shot 05-home-top
key KEYCODE_DPAD_RIGHT; key KEYCODE_DPAD_RIGHT; shot 06-top-right
adb shell am start -n com.sva.launcher/.MatchesActivity
sleep 8
shot 07-matches
key KEYCODE_BACK 2
shot 08-back-home
adb shell dumpsys tv_input > "$OUT/tv_input.txt" 2>&1
adb logcat -d | grep -E "AndroidRuntime|FATAL|com.sva.launcher" | tail -300 > "$OUT/log.txt"
if grep -q "FATAL EXCEPTION" "$OUT/log.txt"; then echo "CRASH" > "$OUT/CRASHED"; fi
adb shell am force-stop com.sva.launcher
ls -la "$OUT"
