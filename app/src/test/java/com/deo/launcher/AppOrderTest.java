package com.sva.launcher;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public final class AppOrderTest {
    private static final List<String> ROW = Arrays.asList("movistar", "disney", "dazn", "prime");

    @Test public void movesRightAndLeft() {
        assertEquals(Arrays.asList("disney", "movistar", "dazn", "prime"), AppOrder.move(ROW, "movistar", 1));
        assertEquals(Arrays.asList("movistar", "dazn", "disney", "prime"), AppOrder.move(ROW, "dazn", -1));
    }

    @Test public void staysPutAtTheEnds() {
        assertEquals(ROW, AppOrder.move(ROW, "movistar", -1));
        assertEquals(ROW, AppOrder.move(ROW, "prime", 1));
    }

    @Test public void unknownAppChangesNothing() {
        assertEquals(ROW, AppOrder.move(ROW, "netflix", 1));
    }
}
