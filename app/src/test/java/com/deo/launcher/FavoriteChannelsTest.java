package com.sva.launcher;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class FavoriteChannelsTest {
    @Test public void favouritesKeepPlaylistOrderAndSkipHidden() {
        Set<String> favs = new HashSet<>(Arrays.asList("c", "a", "gone", "h"));
        assertEquals(Arrays.asList("a", "c"), FavoriteChannels.pick(Arrays.asList("a", "b", "c", "h", "a"),
                s -> s, favs, Collections.singleton("h")));
    }

    @Test public void toggleAddsThenRemoves() {
        Set<String> favs = new HashSet<>();
        assertTrue(FavoriteChannels.toggle(favs, "la1"));
        assertTrue(favs.contains("la1"));
        assertFalse(FavoriteChannels.toggle(favs, "la1"));
        assertTrue(favs.isEmpty());
    }

    @Test public void nothingSavedNothingShown() {
        assertTrue(FavoriteChannels.pick(Arrays.asList("a", "b"), s -> s, new HashSet<>(), new HashSet<>()).isEmpty());
    }
}
