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
key KEYCODE_DPAD_DOWN 1.5; key KEYCODE_DPAD_DOWN 1.5; shot 06b-dock-home
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
# With sample inputs (the emulator has none): the shelf has only apps, inputs are behind "Fuentes".
adb shell am force-stop com.sva.launcher
adb shell am start -W -n com.sva.launcher/.MainActivity --ez demo_tv_inputs true > /dev/null
sleep 12
shot 08b-inputs
# Reorder: hold OK on an app, move it right, drop it.
key KEYCODE_DPAD_RIGHT
adb shell input keyevent --longpress KEYCODE_DPAD_CENTER; sleep 1.5; shot 08d-move-start
key KEYCODE_DPAD_RIGHT 1.5; shot 08e-moved-right
key KEYCODE_DPAD_CENTER 1.5; shot 08f-dropped
# The "add apps" tile at the end of the row, and the picker it opens.
for i in $(seq 1 14); do key KEYCODE_DPAD_RIGHT 0.5; done; sleep 1; shot 08g-add-apps
key KEYCODE_DPAD_CENTER 3; shot 08h-app-picker
key KEYCODE_BACK 2
# "Estoy viendo" with sample Stremio items (real Cinemeta stills, made-up progress).
adb shell am force-stop com.sva.launcher
adb shell am start -W -n com.sva.launcher/.MainActivity --ez demo_watching true > /dev/null
sleep 15
shot 08i-watching
key KEYCODE_DPAD_UP 1.5; shot 08j-watching-focus
key KEYCODE_DPAD_RIGHT 1.5; shot 08k-watching-focus-2
# Same screens in dark mode.
adb shell cmd uimode night yes
adb shell am force-stop com.sva.launcher
adb shell am start -W -n com.sva.launcher/.MainActivity > /dev/null
sleep 12
shot 09-dark-home
key KEYCODE_DPAD_RIGHT; shot 10-dark-focus
# The TV as it will look: inputs, today's row and the shelf together, in dark mode.
adb shell am force-stop com.sva.launcher
adb shell am start -W -n com.sva.launcher/.MainActivity --ez demo_tv_inputs true --ez demo_watching true > /dev/null
sleep 15
shot 11-tv-full
# "Fuentes" in the dock: down to the dock, right past the five tabs, then open it.
key KEYCODE_DPAD_DOWN 1.5; shot 11b-tv-dock
for i in 1 2 3 4 5; do key KEYCODE_DPAD_RIGHT 1; done; shot 11c-tv-fuentes-focus
key KEYCODE_DPAD_CENTER 2; shot 11d-tv-fuentes-list
key KEYCODE_BACK 1.5
key KEYCODE_DPAD_UP 1.5; key KEYCODE_DPAD_UP 1.5; key KEYCODE_DPAD_UP 1.5; shot 11e-tv-full-top-pill
adb shell cmd uimode night no
# Private setup over ADB: a playlist link arrives and Sva asks before saving it (cancelled here).
adb shell am start -n com.sva.launcher/.MainActivity --es set_playlist "'https://iptv-org.github.io/iptv/countries/es.m3u?token=secreto'" > /dev/null
sleep 4; shot 12-setup-playlist
key KEYCODE_BACK 1.5
# Home override self-repair (HomeRepair). Fire OS drops the accessibility service on an update;
# stock Android does not, so the drop is simulated with the process dead, then Sva is reinstalled.
SVC=com.sva.launcher/com.sva.launcher.LauncherOverrideService
adb shell pm grant com.sva.launcher android.permission.WRITE_SECURE_SETTINGS
adb shell settings put secure enabled_accessibility_services "$SVC"
adb shell settings put secure accessibility_enabled 1
sleep 6
adb shell am force-stop com.sva.launcher
adb shell settings put secure enabled_accessibility_services "''"
adb install -r app/build/outputs/apk/debug/app-debug.apk > /dev/null 2>&1
sleep 8
after=$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')
echo "repair after update: $after" | tee "$OUT/home-repair.txt"
case "$after" in *LauncherOverrideService*) echo "REPAIR_OK" >> "$OUT/home-repair.txt" ;; *) echo "REPAIR_FAILED" >> "$OUT/home-repair.txt" ;; esac
# A deliberate "off" while Sva runs must stick, even across an update.
sleep 4
adb shell settings put secure enabled_accessibility_services "''"
sleep 4
adb install -r app/build/outputs/apk/debug/app-debug.apk > /dev/null 2>&1
sleep 8
off=$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')
echo "after deliberate off + update: $off" | tee -a "$OUT/home-repair.txt"
case "$off" in *LauncherOverrideService*) echo "OFF_IGNORED" >> "$OUT/home-repair.txt" ;; *) echo "OFF_RESPECTED" >> "$OUT/home-repair.txt" ;; esac
adb shell dumpsys tv_input > "$OUT/tv_input.txt" 2>&1
adb logcat -d | grep -vE "RuntimeInit|commands.input|Shutting down VM|default boot image|lock profiling" | tail -400 > "$OUT/log.txt"
if grep -q "FATAL EXCEPTION" "$OUT/log.txt"; then echo "CRASH" > "$OUT/CRASHED"; fi
adb shell am force-stop com.sva.launcher
ls -la "$OUT"
