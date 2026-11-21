package org.bogomips.treasureInc.maestro.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bogomips.treasureInc.maestro.admin.SessionSummary.Session;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SessionSummaryTest {

    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
    private static final Duration WINDOW = Duration.ofMinutes(5);

    @Test
    void readsTheEngineSession() throws Exception {
        // as UserService.CachedSession is written by the engine
        String json = """
                {"user":{"id":1,"public_key":"12345678900","money":5,"lastLogin":"t","createdat":"t","updatedat":"t"},
                 "lastAction":"2026-11-20T09:58:00Z","keyHash":"abc","lastSync":"2026-11-20T09:50:00Z","dirty":true}""";
        Session s = Session.of(new ObjectMapper().readTree(json));
        assertEquals(Instant.parse("2026-11-20T09:58:00Z"), s.lastAction());
        assertEquals(Instant.parse("2026-11-20T09:50:00Z"), s.lastSync());
        assertTrue(s.dirty());
    }

    @Test
    void unreadableDatesAreIgnored() throws Exception {
        Session s = Session.of(new ObjectMapper().readTree("{\"lastAction\":\"old\",\"lastSync\":null}"));
        assertNull(s.lastAction());
        assertNull(s.lastSync());
        assertFalse(s.dirty());
    }

    @Test
    void countsConnectedAndDesyncedPlayers() {
        List<Session> sessions = List.of(
                // active and synced
                new Session(NOW.minusSeconds(10), NOW.minusSeconds(10), false),
                // active, written 90 s ago, changed since
                new Session(NOW.minusSeconds(120), NOW.minusSeconds(90), true),
                // idle for 30 min with changes never written for 40 min
                new Session(NOW.minus(Duration.ofMinutes(30)), NOW.minus(Duration.ofMinutes(40)), true),
                // idle and synced: still in Redis until the engine evicts it, but not connected
                new Session(NOW.minus(Duration.ofMinutes(50)), NOW.minus(Duration.ofMinutes(50)), false),
                // exactly at the edge of the window: not connected
                new Session(NOW.minus(WINDOW), NOW.minus(WINDOW), false));

        SessionSummary summary = SessionSummary.of(sessions, NOW, WINDOW);

        assertEquals(2, summary.connected());
        assertEquals(2, summary.desynced());
        assertEquals(40 * 60, summary.oldestPendingSyncSeconds());
    }

    @Test
    void nothingPendingMeansZero() {
        SessionSummary summary = SessionSummary.of(List.of(new Session(NOW, NOW, false)), NOW, WINDOW);
        assertEquals(new SessionSummary(1, 0, 0), summary);
        assertEquals(new SessionSummary(0, 0, 0), SessionSummary.of(List.of(), NOW, WINDOW));
    }

    @Test
    void aPendingSessionWithoutLastSyncFallsBackToItsLastAction() {
        SessionSummary summary = SessionSummary.of(List.of(new Session(NOW.minusSeconds(600), null, true)), NOW, WINDOW);
        assertEquals(600, summary.oldestPendingSyncSeconds());
    }
}
