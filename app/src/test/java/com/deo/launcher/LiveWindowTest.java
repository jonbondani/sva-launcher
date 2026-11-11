package com.sva.launcher;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LiveWindowTest {
    private static final long H = 3_600_000L, KICKOFF = 1_000_000_000_000L;

    @Test public void duringTheGameItIsLive() {
        assertTrue(LiveScores.withinLiveWindow(KICKOFF, KICKOFF + 42 * 60_000L));
        assertTrue(LiveScores.withinLiveWindow(KICKOFF, KICKOFF + 3 * H));   // extra time and penalties
        assertTrue(LiveScores.withinLiveWindow(KICKOFF, KICKOFF - 5 * 60_000L));
    }

    @Test public void daysLaterItIsNot() {
        assertFalse(LiveScores.withinLiveWindow(KICKOFF, KICKOFF + 4 * 24 * H));   // Saturday's game on Wednesday
        assertFalse(LiveScores.withinLiveWindow(KICKOFF, KICKOFF + LiveScores.LIVE_WINDOW_MS + 1));
        assertFalse(LiveScores.withinLiveWindow(KICKOFF, KICKOFF - H));
    }

    @Test public void onlyTodaysScoreboardCounts() {
        assertTrue(LiveScores.sameScoreboardDay("20261111", "20261111"));
        assertFalse(LiveScores.sameScoreboardDay("20261107", "20261111"));
        assertFalse(LiveScores.sameScoreboardDay("", "20261111"));
        assertFalse(LiveScores.sameScoreboardDay(null, "20261111"));
    }
}
