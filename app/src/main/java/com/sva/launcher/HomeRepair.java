package com.sva.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.provider.Settings;

/**
 * Puts Sva's Home override back after Fire OS drops it. Two known ways that happens: an update of
 * Sva (reinstalling clears the app from the enabled accessibility services) and a force-stop
 * (cleared too, noticed at the next boot). Documented by CleanYourTV on Fire OS 7 and 8.
 *
 * Only ever restores what the viewer had on: the service records it while bound and clears it
 * when it is switched off from Settings or the script (onUnbind), so a deliberate "off" sticks.
 * It needs WRITE_SECURE_SETTINGS, granted once over ADB by firetv-optimizer.sh sva-inicio;
 * without it nothing happens.
 */
public final class HomeRepair extends BroadcastReceiver {
    static final String PREF_WANTED = "homeOverrideWanted";
    static final String SERVICE = "com.sva.launcher/com.sva.launcher.LauncherOverrideService";
    private static final String TAG = "SvaHomeRepair";

    @Override public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        android.util.Log.i(TAG, "received " + action);
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) || Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.LOCKED_BOOT_COMPLETED".equals(action)) {
            repair(context);
        }
    }

    static void markWanted(Context context, boolean wanted) {
        prefs(context).edit().putBoolean(PREF_WANTED, wanted).commit();
        android.util.Log.i(TAG, "wanted=" + wanted);
    }

    /** Re-adds the service if the viewer wanted it and it is missing. Returns true if it wrote. */
    static boolean repair(Context context) {
        if (!prefs(context).getBoolean(PREF_WANTED, false)) { android.util.Log.i(TAG, "not wanted"); return false; }
        if (context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED) {
            android.util.Log.i(TAG, "no WRITE_SECURE_SETTINGS");
            return false;
        }
        try {
            String current = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            String enabled = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED);
            String wanted = withService(current, SERVICE);
            boolean listChanged = !wanted.equals(current == null ? "" : current);
            boolean switchOff = !"1".equals(enabled);
            android.util.Log.i(TAG, "list=" + current + " enabled=" + enabled);
            if (!listChanged && !switchOff) return false;
            if (listChanged) Settings.Secure.putString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, wanted);
            if (switchOff) Settings.Secure.putString(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, "1");
            return true;
        } catch (RuntimeException notAllowed) {
            android.util.Log.w(TAG, "could not write", notAllowed);
            return false;
        }
    }

    /** Pure: the enabled-services list with {@code service} in it, others kept in order. */
    static String withService(String current, String service) {
        if (current == null || current.trim().isEmpty() || "null".equals(current.trim())) return service;
        for (String part : current.split(":")) if (part.trim().equalsIgnoreCase(service)) return current;
        return current + ":" + service;
    }

    private static SharedPreferences prefs(Context context) {
        Context device = context.createDeviceProtectedStorageContext();
        return device.getSharedPreferences("home_repair", Context.MODE_PRIVATE);
    }
}
