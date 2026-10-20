package com.sva.launcher;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;

/** Shared palette (neutral graphite and system blue, after tvOS), typography and focus motion. */
final class LiquidTheme {
    private static Typeface semiboldFont;
    static final String PREF_MODE = "liquidGlassMode";
    static final String SYSTEM = "system";
    static final String LIGHT = "light";
    static final String DARK = "dark";

    private LiquidTheme() { }

    private static Typeface regularFont, boldFont, displayFont;

    /**
     * Interface text: Inter (a free typeface in the spirit of Apple's SF Pro), semibold like tvOS labels.
     * Falls back to the bundled Manrope if the variable font cannot be loaded.
     */
    static Typeface font(Context context) {
        synchronized (LiquidTheme.class) {
            if (semiboldFont == null) semiboldFont = inter(context, 600);
            return semiboldFont;
        }
    }

    /** Body text and secondary lines. */
    static Typeface regular(Context context) {
        synchronized (LiquidTheme.class) {
            if (regularFont == null) regularFont = inter(context, 450);
            return regularFont;
        }
    }

    /** Headings and titles, bold like tvOS section titles. */
    static Typeface bold(Context context) {
        synchronized (LiquidTheme.class) {
            if (boldFont == null) boldFont = inter(context, 700);
            return boldFont;
        }
    }

    /** Large numerals (the clock): light and wide, as on the Apple TV screensaver. */
    static Typeface display(Context context) {
        synchronized (LiquidTheme.class) {
            if (displayFont == null) displayFont = inter(context, 300);
            return displayFont;
        }
    }

    private static Typeface inter(Context context, int weight) {
        try {
            Typeface face = new Typeface.Builder(context.getAssets(), "fonts/InterVariable.ttf")
                    .setFontVariationSettings("'wght' " + weight)
                    .build();
            if (face != null) return face;
        } catch (RuntimeException ignored) { }
        try {
            return context.getResources().getFont(R.font.manrope_semibold);
        } catch (RuntimeException e) {
            return Typeface.DEFAULT;
        }
    }

    static int alertDialogTheme(Context context) {
        return isLight(context) ? R.style.SvaLightAlertDialogTheme : R.style.SvaAlertDialogTheme;
    }

    static boolean isLight(Context context) {
        String mode = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
                .getString(PREF_MODE, SYSTEM);
        if (LIGHT.equals(mode)) return true;
        if (DARK.equals(mode)) return false;
        int nightMask = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMask == Configuration.UI_MODE_NIGHT_NO;
    }

    static int background(Context context) { return isLight(context) ? 0xFFF2F2F7 : 0xFF0B0B0F; }
    static int surface(Context context) { return isLight(context) ? 0xE9FFFFFF : 0xB81C1C1E; }
    static int raisedSurface(Context context) { return isLight(context) ? 0xFFFFFFFF : 0xE02C2C2E; }
    static int text(Context context) { return isLight(context) ? 0xFF1C1C1E : 0xFFFFFFFF; }
    static int secondaryText(Context context) { return isLight(context) ? 0xFF6E6E73 : 0xFFAEAEB2; }
    static int border(Context context) { return isLight(context) ? 0x26000000 : 0x40FFFFFF; }
    static int accent(Context context) { return isLight(context) ? 0xFF007AFF : 0xFF409CFF; }
    static int lavender(Context context) { return secondaryText(context); }

    static Drawable scenicBackground(Context context) {
        Drawable photo = context.getDrawable(R.drawable.mountain_wallpaper);
        GradientDrawable veil = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                isLight(context)
                        ? new int[]{0xB8F2F2F7, 0xE8F2F2F7, 0xF4F2F2F7}
                        : new int[]{0x8C050507, 0xC80A0A0D, 0xF40B0B0F});
        return new LayerDrawable(new Drawable[]{photo, veil});
    }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static void animateFocus(android.view.View view, boolean focused) {
        view.animate().cancel();
        // tvOS-style focus: the item lifts towards the viewer (slight zoom and a deeper shadow).
        float scale = focused ? 1.06f : 1f;
        view.animate().scaleX(scale).scaleY(scale)
                .translationZ(focused ? dp(view.getContext(), 14) : 0)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f))
                .setDuration(android.animation.ValueAnimator.areAnimatorsEnabled() ? 200 : 0).start();
    }

    /** Focus animation for controls contained by a fixed rounded panel. */
    static void animateContainedFocus(android.view.View view, boolean focused) {
        view.animate().cancel();
        view.animate().scaleX(1f).scaleY(1f)
                .translationZ(focused ? dp(view.getContext(), 4) : 0)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .setDuration(android.animation.ValueAnimator.areAnimatorsEnabled() ? 140 : 0).start();
    }
}
