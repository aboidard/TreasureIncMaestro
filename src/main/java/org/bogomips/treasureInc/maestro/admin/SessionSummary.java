package org.bogomips.treasureInc.maestro.admin;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collection;

/**
 * What the sessions the engine keeps in Redis ({@code treasure_inc.users.logged}) tell about the players.
 *
 * @param connected           sessions whose last action is more recent than the connection window
 * @param desynced            sessions flagged dirty: changes in Redis not yet written to PostgreSQL
 * @param oldestPendingSyncSeconds among the desynced sessions, the age of the oldest last write to the database
 *                            (0 when nothing is pending): an upper bound of how old the unsaved data can be
 */
public record SessionSummary(long connected, long desynced, long oldestPendingSyncSeconds) {

    /**
     * The fields of a cached session (UserService.CachedSession in the engine) that matter here.
     * lastAction / lastSync are ISO-8601 instants, null when absent or unreadable.
     */
    public record Session(Instant lastAction, Instant lastSync, boolean dirty) {

        public static Session of(JsonNode json) {
            return new Session(instant(json.get("lastAction")), instant(json.get("lastSync")),
                    json.path("dirty").asBoolean(false));
        }

        private static Instant instant(JsonNode n) {
            if (n == null || !n.isTextual()) {
                return null;
            }
            try {
                return Instant.parse(n.asText());
            } catch (DateTimeParseException e) {
                return null;
            }
        }
    }

    public static SessionSummary of(Collection<Session> sessions, Instant now, Duration connectedWindow) {
        Instant connectedSince = now.minus(connectedWindow);
        long connected = 0;
        long desynced = 0;
        long oldest = 0;
        for (Session s : sessions) {
            if (s.lastAction() != null && s.lastAction().isAfter(connectedSince)) {
                connected++;
            }
            if (s.dirty()) {
                desynced++;
                // the engine starts a session as synced at its first action, so lastSync is normally set
                Instant written = s.lastSync() != null ? s.lastSync() : s.lastAction();
                if (written != null) {
                    oldest = Math.max(oldest, Math.max(0, Duration.between(written, now).toSeconds()));
                }
            }
        }
        return new SessionSummary(connected, desynced, oldest);
    }
}
