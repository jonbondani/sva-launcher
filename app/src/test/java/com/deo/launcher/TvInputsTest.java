package com.sva.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class TvInputsTest {
    @Test public void portNumberComesFromTheTvLabel() {
        assertEquals(2, TvInputs.portNumber("HDMI 2", 1));
        assertEquals(3, TvInputs.portNumber("HDMI3 (eARC)", 1));
        assertEquals(1, TvInputs.portNumber("hdmi 1", 4));
        assertEquals(4, TvInputs.portNumber("Entrada 4", 1));
    }

    @Test public void portNumberFallsBackToPosition() {
        assertEquals(2, TvInputs.portNumber("Game console", 2));
        assertEquals(3, TvInputs.portNumber(null, 3));
    }

    @Test public void customNameWinsThenDeviceNameThenPort() {
        assertEquals("Fire TV", TvInputs.hdmiLabel("Fire TV", "PlayStation 5", 1));
        assertEquals("PlayStation 5", TvInputs.hdmiLabel(null, "PlayStation 5", 1));
        assertEquals("PlayStation 5", TvInputs.hdmiLabel("  ", " PlayStation 5 ", 1));
        assertEquals("HDMI 2", TvInputs.hdmiLabel(null, null, 2));
        assertEquals("HDMI 2", TvInputs.hdmiLabel("", "", 2));
    }

    @Test public void aDeviceThatOnlyReportsThePortKeepsThePortName() {
        assertEquals("HDMI 3", TvInputs.hdmiLabel(null, "HDMI 3", 3));
    }
}
