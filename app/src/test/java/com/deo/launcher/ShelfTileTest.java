package com.sva.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class ShelfTileTest {
    private static final int DEFAULT = 0xFF9FB4D6;

    @Test public void pureRedLogoGivesRed() {
        int[] pixels = new int[16];
        java.util.Arrays.fill(pixels, 0xFFE50914);
        assertEquals(0xFFE50914, ShelfTile.dominantColor(pixels));
    }

    @Test public void ignoresWhiteBlackAndTransparentPixels() {
        int[] pixels = {0xFFFFFFFF, 0xFF000000, 0x00FF0000, 0x40FF0000, 0xFF1A73E8, 0xFFFFFFFF};
        assertEquals(0xFF1A73E8, ShelfTile.dominantColor(pixels));
    }

    @Test public void greyOrEmptyLogoGivesTheSoftDefault() {
        assertEquals(DEFAULT, ShelfTile.dominantColor(new int[]{0xFF808080, 0xFFFFFFFF, 0xFF101010}));
        assertEquals(DEFAULT, ShelfTile.dominantColor(new int[0]));
    }

    @Test public void moreSaturatedPixelsWeighMore() {
        // Vivid green against a dull, barely saturated olive: the result leans to the green.
        int color = ShelfTile.dominantColor(new int[]{0xFF00C853, 0xFF00C853, 0xFF8A9070});
        int green = (color >> 8) & 0xFF, red = (color >> 16) & 0xFF;
        assertEquals(true, green > 150 && red < 40);
    }
}
