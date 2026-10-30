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
}
