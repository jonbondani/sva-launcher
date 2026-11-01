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
# Boot catch-up: the stock home is already on screen when the Home override connects (as after a
# power cut). Sva must open by itself within a few seconds.
adb shell input keyevent KEYCODE_HOME; sleep 4
adb shell settings put secure enabled_accessibility_services com.sva.launcher/com.sva.launcher.LauncherOverrideService
adb shell settings put secure accessibility_enabled 1
sleep 9
top=$(adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" | head -1 | tr -d '\r')
echo "uptime: $(adb shell cat /proc/uptime | cut -d' ' -f1)  top: $top" > "$OUT/boot-catch-up.txt"
case "$top" in *com.sva.launcher*) echo "CATCH_UP_OK" >> "$OUT/boot-catch-up.txt" ;; *) echo "CATCH_UP_FAILED" >> "$OUT/boot-catch-up.txt" ;; esac
shot 00-boot-catch-up
adb shell settings put secure enabled_accessibility_services "''"
sleep 3
adb shell am force-stop com.sva.launcher
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
# TV en directo without a list: Up from the dock lands on "Añadir lista", which opens the list settings.
adb shell am force-stop com.sva.launcher
adb shell am start -W -n com.sva.launcher/.MainActivity > /dev/null
sleep 12
key KEYCODE_DPAD_DOWN 1.5; key KEYCODE_DPAD_DOWN 1.5; key KEYCODE_DPAD_RIGHT; key KEYCODE_DPAD_CENTER 3
key KEYCODE_DPAD_UP 1.5; shot 12b-livetv-add-focus
key KEYCODE_DPAD_CENTER 3; shot 12c-livetv-add-opens-settings
key KEYCODE_BACK 2; key KEYCODE_BACK 2
# Seasonal themes, sent the way firetv-optimizer does (sva-tema). 13-theme-auto is today's.
for t in auto otono navidad invierno primavera verano clasico halloween; do
  adb shell am start -n com.sva.launcher/.MainActivity --es set_theme "'$t'" > /dev/null
  sleep 7; shot 13-theme-$t
done
adb shell am start -n com.sva.launcher/.MainActivity --es set_theme "'auto'" > /dev/null
sleep 4
# Stress: a real Spanish list, then the channel row scrolled right as fast as keys can go
# (a viewer reported Sva vanishing while doing this on a Fire TV Stick).
adb shell am force-stop com.sva.launcher
adb shell am start -W -n com.sva.launcher/.MainActivity > /dev/null
sleep 10
adb shell am start -n com.sva.launcher/.MainActivity --es set_playlist "'https://iptv-org.github.io/iptv/countries/es.m3u'" > /dev/null
sleep 3; shot 14a-stress-confirm
# The dialog: try "Guardar" (positive) whichever button starts focused.
key KEYCODE_DPAD_RIGHT 0.8; shot 14b-stress-confirm-right
key KEYCODE_DPAD_CENTER 3
adb shell run-as com.sva.launcher cat shared_prefs/launcher.xml 2>/dev/null | grep -o '<string name="playlist">[^<]*' > "$OUT/stress.txt"
if ! grep -q es.m3u "$OUT/stress.txt"; then
  adb shell am start -n com.sva.launcher/.MainActivity --es set_playlist "'https://iptv-org.github.io/iptv/countries/es.m3u'" > /dev/null
  sleep 3; key KEYCODE_DPAD_LEFT 0.8; key KEYCODE_DPAD_CENTER 3
  adb shell run-as com.sva.launcher cat shared_prefs/launcher.xml 2>/dev/null | grep -o '<string name="playlist">[^<]*' >> "$OUT/stress.txt"
fi
sleep 25
adb logcat -d -b crash > "$OUT/crash-before-stress.txt" 2>&1; grep -q com.sva.launcher "$OUT/crash-before-stress.txt" && echo CRASH > "$OUT/CRASHED"; adb logcat -c
# Dock, TV en directo, up into the first channel.
key KEYCODE_DPAD_DOWN 1; key KEYCODE_DPAD_DOWN 1; key KEYCODE_DPAD_RIGHT; key KEYCODE_DPAD_CENTER 4
# Up lands on the first category tab; focusing a tab selects it. Four to the right is "General"
# (80 channels in the Spanish list), then down into its row.
key KEYCODE_DPAD_UP 1.5
for i in $(seq 1 14); do
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb shell cat /sdcard/ui.xml | grep -o 'text="[^"]*" [^>]*focused="true"' | grep -qE 'text="General +· +80"' && break
  key KEYCODE_DPAD_RIGHT 1
