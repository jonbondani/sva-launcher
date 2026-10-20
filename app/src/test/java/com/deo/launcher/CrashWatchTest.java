package com.sva.launcher;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CrashWatchTest {
    private static final long NOW = 10_000_000L;

    @Test public void threeCrashesWithinFiveMinutesTrip() {
        assertTrue(CrashWatch.tripped(Arrays.asList(NOW - 240_000L, NOW - 60_000L, NOW - 1_000L), NOW));
    }

    @Test public void twoCrashesDoNot() {
        assertFalse(CrashWatch.tripped(Arrays.asList(NOW - 60_000L, NOW - 1_000L), NOW));
        assertFalse(CrashWatch.tripped(Collections.emptyList(), NOW));
    }

    @Test public void oldCrashesAreForgotten() {
        assertFalse(CrashWatch.tripped(Arrays.asList(NOW - 900_000L, NOW - 400_000L, NOW - 1_000L), NOW));
    }
}
