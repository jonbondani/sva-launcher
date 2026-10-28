package com.sva.launcher;

import android.content.ComponentName;
import android.content.Intent;

import java.util.ArrayList;
import java.util.List;

/**
 * The ways to open the TV's own Settings, best first. On Fire OS, Settings.ACTION_SETTINGS is
 * answered by a stub ("CTSDummy") on some builds, and on Fire OS 8 televisions the real Settings
 * grid is MainSettingsActivity inside Amazon's launcher, exported without a permission
 * (documented by CleanYourTV on a Hisense Fire OS 8 set). Sva's Home override only reacts to
 * Amazon's HomeActivity, so opening this one does not bounce back to Sva.
 */
final class FireSettings {
    static final String MAIN_SETTINGS_ACTION = "amazon.intent.action.MAINSETTINGS";

    private FireSettings() { }

    static List<Intent> candidates(boolean fireOs8) {
        List<Intent> out = new ArrayList<>();
        if (fireOs8) {
            out.add(new Intent(MAIN_SETTINGS_ACTION));
            out.add(new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(
                    LauncherOverrideService.AMAZON_LAUNCHER,
                    LauncherOverrideService.AMAZON_LAUNCHER + ".ui.MainSettingsActivity")));
        }
        out.add(new Intent(android.provider.Settings.ACTION_SETTINGS));
        out.add(new Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.LEANBACK_SETTINGS"));
        return out;
    }
}
