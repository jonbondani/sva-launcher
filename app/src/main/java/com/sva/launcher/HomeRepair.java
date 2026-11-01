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
 * An update may unbind the service just before replacing the app, so an unbind followed within
 * {@link #UPDATE_WINDOW_MS} by Sva's own update counts as the update, not as the viewer.
 * It needs WRITE_SECURE_SETTINGS, granted once over ADB by firetv-optimizer.sh sva-inicio;
 * without it nothing happens.
 */
public final class HomeRepair extends BroadcastReceiver {
    static final String PREF_WANTED = "homeOverrideWanted";
    static final String PREF_UNBOUND_AT = "homeOverrideUnboundAt";
    static final String PREF_WANTED_BEFORE_UNBIND = "homeOverrideWantedBeforeUnbind";
    static final long UPDATE_WINDOW_MS = 120_000L;
    static final String SERVICE = "com.sva.launcher/com.sva.launcher.LauncherOverrideService";
    /** Sent to ourselves a while after boot to check the override really came back. */
    static final String ACTION_CHECK = "com.sva.launcher.CHECK_HOME_OVERRIDE";
    static final long BOOT_CHECK_DELAY_MS = 60_000L;
    /** Time between taking the service out of the list and putting it back, so the system sees both. */
    static final long REBIND_GAP_MS = 1_500L;
    private static final String TAG = "SvaHomeRepair";

    @Override public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        android.util.Log.i(TAG, "received " + action);
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            SharedPreferences p = prefs(context);
            if (!p.getBoolean(PREF_WANTED, false) && unboundByUpdate(p.getBoolean(PREF_WANTED_BEFORE_UNBIND, false),
                    p.getLong(PREF_UNBOUND_AT, 0L), System.currentTimeMillis())) {
                android.util.Log.i(TAG, "unbound by the update: restoring");
                p.edit().putBoolean(PREF_WANTED, true).commit();
            }
            repair(context);
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.LOCKED_BOOT_COMPLETED".equals(action)) {
            repair(context);
            if (Intent.ACTION_BOOT_COMPLETED.equals(action)) scheduleCheck(context, BOOT_CHECK_DELAY_MS);
        } else if (ACTION_CHECK.equals(action)) {
            PendingResult pending = goAsync();
            rebindIfStuck(context, pending::finish);
        }
    }

    /** Pure: an unbind of a wanted service shortly before our own update was the update's doing. */
    static boolean unboundByUpdate(boolean wantedBefore, long unboundAt, long now) {
        return wantedBefore && unboundAt > 0 && now >= unboundAt && now - unboundAt <= UPDATE_WINDOW_MS;
    }

    /** The service was unbound: remember whether it was wanted, and when, before clearing it. */
    static void markUnbound(Context context) {
        SharedPreferences p = prefs(context);
        p.edit().putBoolean(PREF_WANTED_BEFORE_UNBIND, p.getBoolean(PREF_WANTED, false))
                .putLong(PREF_UNBOUND_AT, System.currentTimeMillis()).putBoolean(PREF_WANTED, false).commit();
        android.util.Log.i(TAG, "unbound");
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

    /**
     * Pure: the override is listed and switched on, the viewer wants it, yet the system has not
     * bound it. Happens after Sva crashes: newer Android versions stop rebinding a service whose
     * process died until it is switched off and on again.
     */
    static boolean stuck(boolean wanted, boolean listed, boolean switchedOn, boolean connected) {
        return wanted && listed && switchedOn && !connected;
    }

    static void scheduleCheck(Context context, long delayMs) {
        try {
            android.app.AlarmManager alarms = (android.app.AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent check = new Intent(context, HomeRepair.class).setAction(ACTION_CHECK);
            android.app.PendingIntent pending = android.app.PendingIntent.getBroadcast(context, 7, check,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
            alarms.set(android.app.AlarmManager.ELAPSED_REALTIME,
                    android.os.SystemClock.elapsedRealtime() + delayMs, pending);
            android.util.Log.i(TAG, "check scheduled in " + delayMs + " ms");
        } catch (RuntimeException e) {
            android.util.Log.w(TAG, "could not schedule check", e);
        }
    }

    /**
     * If the override is stuck (see {@link #stuck}), takes it out of the enabled list and puts it
     * back, which makes the system bind it again. {@code done} runs when finished either way.
     */
    static void rebindIfStuck(Context context, Runnable done) {
        boolean wrote = false;
        try {
            if (context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED) return;
            String current = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            String enabled = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED);
            boolean listed = current != null && withService(current, SERVICE).equals(current);
            boolean isStuck = stuck(prefs(context).getBoolean(PREF_WANTED, false), listed, "1".equals(enabled),
                    LauncherOverrideService.isConnected());
            android.util.Log.i(TAG, "check: listed=" + listed + " enabled=" + enabled
                    + " connected=" + LauncherOverrideService.isConnected() + " stuck=" + isStuck);
            if (!isStuck) return;
            Settings.Secure.putString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    withoutService(current, SERVICE));
            wrote = true;
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                try {
                    String now = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                    Settings.Secure.putString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                            withService(now, SERVICE));
                    android.util.Log.i(TAG, "rebound");
                } catch (RuntimeException e) {
                    android.util.Log.w(TAG, "could not put the service back", e);
                } finally {
                    if (done != null) done.run();
                }
            }, REBIND_GAP_MS);
        } catch (RuntimeException e) {
            android.util.Log.w(TAG, "check failed", e);
        } finally {
            if (!wrote && done != null) done.run();
        }
    }

    /** Pure: the enabled-services list without {@code service}. */
    static String withoutService(String current, String service) {
        if (current == null) return "";
        StringBuilder out = new StringBuilder();
        for (String part : current.split(":")) {
            if (part.trim().isEmpty() || part.trim().equalsIgnoreCase(service)) continue;
            if (out.length() > 0) out.append(':');
            out.append(part);
        }
        return out.toString();
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
