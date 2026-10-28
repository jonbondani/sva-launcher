package com.sva.launcher;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public final class PinnedAppsTest {
    private static List<String[]> apps(String... pairs) {
        List<String[]> out = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) out.add(new String[]{pairs[i], pairs[i + 1]});
        return out;
    }

    @Test public void pinsTheSixInOrderWhateverTheInstallOrder() {
        List<String[]> installed = apps(
                "com.stremio.one", "Stremio",
                "com.amazon.cloud9", "Silk Browser",
                "com.amazon.firebat", "Prime Video",
                "com.disney.disneyplus", "Disney+",
                "com.dazn", "DAZN",
                "es.plus.yomvi", "Movistar Plus+",
                "com.netflix.ninja", "Netflix");
        assertEquals(Arrays.asList("es.plus.yomvi", "com.dazn", "com.disney.disneyplus", "com.amazon.firebat",
                "com.amazon.cloud9", "com.stremio.one"), PinnedApps.pick(installed));
    }

    @Test public void spanishBrowserNameAndAccentsCount() {
        assertEquals(Arrays.asList("b"), PinnedApps.pick(apps("a", "Netflix", "b", "Navegador Silk")));
        assertEquals(Arrays.asList("m"), PinnedApps.pick(apps("m", "MOVISTAR PLUS+")));
    }

    @Test public void missingAppsAreSkipped() {
        assertEquals(Arrays.asList("p", "s"), PinnedApps.pick(apps("p", "Prime Video", "s", "Stremio", "y", "YouTube")));
        assertEquals(new ArrayList<String>(), PinnedApps.pick(new ArrayList<>()));
    }

    @Test public void oneAppFillsOnlyOneSlot() {
        assertEquals(Arrays.asList("x"), PinnedApps.pick(apps("x", "Disney+ Internet")));
    }

    @Test public void emptyPortsSayNoSignal() {
        assertEquals("HDMI 2 · sin señal", TvInputs.menuLabel(new TvInputs.Input("i", "HDMI 2", TvInputs.HDMI, 2, false)));
        assertEquals("Fire TV", TvInputs.menuLabel(new TvInputs.Input("i", "Fire TV", TvInputs.HDMI, 1, true)));
        assertEquals("TDT", TvInputs.menuLabel(new TvInputs.Input("t", "TDT", TvInputs.TUNER, 0, true)));
    }
}
