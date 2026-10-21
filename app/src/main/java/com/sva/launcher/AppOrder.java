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
}
