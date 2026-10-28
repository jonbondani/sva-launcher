package com.sva.launcher;

import java.util.ArrayList;
import java.util.List;

/**
 * The apps that are always on Home, in this order, when installed: Movistar+, DAZN, Disney+,
 * Prime Video, a web browser and Stremio. Matched by the app's name (accents and case ignored),
 * so it works whatever the package is called on Fire TV or Android TV.
 */
final class PinnedApps {
    /** Each slot lists the names that count for it, first match wins. */
    static final String[][] SLOTS = {
            {"movistar"},
            {"dazn"},
            {"disney"},
            {"prime video"},
            {"navegador", "browser", "silk", "internet", "chrome", "firefox"},
            {"stremio"},
    };

    private PinnedApps() { }

    /** Pure: {package, label} pairs in, pinned packages out, in slot order. */
    static List<String> pick(List<String[]> apps) {
        List<String> out = new ArrayList<>();
        for (String[] slot : SLOTS) {
            String found = null;
            for (String wanted : slot) {
                for (String[] app : apps) {
                    if (app == null || app.length < 2 || out.contains(app[0])) continue;
                    if (MatchRepository.fold(app[1]).contains(wanted)) { found = app[0]; break; }
                }
                if (found != null) break;
            }
            if (found != null) out.add(found);
        }
        return out;
    }
}
