package com.sva.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class HomeRepairTest {
    private static final String SVA = HomeRepair.SERVICE;

    @Test public void addsSvaToAnEmptyList() {
        assertEquals(SVA, HomeRepair.withService(null, SVA));
        assertEquals(SVA, HomeRepair.withService("", SVA));
        assertEquals(SVA, HomeRepair.withService("null", SVA));
    }

    @Test public void keepsOtherServicesAndTheirOrder() {
        assertEquals("a/.A:b/.B:" + SVA, HomeRepair.withService("a/.A:b/.B", SVA));
    }

    @Test public void leavesTheListAloneWhenSvaIsThere() {
        String list = "a/.A:" + SVA + ":b/.B";
        assertEquals(list, HomeRepair.withService(list, SVA));
    }

    @Test public void unbindJustBeforeTheUpdateIsTheUpdate() {
        long t = 1_000_000L;
        org.junit.Assert.assertTrue(HomeRepair.unboundByUpdate(true, t, t + 5_000));
        org.junit.Assert.assertTrue(HomeRepair.unboundByUpdate(true, t, t + HomeRepair.UPDATE_WINDOW_MS));
    }

    @Test public void anOldOrUnwantedUnbindIsTheViewer() {
        long t = 1_000_000L;
        org.junit.Assert.assertFalse(HomeRepair.unboundByUpdate(true, t, t + HomeRepair.UPDATE_WINDOW_MS + 1));
        org.junit.Assert.assertFalse(HomeRepair.unboundByUpdate(false, t, t + 1_000));
        org.junit.Assert.assertFalse(HomeRepair.unboundByUpdate(true, 0L, t));
        org.junit.Assert.assertFalse(HomeRepair.unboundByUpdate(true, t, t - 1));
    }
}
