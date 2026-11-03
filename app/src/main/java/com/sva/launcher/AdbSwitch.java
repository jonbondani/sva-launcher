package com.sva.launcher;

import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;

/**
 * Network ADB on and off from the TV, so port 5555 is open only while the computer needs it
 * (firetv-optimizer, sideloading). Idea from tvOS Launcher 2.1.0 (its one-tap ADB toggle).
 * Uses WRITE_SECURE_SETTINGS, granted once by firetv-optimizer.sh sva-inicio. Deliberately not
 * reachable from an intent: only someone holding the remote can open ADB.
 */
final class AdbSwitch {
    private AdbSwitch() { }

    static boolean canChange(Context context) {
        return context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED;
    }

    static boolean isOn(Context context) {
        return Settings.Global.getInt(context.getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1;
    }

    /** Returns true when the system took the change. */
    static boolean set(Context context, boolean on) {
        if (!canChange(context)) return false;
        try {
            Settings.Global.putInt(context.getContentResolver(), Settings.Global.ADB_ENABLED, on ? 1 : 0);
            return isOn(context) == on;
        } catch (RuntimeException refused) {
            return false;
        }
    }
}
