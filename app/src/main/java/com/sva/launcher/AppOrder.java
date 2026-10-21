package com.sva.launcher;

import java.util.ArrayList;
import java.util.List;

/** Moving one app left or right in the home row (kept apart so it can be unit tested). */
final class AppOrder {
    private AppOrder() { }

    /** Returns a new order with {@code pkg} moved {@code delta} places, clamped to the ends. */
    static List<String> move(List<String> order, String pkg, int delta) {
        List<String> out = new ArrayList<>(order);
        int from = out.indexOf(pkg);
        if (from < 0 || delta == 0) return out;
        int to = Math.max(0, Math.min(out.size() - 1, from + delta));
        if (to == from) return out;
        out.remove(from);
        out.add(to, pkg);
        return out;
    }

    /**
     * Makes sure every app in {@code pinned} (Movistar+, Disney+, DAZN, Prime Video when installed)
     * is in the row: missing ones go to the front, in pinned order; the rest keeps its order, so a
     * pinned app the viewer has moved stays where they put it.
     */
    static List<String> withPinned(List<String> order, List<String> pinned) {
        List<String> out = new ArrayList<>(order);
        int at = 0;
        for (String pkg : pinned) {
            if (out.contains(pkg)) continue;
            out.add(Math.min(at, out.size()), pkg);
            at++;
        }
        return out;
    }
}
