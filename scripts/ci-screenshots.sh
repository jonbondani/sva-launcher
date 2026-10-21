#!/usr/bin/env bash
# Runs inside the Android TV emulator job: installs the debug APK, opens Sva's screens and
# saves screenshots plus any crash found in the log to ./shots, so a change can be checked
# visually before it reaches a real TV.
set -u
OUT=shots
mkdir -p "$OUT"
shot() { adb exec-out screencap -p > "$OUT/$1.png"; echo "shot $1"; }
key()  { adb shell input keyevent "$1"; sleep "${2:-1.2}"; }

# Wait until the system has fully booted and the screen is on.
for i in $(seq 1 60); do [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ] && break; sleep 2; done
adb shell input keyevent KEYCODE_WAKEUP
sleep 20
adb install -r app/build/outputs/apk/debug/app-debug.apk 2>&1 | tee "$OUT/install.txt"
grep -q Success "$OUT/install.txt" || { echo "INSTALL FAILED" > "$OUT/CRASHED"; }
adb logcat -c
adb shell am start -W -n com.sva.launcher/.MainActivity | tee "$OUT/start.txt"
sleep 15
adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" > "$OUT/resumed.txt"
shot 01-home
key KEYCODE_DPAD_RIGHT; shot 02-home-focus-second-tile
key KEYCODE_DPAD_RIGHT; key KEYCODE_DPAD_RIGHT; shot 03-home-focus-fourth-tile
key KEYCODE_DPAD_UP 1.5; shot 04-home-up
key KEYCODE_DPAD_UP 1.5; shot 05-home-top
key KEYCODE_DPAD_RIGHT; key KEYCODE_DPAD_RIGHT; shot 06-top-right
# Dock tabs, the shortcut preview panel and Sva's settings.
key KEYCODE_DPAD_DOWN 1.5; shot 06b-dock-home
key KEYCODE_DPAD_RIGHT; key KEYCODE_DPAD_CENTER 3; shot 06c-tab-livetv
key KEYCODE_DPAD_RIGHT; key KEYCODE_DPAD_CENTER 3; shot 06d-tab-games
key KEYCODE_DPAD_RIGHT; key KEYCODE_DPAD_CENTER 3; shot 06e-tab-events
key KEYCODE_DPAD_RIGHT; key KEYCODE_DPAD_CENTER 3; shot 06f-tab-saved
key KEYCODE_DPAD_RIGHT 1.5; shot 06g-shortcut-matches-preview
for i in 1 2 3 4; do key KEYCODE_DPAD_RIGHT 1; done; shot 06h-shortcut-settings-preview
key KEYCODE_DPAD_CENTER 3; shot 06i-sva-settings
key KEYCODE_DPAD_DOWN; key KEYCODE_DPAD_DOWN; shot 06j-sva-settings-focus
key KEYCODE_BACK 2; key KEYCODE_BACK 2
adb shell am start -n com.sva.launcher/.MatchesActivity
sleep 8
shot 07-matches
key KEYCODE_BACK 2
shot 08-back-home
# The input tiles (HDMI, TDT) with sample inputs, since the emulator has none.
adb shell am force-stop com.sva.launcher
adb shell am start -W -n com.sva.launcher/.MainActivity --ez demo_tv_inputs true > /dev/null
sleep 12
shot 08b-inputs
key KEYCODE_DPAD_LEFT; key KEYCODE_DPAD_LEFT; key KEYCODE_DPAD_LEFT; key KEYCODE_DPAD_LEFT; shot 08c-inputs-focus
# Same screens in dark mode.
adb shell cmd uimode night yes
adb shell am force-stop com.sva.launcher
adb shell am start -W -n com.sva.launcher/.MainActivity > /dev/null
sleep 12
shot 09-dark-home
key KEYCODE_DPAD_RIGHT; shot 10-dark-focus
adb shell cmd uimode night no
adb shell dumpsys tv_input > "$OUT/tv_input.txt" 2>&1
adb logcat -d | grep -vE "RuntimeInit|commands.input|Shutting down VM|default boot image|lock profiling" | tail -400 > "$OUT/log.txt"
if grep -q "FATAL EXCEPTION" "$OUT/log.txt"; then echo "CRASH" > "$OUT/CRASHED"; fi
adb shell am force-stop com.sva.launcher
ls -la "$OUT"
