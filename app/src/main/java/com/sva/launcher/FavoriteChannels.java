package com.sva.launcher;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Favourite channels, kept by their stable key. Pure, so the rules can be tested off the device. */
final class FavoriteChannels {
    static final String PREF = "favoriteChannels";

    private FavoriteChannels() { }

    /** The favourites in playlist order, without hidden channels and without repeats. */
    static <T> List<T> pick(List<T> channels, Function<T, String> key, Set<String> favourites, Set<String> hidden) {
        List<T> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (T channel : channels) {
            String k = key.apply(channel);
            if (k == null || !favourites.contains(k) || hidden.contains(k) || !seen.add(k)) continue;
            out.add(channel);
        }
        return out;
    }

    /** Adds or removes a key; returns true when it is now a favourite. */
    static boolean toggle(Set<String> favourites, String key) {
        if (favourites.remove(key)) return false;
        favourites.add(key);
        return true;
    }
}
