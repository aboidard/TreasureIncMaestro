package org.bogomips.treasureInc.maestro.admin;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bogomips.treasureInc.maestro.admin.StatusReport.Extra;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Builds the production summary of {@code GET /admin/status} from Redis (the engine's hot state) and PostgreSQL.
 * Each source is read on its own: when one is down, its values are null and the others are still reported.
 */
@ApplicationScoped
public class StatusService {

    private static final Logger LOG = Logger.getLogger(StatusService.class);

    public static final String TOTAL_USERS = "totalUsers";
    public static final String NEW_USERS_TODAY = "newUsersToday";
    public static final String OLDEST_PENDING_SYNC = "oldestPendingSync";

    private final GameState gameState;
    private final Players players;
    private final Duration connectedWindow;
    private final ZoneId zone;
    private final Clock clock;

    @Inject
    StatusService(GameState gameState, Players players,
                  @ConfigProperty(name = "maestro.status.connected-window", defaultValue = "5m") Duration connectedWindow,
                  @ConfigProperty(name = "maestro.status.zone", defaultValue = "Europe/Paris") ZoneId zone) {
        this(gameState, players, connectedWindow, zone, Clock.systemUTC());
    }

    public StatusService(GameState gameState, Players players, Duration connectedWindow, ZoneId zone, Clock clock) {
        this.gameState = gameState;
        this.players = players;
        this.connectedWindow = connectedWindow;
        this.zone = zone;
        this.clock = clock;
    }

    public Uni<StatusReport> status() {
        Instant now = clock.instant();
        Instant startOfDay = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant();
        return Uni.combine().all().unis(
                        orNull(gameState.sessions(now, connectedWindow), "sessions"),
                        orNull(gameState.usedMemoryMb(), "redis_memory"),
                        orNull(gameState.requestsPerMinute(now), "requests_per_minute"),
                        orNull(players.counts(startOfDay), "player_counts"))
                .asTuple()
                .map(t -> {
                    SessionSummary sessions = t.getItem1();
                    Players.Counts counts = t.getItem4();
                    Map<String, Extra> extra = new LinkedHashMap<>();
                    extra.put(TOTAL_USERS, new Extra("Joueurs inscrits",
                            get(counts, Players.Counts::total), "joueurs"));
                    extra.put(NEW_USERS_TODAY, new Extra("Nouveaux inscrits aujourd'hui",
                            get(counts, Players.Counts::createdToday), "joueurs"));
                    extra.put(OLDEST_PENDING_SYNC, new Extra("Plus ancienne donnée non synchronisée",
                            get(sessions, SessionSummary::oldestPendingSyncSeconds), "s"));
                    return new StatusReport(now.toString(),
                            get(sessions, SessionSummary::connected),
                            get(sessions, SessionSummary::desynced),
                            t.getItem2(),
                            t.getItem3(),
                            extra);
                });
    }

    private static <T> Uni<T> orNull(Uni<T> source, String what) {
        return source.onFailure().recoverWithItem(e -> {
            LOG.warnf("event=status_metric_failed metric=%s reason=%s", what, e.toString());
            return null;
        });
    }

    private static <T, R> R get(T value, Function<T, R> getter) {
        return value == null ? null : getter.apply(value);
    }

    // ---- ports, implemented by the Redis and Panache adapters

    /** The engine's state in Redis. */
    public interface GameState {
        Uni<SessionSummary> sessions(Instant now, Duration connectedWindow);

        /** used_memory of INFO memory, in MB. */
        Uni<Double> usedMemoryMb();

        /** Requests received by the engine over the last complete minute. */
        Uni<Double> requestsPerMinute(Instant now);
    }

    /** The players in PostgreSQL. */
    public interface Players {
        record Counts(long total, long createdToday) {
        }

        /** How many players there are, and how many registered since {@code startOfDay}. */
        Uni<Counts> counts(Instant startOfDay);
    }
}
