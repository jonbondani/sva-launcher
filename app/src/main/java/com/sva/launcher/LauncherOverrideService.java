package com.sva.launcher;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityOptions;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * Reopens Sva when the TV's built-in launcher handles the Home button.
 *
 * Android holds back a plain startActivity right after a HOME press (the five-second
 * "app switch" delay), which is why Amazon's home used to show for a moment. A PendingIntent
 * fired by an alarm is sent by the system and is exempt, so Sva is reopened through an exact
 * alarm. It fires at once only when the global {@code alarm_manager_constants} contains
 * {@code min_futurity=0} (set over ADB by {@code firetv-optimizer.sh sva-inicio}); without it
 * the alarm still works, just a few seconds later. Meanwhile the stock home is covered with a
 * plain overlay so it is never seen. Technique adapted from dumbtv (MIT).
 */
public final class LauncherOverrideService extends AccessibilityService {
    private static final long REOPEN_THROTTLE_MS = 1000L;
    /** Upper bound for the cover, so a failed launch can never leave the screen blank. */
    private static final long COVER_MAX_MS = 1500L;
    private static final long UNCOVER_DELAY_MS = 120L;
    private static final int COVER_COLOR = 0xFF0B0F17;
    private static final String[] STOCK_LAUNCHERS = {
            "com.google.android.tvlauncher",
            "com.google.android.apps.tv.launcherx",
            "com.google.android.leanbacklauncher",
            "com.google.android.leanbacklauncher.recommendations",
            // Fire OS (Fire TV Stick, Cube and Fire TV Edition televisions). Fire OS
            // ignores third-party HOME activities, so this override is the only route.
            "com.amazon.tv.launcher"
    };
    private long lastReopenAt;
    /** The running service, so Sva can ask the system for its power menu (restart / power off). */
    private static volatile LauncherOverrideService running;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable uncover = this::uncover;
    private View cover;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        running = this;
        try {
            View view = new View(this);
            view.setBackgroundColor(COVER_COLOR);
            view.setVisibility(View.GONE);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.OPAQUE);
            lp.windowAnimations = 0;
            ((WindowManager) getSystemService(WINDOW_SERVICE)).addView(view, lp);
            cover = view;
        } catch (Exception noOverlay) {
            cover = null;   // Still works, the stock home is just visible for a moment.
        }
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(uncover);
        if (cover != null) {
            try { ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(cover); } catch (Exception ignored) { }
            cover = null;
        }
        if (running == this) running = null;
        super.onDestroy();
    }

    /** Opens the system power menu; false when the accessibility service is not enabled. */
    static boolean showPowerMenu() {
        LauncherOverrideService service = running;
        return service != null && service.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
    }

    /** True when the alarm used to reopen Sva fires immediately (min_futurity=0 set over ADB). */
    static boolean instantHome(Context context) {
        try {
            String constants = Settings.Global.getString(context.getContentResolver(), "alarm_manager_constants");
            return constants != null && constants.replace(" ", "").contains("min_futurity=0");
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence packageName = event.getPackageName();
        if (packageName == null) return;
        String pkg = packageName.toString();

        if (!isStockLauncher(pkg)) {
            if ("com.android.systemui".equals(pkg) || "android".equals(pkg)) return;
            if (getPackageName().equals(pkg)) {
                // Sva is on screen: drop the cover once its first frame has had time to draw.
                handler.removeCallbacks(uncover);
                handler.postDelayed(uncover, UNCOVER_DELAY_MS);
            } else {
                // The viewer opened something else: a queued reopen must not pull them back.
                cancelReopen();
                uncover();
            }
            return;
        }

        long now = SystemClock.uptimeMillis();
        if (now - lastReopenAt < REOPEN_THROTTLE_MS) return;
        lastReopenAt = now;
        showCover();
        reopen();
    }

    private void reopen() {
        try {
            AlarmManager alarms = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (android.os.Build.VERSION.SDK_INT >= 31 && !alarms.canScheduleExactAlarms()) {
                startActivity(homeIntent());   // Android 12+ without exact alarms (not Fire OS).
                return;
            }
            alarms.setExact(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime(), reopenIntent());
        } catch (Exception noExactAlarms) {
            // Android 12+ without the exact-alarm permission: fall back to a direct start.
            try { startActivity(homeIntent()); } catch (Exception ignored) { uncover(); }
        }
    }

    private void cancelReopen() {
        try {
            ((AlarmManager) getSystemService(Context.ALARM_SERVICE)).cancel(reopenIntent());
        } catch (Exception ignored) { }
    }

    private Intent homeIntent() {
        return new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    private PendingIntent reopenIntent() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        android.os.Bundle options = ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle();
        return PendingIntent.getActivity(this, 0, homeIntent(), flags, options);
    }

    private void showCover() {
        if (cover == null) return;
        cover.setVisibility(View.VISIBLE);
        handler.removeCallbacks(uncover);
        handler.postDelayed(uncover, COVER_MAX_MS);
    }

    private void uncover() {
        handler.removeCallbacks(uncover);
        if (cover != null) cover.setVisibility(View.GONE);
    }

    private boolean isStockLauncher(String packageName) {
        for (String candidate : STOCK_LAUNCHERS) {
            if (candidate.equals(packageName)) return true;
        }
        return false;
    }

    @Override public void onInterrupt() { }
}
