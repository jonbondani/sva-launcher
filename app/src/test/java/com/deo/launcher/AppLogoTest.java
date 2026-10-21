package com.sva.launcher;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class AppLogoTest {
    private static final int N = 20;
    private static final int BLUE = 0xFF0A5EB0, WHITE = 0xFFFFFFFF, RED = 0xFFE50914;

    /** A flat icon: one backdrop colour with a 6x6 mark in the middle. */
    private static int[] flatIcon(int backdrop, int mark) {
        int[] px = new int[N * N];
        Arrays.fill(px, backdrop);
        for (int y = 7; y < 13; y++) for (int x = 7; x < 13; x++) px[y * N + x] = mark;
        return px;
    }

    @Test public void cutsTheMarkOutOfAFlatIcon() {
        AppLogo.Cut cut = AppLogo.cut(flatIcon(BLUE, WHITE), N, N);
        assertEquals(BLUE, cut.backdrop);
        assertTrue(cut.usable());
        assertEquals(WHITE, cut.pixels[10 * N + 10]);
        assertEquals(0, cut.pixels[0]);
        assertEquals(0, cut.pixels[3 * N + 15]);
        assertArrayEquals(new int[]{7, 7, 12, 12}, AppLogo.bounds(cut.pixels, N, N));
    }

    @Test public void colourfulBackdropGivesTheGlowColour() {
        AppLogo.Cut cut = AppLogo.cut(flatIcon(BLUE, WHITE), N, N);
        assertEquals(BLUE, AppLogo.glowColor(cut.backdrop, cut.pixels));
    }

    @Test public void whiteBackdropLetsTheMarkColourTheGlow() {
        AppLogo.Cut cut = AppLogo.cut(flatIcon(WHITE, RED), N, N);
        assertEquals(WHITE, cut.backdrop);
        assertEquals(RED, AppLogo.glowColor(cut.backdrop, cut.pixels));
    }

    @Test public void roundIconWithClearCornersStillFindsItsBackdrop() {
        int[] px = flatIcon(BLUE, WHITE);
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            double dx = x - 9.5, dy = y - 9.5;
            if (dx * dx + dy * dy > 10 * 10) px[y * N + x] = 0;
        }
        AppLogo.Cut cut = AppLogo.cut(px, N, N);
        assertEquals(BLUE, cut.backdrop);
        assertTrue(cut.usable());
    }

    @Test public void busyArtworkIsNotTrustedAsAMark() {
        int[] px = new int[N * N];
        java.util.Random random = new java.util.Random(7);
        for (int i = 0; i < px.length; i++) px[i] = 0xFF000000 | random.nextInt(0xFFFFFF);
        assertFalse(AppLogo.cut(px, N, N).usable());
    }

    @Test public void plainSquareHasNoMark() {
        int[] px = new int[N * N];
        Arrays.fill(px, BLUE);
        AppLogo.Cut cut = AppLogo.cut(px, N, N);
        assertFalse(cut.usable());
        assertNull(AppLogo.bounds(cut.pixels, N, N));
    }

    @Test public void blurSpreadsLightWithoutDarkeningItsColour() {
        int w = 21;
        int[] px = new int[w * w];
        px[10 * w + 10] = RED;
        int[] out = AppLogo.blur(px, w, w, 2);
        int centre = out[10 * w + 10];
        assertEquals(RED & 0xFFFFFF, centre & 0xFFFFFF);
        assertTrue((centre >>> 24) > 0 && (centre >>> 24) < 255);
        assertEquals(RED & 0xFFFFFF, out[10 * w + 13] & 0xFFFFFF);
        assertEquals(out[10 * w + 7], out[10 * w + 13]);
        assertEquals(out[7 * w + 10], out[13 * w + 10]);
        assertEquals(0, out[0]);
    }

    @Test public void faintShadowPixelsAreDroppedAndSolidOnesKept() {
        int[] out = AppLogo.dropFaint(new int[]{0x33000000, 0x50FFFFFF, 0xFFFFFFFF, 0xC0E50914});
        assertEquals(0, out[0]);
        assertEquals(0, out[1]);
        assertEquals(WHITE, out[2]);
        assertEquals(0xE50914, out[3] & 0xFFFFFF);
        assertTrue((out[3] >>> 24) > 0x80 && (out[3] >>> 24) < 0xC0);
    }
}
