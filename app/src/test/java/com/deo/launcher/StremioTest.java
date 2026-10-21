package com.sva.launcher;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class StremioTest {
    private static JSONObject item(String id, String type, long offset, long duration, String video, Object watched, boolean removed)
            throws Exception {
        return new JSONObject().put("_id", id).put("type", type).put("name", id).put("removed", removed)
                .put("state", new JSONObject().put("timeOffset", offset).put("duration", duration)
                        .put("video_id", video).put("lastWatched", watched));
    }

    @Test public void keepsStartedUnfinishedItemsNewestFirst() throws Exception {
        JSONArray library = new JSONArray()
                .put(item("old", "series", 100_000, 2_000_000, "old:1:2", "2026-10-01T20:00:00.000Z", false))
                .put(item("new", "movie", 500_000, 6_000_000, "new", "2026-10-20T21:30:00.000Z", false))
                .put(item("unstarted", "movie", 0, 6_000_000, "unstarted", "2026-10-21T10:00:00.000Z", false))
                .put(item("finished", "movie", 5_900_000, 6_000_000, "finished", "2026-10-21T11:00:00.000Z", false))
                .put(item("removed", "movie", 100_000, 6_000_000, "removed", "2026-10-21T12:00:00.000Z", true));
        List<Stremio.Item> out = Stremio.parseLibrary(library, 10);
        assertEquals(2, out.size());
        assertEquals("new", out.get(0).id);
        assertEquals("old", out.get(1).id);
        assertEquals("T1 · E2", out.get(1).episodeLabel());
        assertEquals("", out.get(0).episodeLabel());
    }

    @Test public void limitsTheRow() throws Exception {
        JSONArray library = new JSONArray();
        for (int i = 0; i < 8; i++) library.put(item("x" + i, "movie", 1000, 10_000, "x" + i, 1_000_000L + i, false));
        assertEquals(5, Stremio.parseLibrary(library, 5).size());
        assertEquals("x7", Stremio.parseLibrary(library, 5).get(0).id);
    }

    @Test public void seasonAndEpisodeFromVideoId() {
        assertArrayEquals(new int[]{2, 5}, Stremio.seasonEpisode("tt0903747:2:5"));
        assertArrayEquals(new int[]{0, 0}, Stremio.seasonEpisode("tt0816692"));
        assertArrayEquals(new int[]{0, 0}, Stremio.seasonEpisode(null));
    }

    @Test public void episodeStillAndTitleFromCinemeta() throws Exception {
        Stremio.Item item = new Stremio.Item();
        item.name = "Breaking Bad";
        item.videoId = "tt0903747:2:5";
        JSONObject meta = new JSONObject().put("background", "bg.jpg").put("videos", new JSONArray()
                .put(new JSONObject().put("id", "tt0903747:2:4").put("thumbnail", "a.jpg").put("name", "Four"))
                .put(new JSONObject().put("id", "tt0903747:2:5").put("thumbnail", "b.jpg").put("name", "Breakage")));
        Stremio.applyMeta(item, meta);
        assertEquals("b.jpg", item.still);
        assertEquals("Breakage", item.episodeTitle);
    }

    @Test public void progressIsAShare() {
        Stremio.Item item = new Stremio.Item();
        item.offsetMs = 1_500;
        item.durationMs = 6_000;
        assertEquals(0.25, item.progress(), 1e-9);
        item.durationMs = 0;
        assertTrue(item.progress() < 0);
    }
}
