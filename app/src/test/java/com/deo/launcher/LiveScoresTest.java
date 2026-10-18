package com.sva.launcher;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LiveScoresTest {
    private static final String EVENT = "{\"events\":[{\"id\":\"401\",\"date\":\"2026-10-18T16:00Z\","
            + "\"status\":{\"displayClock\":\"67'\",\"type\":{\"state\":\"in\",\"shortDetail\":\"67'\"}},"
            + "\"competitions\":[{\"competitors\":["
            + "{\"homeAway\":\"home\",\"score\":\"1\",\"team\":{\"displayName\":\"Málaga\",\"shortDisplayName\":\"Málaga\",\"logo\":\"https://a.espncdn.com/i/teamlogos/soccer/500/99.png\"}},"
            + "{\"homeAway\":\"away\",\"score\":\"0\",\"team\":{\"displayName\":\"Real Sociedad\",\"shortDisplayName\":\"Real Sociedad\",\"logo\":\"\"}}]}]},"
            + "{\"id\":\"402\",\"date\":\"2026-10-18T19:00Z\",\"status\":{\"displayClock\":\"0'\",\"type\":{\"state\":\"pre\",\"shortDetail\":\"Scheduled\"}},"
            + "\"competitions\":[{\"competitors\":[{\"homeAway\":\"home\",\"score\":\"0\",\"team\":{\"displayName\":\"Real Madrid\"}},"
            + "{\"homeAway\":\"away\",\"score\":\"0\",\"team\":{\"displayName\":\"Sevilla\"}}]}]}]}";

    @Test public void parsesLiveAndScheduledGames() throws Exception {
        JSONArray out = new JSONArray();
        LiveScores.parse(new JSONObject(EVENT), "LaLiga", out);
        assertEquals(2, out.length());
        JSONObject live = out.getJSONObject(0);
        assertEquals("in", live.getString("state"));
        assertEquals("67'", live.getString("clock"));
        assertEquals("Málaga", live.getString("homeName"));
        assertEquals("1", live.getString("homeScore"));
        assertEquals("LaLiga", live.getString("competition"));
        assertEquals("pre", out.getJSONObject(1).getString("state"));
    }

    @Test public void matchesFollowedTeamsWithoutFalseFriends() {
        LiveScores.Game game = new LiveScores.Game("1", "LaLiga", "in", "67'", "67'", 0,
                "Málaga CF", "1", "", "Real Madrid Castilla", "0", "");
        assertTrue(game.involves("Málaga"));
        assertTrue(game.involves("malaga"));
        assertFalse(game.involves("Real Madrid"));
        assertTrue(game.live());
        assertEquals("67'", game.status());
        assertEquals("Málaga CF 1 - 0 Real Madrid Castilla", game.scoreLine());
    }

    @Test public void halfTimeIsShownInSpanish() {
        LiveScores.Game game = new LiveScores.Game("1", "LaLiga", "in", "45'", "Halftime", 0,
                "Sevilla", "0", "", "Betis", "0", "");
        assertEquals("Descanso", game.status());
    }
}
