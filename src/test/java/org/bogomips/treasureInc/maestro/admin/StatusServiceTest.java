package org.bogomips.treasureInc.maestro.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class StatusServiceTest {

    // 00:30 in Paris (UTC+1 in November): the day started 30 minutes ago
    private static final Instant NOW = Instant.parse("2026-11-20T23:30:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<Instant> askedStartOfDay = new AtomicReference<>();
    private final AtomicReference<Duration> askedWindow = new AtomicReference<>();

    private final StatusService.GameState redis = new StatusService.GameState() {
        public Uni<SessionSummary> sessions(Instant now, Duration window) {
            askedWindow.set(window);
            return Uni.createFrom().item(new SessionSummary(7, 3, 95));
        }

        public Uni<Double> usedMemoryMb() {
            return Uni.createFrom().item(12.5);
        }

        public Uni<Double> requestsPerMinute(Instant now) {
            return Uni.createFrom().item(240.0);
        }
    };

    private final StatusService.Players players = startOfDay -> {
        askedStartOfDay.set(startOfDay);
        return Uni.createFrom().item(new StatusService.Players.Counts(1500, 12));
    };

    private final StatusService.GameState redisDown = new StatusService.GameState() {
        public Uni<SessionSummary> sessions(Instant now, Duration window) {
            return Uni.createFrom().failure(new IllegalStateException("connection refused"));
        }

        public Uni<Double> usedMemoryMb() {
            return Uni.createFrom().failure(new IllegalStateException("connection refused"));
        }

        public Uni<Double> requestsPerMinute(Instant now) {
            return Uni.createFrom().failure(new IllegalStateException("connection refused"));
        }
    };

    private JsonNode status(StatusService.GameState gameState, StatusService.Players players) {
        StatusService service = new StatusService(gameState, players, Duration.ofMinutes(5), ZoneId.of("Europe/Paris"), CLOCK);
        return mapper.valueToTree(service.status().await().indefinitely());
    }

    @Test
    void followsTheContractOfTheAdminPage() {
        JsonNode n = status(redis, players);

        assertEquals(List.of("generatedAt", "connectedUsers", "desyncedUsers", "redisMemoryMb", "requestsPerMinute", "extra"),
                fieldNames(n));
        assertEquals("2026-11-20T23:30:00Z", n.get("generatedAt").asText());
        assertEquals(7, n.get("connectedUsers").asLong());
        assertEquals(3, n.get("desyncedUsers").asLong());
        assertEquals(12.5, n.get("redisMemoryMb").asDouble());
        assertEquals(240.0, n.get("requestsPerMinute").asDouble());

        JsonNode extra = n.get("extra");
        assertEquals(List.of("totalUsers", "newUsersToday", "oldestPendingSync"), fieldNames(extra));
        assertEquals(1500, extra.at("/totalUsers/value").asLong());
        assertEquals(12, extra.at("/newUsersToday/value").asLong());
        assertEquals(95, extra.at("/oldestPendingSync/value").asLong());
        assertEquals("s", extra.at("/oldestPendingSync/unit").asText());
        extra.forEach(e -> assertFalse(e.get("label").asText().isBlank()));

        assertEquals(Duration.ofMinutes(5), askedWindow.get());
        assertEquals(Instant.parse("2026-11-20T23:00:00Z"), askedStartOfDay.get(), "midnight in Paris");
    }

    @Test
    void anUnreachableSourceGivesNullsButKeepsTheShape() {
        StatusService.Players databaseDown = startOfDay -> Uni.createFrom().failure(new IllegalStateException("down"));

        JsonNode n = status(redisDown, databaseDown);

        assertEquals(List.of("generatedAt", "connectedUsers", "desyncedUsers", "redisMemoryMb", "requestsPerMinute", "extra"),
                fieldNames(n));
        assertTrue(n.get("connectedUsers").isNull());
        assertTrue(n.get("desyncedUsers").isNull());
        assertTrue(n.get("redisMemoryMb").isNull());
        assertTrue(n.get("requestsPerMinute").isNull());
        for (String key : List.of("totalUsers", "newUsersToday", "oldestPendingSync")) {
            assertTrue(n.at("/extra/" + key).has("value"), key);
            assertTrue(n.at("/extra/" + key + "/value").isNull(), key);
            assertFalse(n.at("/extra/" + key + "/label").asText().isBlank(), key);
        }
    }

    @Test
    void onlyTheFailingSourceIsNull() {
        StatusService.Players databaseDown = startOfDay -> Uni.createFrom().failure(new IllegalStateException("down"));

        JsonNode n = status(redis, databaseDown);

        assertEquals(7, n.get("connectedUsers").asLong());
        assertEquals(95, n.at("/extra/oldestPendingSync/value").asLong());
        assertTrue(n.at("/extra/totalUsers/value").isNull());
    }

    private static List<String> fieldNames(JsonNode n) {
        List<String> names = new java.util.ArrayList<>();
        n.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
