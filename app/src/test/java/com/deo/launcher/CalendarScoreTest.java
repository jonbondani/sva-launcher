package com.sva.launcher;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public final class CalendarScoreTest {
    @Test public void scoreAtTheEndOfTheTitle() {
        assertArrayEquals(new String[]{"0", "0"}, LiveScores.titleScore("⚽ Marbella FC - Mijas-Las Lagunas (0-0)"));
        assertArrayEquals(new String[]{"2", "1"}, LiveScores.titleScore("Málaga CF - Racing (2 - 1)"));
        assertArrayEquals(new String[]{"10", "3"}, LiveScores.titleScore("A - B (10–3)"));
    }

    @Test public void noScoreMeansEmpty() {
        assertArrayEquals(new String[]{"", ""}, LiveScores.titleScore("Getafe - Sevilla"));
        assertArrayEquals(new String[]{"", ""}, LiveScores.titleScore("Getafe - Sevilla (aplazado)"));
        assertArrayEquals(new String[]{"", ""}, LiveScores.titleScore(null));
    }

    @Test public void teamNamesLoseTheEmojiAndScore() {
        assertArrayEquals(new String[]{"Marbella FC", "Mijas-Las Lagunas"},
                LiveScores.splitTitle("⚽ Marbella FC - Mijas-Las Lagunas (0-0)"));
        assertEquals("Real Madrid", LiveScores.teamName("🏆  Real Madrid"));
        assertEquals("1860 Múnich", LiveScores.teamName("1860 Múnich"));
    }
}
