package com.sva.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

public final class SeasonTest {
    @Test public void calendarBoundaries() {
        assertEquals(Season.AUTUMN, Season.forDate(10, 24));
        assertEquals(Season.HALLOWEEN, Season.forDate(10, 25));
        assertEquals(Season.HALLOWEEN, Season.forDate(10, 31));
        assertEquals(Season.HALLOWEEN, Season.forDate(11, 1));
        assertEquals(Season.HALLOWEEN, Season.forDate(11, 2));
        assertEquals(Season.AUTUMN, Season.forDate(11, 3));
        assertEquals(Season.AUTUMN, Season.forDate(11, 30));
        assertEquals(Season.CHRISTMAS, Season.forDate(12, 1));
        assertEquals(Season.CHRISTMAS, Season.forDate(12, 31));
        assertEquals(Season.CHRISTMAS, Season.forDate(1, 1));
        assertEquals(Season.CHRISTMAS, Season.forDate(1, 6));
        assertEquals(Season.WINTER, Season.forDate(1, 7));
        assertEquals(Season.WINTER, Season.forDate(3, 19));
        assertEquals(Season.SPRING, Season.forDate(3, 20));
        assertEquals(Season.SPRING, Season.forDate(6, 20));
        assertEquals(Season.SUMMER, Season.forDate(6, 21));
        assertEquals(Season.SUMMER, Season.forDate(9, 22));
        assertEquals(Season.AUTUMN, Season.forDate(9, 23));
    }

    @Test public void everyDayOfTheYearHasASeason() {
        int[] days = {31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        for (int m = 1; m <= 12; m++) for (int d = 1; d <= days[m - 1]; d++) {
            String season = Season.forDate(m, d);
            assertNotEquals(Season.AUTO, season);
            assertNotEquals(Season.CLASSIC, season);
            assertNotEquals(0, Season.accent(season, true));
            assertNotEquals(0, Season.accent(season, false));
        }
    }

    @Test public void choiceWinsOverTheDate() {
        assertEquals(Season.HALLOWEEN, Season.resolve(Season.AUTO, 11, 1));
        assertEquals(Season.HALLOWEEN, Season.resolve(null, 11, 1));
        assertEquals(Season.SUMMER, Season.resolve("verano", 11, 1));
        assertEquals(Season.CLASSIC, Season.resolve("clasico", 11, 1));
        assertEquals(Season.AUTUMN, Season.resolve("cualquier cosa", 11, 3));
    }

    @Test public void spanishAndEnglishNamesFromTheComputer() {
        assertEquals(Season.AUTUMN, Season.normalize("Otoño"));
        assertEquals(Season.AUTUMN, Season.normalize("otono"));
        assertEquals(Season.CHRISTMAS, Season.normalize(" NAVIDAD "));
        assertEquals(Season.CLASSIC, Season.normalize("clásico"));
        assertEquals(Season.AUTO, Season.normalize("automático"));
        assertEquals(Season.HALLOWEEN, Season.normalize("halloween"));
        assertNull(Season.normalize("pascua"));
        assertNull(Season.normalize(null));
    }

    @Test public void classicKeepsSvaBlueAndHasNoAnimation() {
        assertEquals(0, Season.accent(Season.CLASSIC, true));
        assertEquals(Season.PARTICLES_NONE, Season.particles(Season.CLASSIC));
        assertEquals(Season.PARTICLES_SNOW, Season.particles(Season.CHRISTMAS));
        assertEquals(Season.PARTICLES_LEAVES, Season.particles(Season.HALLOWEEN));
    }

    @Test public void settingsListLinesUp() {
        assertEquals(Season.CHOICES.length, Season.LABELS.length);
        assertEquals(0, Season.index("auto"));
        assertEquals(3, Season.index("Halloween"));
        assertEquals("Navidad", Season.label(Season.CHRISTMAS));
    }
}
