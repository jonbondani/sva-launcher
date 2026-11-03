package com.sva.launcher;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class UpdaterChangesTest {
    @Test public void readsTheChangesList() throws Exception {
        Updater.Release r = Updater.parse("{\"versionCode\": 132, \"versionName\": \"4.0.1-firetv.16\", "
                + "\"changes\": [\"Canales favoritos\", \"  \", \"Interruptor de ADB\"]}");
        assertEquals(132, r.versionCode);
        assertEquals("4.0.1-firetv.16", r.versionName);
        assertEquals(Arrays.asList("Canales favoritos", "Interruptor de ADB"), r.changes);
    }

    @Test public void olderVersionJsonWithoutChangesStillWorks() throws Exception {
        Updater.Release r = Updater.parse("{\"versionCode\": 130, \"versionName\": \"4.0.1-firetv.15\", \"sha\": \"x\"}");
        assertEquals(130, r.versionCode);
        assertTrue(r.changes.isEmpty());
        assertEquals("", Updater.changesText(r.changes));
    }

    @Test public void atMostEightLinesAndBulleted() throws Exception {
        StringBuilder json = new StringBuilder("{\"versionCode\": 1, \"changes\": [");
        for (int i = 0; i < 12; i++) json.append(i == 0 ? "" : ",").append("\"c").append(i).append('"');
        json.append("]}");
        Updater.Release r = Updater.parse(json.toString());
        assertEquals(Updater.MAX_CHANGES, r.changes.size());
        assertEquals("Novedades:\n• a\n• b", Updater.changesText(Arrays.asList("a", "b")));
        assertEquals("", Updater.changesText(Collections.emptyList()));
    }
}