done
echo "category tab: $(adb shell cat /sdcard/ui.xml | grep -o 'text="[^"]*" [^>]*focused="true"' | head -1 | cut -c1-40)" >> "$OUT/stress.txt"
key KEYCODE_DPAD_DOWN 2; shot 14c-stress-row
pid0=$(adb shell pidof com.sva.launcher | tr -d '\r')
# Many key presses in one shell call (no per-key process start): about as fast as a held button.
R=$(printf 'KEYCODE_DPAD_RIGHT %.0s' $(seq 1 40))
for round in 1 2 3; do adb shell input keyevent $R; done
adb shell dumpsys gfxinfo com.sva.launcher | grep -E "Total frames|Janky|percentile" >> "$OUT/stress.txt"
sleep 2; shot 14d-stress-after-right
adb shell input keyevent --longpress $(printf 'KEYCODE_DPAD_RIGHT %.0s' $(seq 1 20))
sleep 2; shot 14e-stress-after-hold
L=$(printf 'KEYCODE_DPAD_LEFT %.0s' $(seq 1 60))
for round in 1 2; do adb shell input keyevent $L; done
sleep 2; shot 14f-stress-after-left
pid1=$(adb shell pidof com.sva.launcher | tr -d '\r')
top=$(adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" | head -2)
{ echo "pid before: $pid0  after: $pid1"; echo "$top"; } >> "$OUT/stress.txt"
adb logcat -d > "$OUT/stress-log.txt"
grep -E "FATAL|AndroidRuntime|OutOfMemory|lowmemorykiller|Process com.sva.launcher|ANR in|signal 11|backtrace" "$OUT/stress-log.txt" | head -80 >> "$OUT/stress.txt"
adb logcat -d -b crash >> "$OUT/stress.txt" 2>&1
adb shell dumpsys meminfo com.sva.launcher | head -40 >> "$OUT/stress.txt"
# Favourites: hold OK on two channels, restart Sva (they must persist), see them in Guardados,
# then hold OK on one there to remove it.
adb shell input keyevent --longpress KEYCODE_DPAD_CENTER; sleep 1; shot 15a-fav-added
key KEYCODE_DPAD_RIGHT 1; key KEYCODE_DPAD_RIGHT 1
adb shell input keyevent --longpress KEYCODE_DPAD_CENTER; sleep 2; shot 15b-fav-added-2
adb shell run-as com.sva.launcher cat shared_prefs/launcher.xml 2>/dev/null | grep -A4 'favoriteChannels' > "$OUT/favourites.txt"
adb shell am force-stop com.sva.launcher
adb shell am start -W -n com.sva.launcher/.MainActivity > /dev/null
sleep 12
key KEYCODE_DPAD_DOWN 1.5; key KEYCODE_DPAD_DOWN 1.5
for i in 1 2 3 4; do key KEYCODE_DPAD_RIGHT 1; done
key KEYCODE_DPAD_CENTER 3; shot 15c-saved
key KEYCODE_DPAD_UP 1.5; shot 15d-saved-focus
adb shell input keyevent --longpress KEYCODE_DPAD_CENTER; sleep 2; shot 15e-saved-removed
adb shell run-as com.sva.launcher cat shared_prefs/launcher.xml 2>/dev/null | grep -A4 'favoriteChannels' >> "$OUT/favourites.txt"
# Home override self-repair (HomeRepair). Fire OS drops the accessibility service on an update;
# stock Android does not, so the drop is simulated with the process dead, then Sva is reinstalled.
SVC=com.sva.launcher/com.sva.launcher.LauncherOverrideService
adb shell pm grant com.sva.launcher android.permission.WRITE_SECURE_SETTINGS
adb shell settings put secure enabled_accessibility_services "$SVC"
adb shell settings put secure accessibility_enabled 1
sleep 6
adb shell dumpsys package com.sva.launcher | grep -i "WRITE_SECURE\|stopped=" >> "$OUT/home-repair-debug.txt"
# Fire OS drops the service around Sva's update (stock Android keeps it): drop it, then update.
adb shell settings put secure enabled_accessibility_services "''"
adb install -r app/build/outputs/apk/debug/app-debug.apk > /dev/null 2>&1
sleep 8
after=$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')
echo "repair after update: $after" | tee "$OUT/home-repair.txt"
adb shell dumpsys package com.sva.launcher | grep -i "WRITE_SECURE\|stopped=" >> "$OUT/home-repair-debug.txt"
adb logcat -d | grep -i "SvaHomeRepair\|LauncherOverride\|MY_PACKAGE_REPLACED" | tail -60 >> "$OUT/home-repair-debug.txt"
case "$after" in *LauncherOverrideService*) echo "REPAIR_OK" >> "$OUT/home-repair.txt" ;; *) echo "REPAIR_FAILED" >> "$OUT/home-repair.txt" ;; esac
# A deliberate "off" while Sva runs must stick, even across a later update (outside the window).
sleep 4
adb shell settings put secure enabled_accessibility_services "''"
sleep 130
adb install -r app/build/outputs/apk/debug/app-debug.apk > /dev/null 2>&1
sleep 8
off=$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')
echo "after deliberate off + update: $off" | tee -a "$OUT/home-repair.txt"
case "$off" in *LauncherOverrideService*) echo "OFF_IGNORED" >> "$OUT/home-repair.txt" ;; *) echo "OFF_RESPECTED" >> "$OUT/home-repair.txt" ;; esac
# After a crash: does the override come back? Newer Android stops rebinding a crashed service;
# Sva's check (10 s after it opens) switches it off and on again.
adb shell settings put secure enabled_accessibility_services "$SVC"
adb shell settings put secure accessibility_enabled 1
sleep 8
bound() { adb shell dumpsys accessibility | grep -iE "Bound services|bound services" | head -1 | tr -d '\r'; }
echo "before crash: $(bound)" > "$OUT/home-crash.txt"
adb shell am crash com.sva.launcher > /dev/null 2>&1
sleep 10
echo "after crash: $(bound)" >> "$OUT/home-crash.txt"
adb shell am start -n com.sva.launcher/.MainActivity > /dev/null
sleep 18
echo "after opening Sva: $(bound)" >> "$OUT/home-crash.txt"
adb shell settings get secure enabled_accessibility_services >> "$OUT/home-crash.txt"
adb logcat -d | grep "SvaHomeRepair" | tail -8 >> "$OUT/home-crash.txt"
case "$(bound)" in *"Sva Launcher"*) echo "REBOUND_OK" >> "$OUT/home-crash.txt" ;; *) echo "REBOUND_FAILED" >> "$OUT/home-crash.txt" ;; esac
adb shell dumpsys tv_input > "$OUT/tv_input.txt" 2>&1
adb logcat -d | grep -vE "RuntimeInit|commands.input|Shutting down VM|default boot image|lock profiling" | tail -400 > "$OUT/log.txt"
if [ "$(grep -c "FATAL EXCEPTION" "$OUT/log.txt")" -gt "$(grep -c "shell-induced crash" "$OUT/log.txt")" ]; then echo "CRASH" > "$OUT/CRASHED"; fi
# Earlier crashes may have scrolled out of the tail above.
adb logcat -d -b crash > "$OUT/crash-buffer.txt" 2>&1
# The crash the HomeRepair test causes on purpose (am crash) does not count.
if grep -v "shell-induced crash" "$OUT/crash-buffer.txt" | grep -q "Process: com.sva.launcher" \
   && [ "$(grep -c "Process: com.sva.launcher" "$OUT/crash-buffer.txt")" -gt "$(grep -c "shell-induced crash" "$OUT/crash-buffer.txt")" ]; then
  echo "CRASH" > "$OUT/CRASHED"
fi
adb shell am force-stop com.sva.launcher
ls -la "$OUT"
