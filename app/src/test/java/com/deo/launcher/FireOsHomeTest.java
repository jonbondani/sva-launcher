package com.sva.launcher;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class FireOsHomeTest {
    private static final String AMAZON = "com.amazon.tv.launcher";

    @Test public void amazonHomeIsRedirected() {
        assertTrue(LauncherOverrideService.isStockHome(AMAZON, AMAZON + ".ui.HomeActivity_vNext"));
        assertTrue(LauncherOverrideService.isStockHome(AMAZON, AMAZON + ".ui.HomeActivity"));
    }

    @Test public void amazonSettingsAndSideMenuAreLeftAlone() {
        // Fire OS 8 televisions keep these inside Amazon's launcher package.
        assertFalse(LauncherOverrideService.isStockHome(AMAZON, AMAZON + ".ui.MainSettingsActivity"));
        assertFalse(LauncherOverrideService.isStockHome(AMAZON, AMAZON + ".ui.NavigationActivity"));
        assertFalse(LauncherOverrideService.isStockHome(AMAZON, "android.widget.FrameLayout"));
        assertFalse(LauncherOverrideService.isStockHome(AMAZON, null));
    }

    @Test public void googleLaunchersStillCountByPackage() {
        assertTrue(LauncherOverrideService.isStockHome("com.google.android.tvlauncher", null));
        assertTrue(LauncherOverrideService.isStockHome("com.google.android.apps.tv.launcherx", "x.Main"));
    }

    @Test public void otherAppsAreNeverHome() {
        assertFalse(LauncherOverrideService.isStockHome("com.netflix.ninja", "com.netflix.Main"));
        assertFalse(LauncherOverrideService.isStockHome("com.amazon.tv.settings.v2", "x"));
        assertFalse(LauncherOverrideService.isStockHome(null, null));
    }

    @Test public void onlyMediatekInputsOpenInsideSva() {
        assertTrue(HdmiActivity.canShow("com.mediatek.tis/.HdmiInputService/HW2"));
        assertFalse(HdmiActivity.canShow("com.amazon.tv.livetv/.HdmiInput/HW2"));
        assertFalse(HdmiActivity.canShow("demo/hdmi1"));
        assertFalse(HdmiActivity.canShow(null));
    }

    @Test public void allPortsConnectedMeansTheStateIsUseless() {
        assertTrue(TvInputs.bogusConnected(3, 3));
        assertFalse(TvInputs.bogusConnected(3, 2));
        assertFalse(TvInputs.bogusConnected(1, 1));
        assertFalse(TvInputs.bogusConnected(0, 0));
    }
}
