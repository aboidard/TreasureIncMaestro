package org.bogomips.treasureInc.maestro.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.ScanArgs;
import io.quarkus.redis.datasource.hash.ReactiveHashCommands;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.bogomips.treasureInc.maestro.admin.SessionSummary.Session;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads, without ever writing, what the engine keeps in Redis. The key names are those of the engine
 * (RedisUserCache, RedisRequestStats in TreasureIncENGINE_rebirth).
 */
@ApplicationScoped
class RedisGameState implements StatusService.GameState {

    private static final Logger LOG = Logger.getLogger(RedisGameState.class);

    /** Hash of the logged users: public key -> JSON of the engine's CachedSession. */
    static final String SESSIONS_KEY = "treasure_inc.users.logged";
    /** Request counters of the engine, one per 10-second bucket: prefix + epochSecond / 10. */
    static final String REQUESTS_KEY_PREFIX = "treasure_inc.stats.requests.";
    static final long BUCKET_SECONDS = 10;
    private static final int SCAN_BATCH = 200;
    private static final Pattern USED_MEMORY = Pattern.compile("(?m)^used_memory:(\\d+)\\s*$");

    private final ReactiveRedisDataSource redis;
    private final ReactiveHashCommands<String, String, String> hash;
    private final ReactiveValueCommands<String, Long> counters;
    private final ObjectMapper mapper;

    RedisGameState(ReactiveRedisDataSource redis, ObjectMapper mapper) {
        this.redis = redis;
        this.hash = redis.hash(String.class, String.class, String.class);
        this.counters = redis.value(String.class, Long.class);
        this.mapper = mapper;
    }

    /** Walks the hash incrementally (HSCAN); a field returned twice by the scan is only counted once. */
    @Override
    public Uni<SessionSummary> sessions(Instant now, Duration connectedWindow) {
        return hash.hscan(SESSIONS_KEY, new ScanArgs().count(SCAN_BATCH)).toMulti()
                .collect().in(() -> new HashMap<String, Session>(), (sessions, e) -> {
                    Session s = read(e.getKey(), e.getValue());
                    if (s != null) {
                        sessions.put(e.getKey(), s);
                    }
                })
                .map(sessions -> SessionSummary.of(sessions.values(), now, connectedWindow));
    }

    @Override
    public Uni<Double> usedMemoryMb() {
        return redis.execute("INFO", "memory").map(r -> usedMemoryMb(r == null ? null : r.toString()));
    }

    /** The 6 complete buckets before the current one: the last minute, at most 10 seconds old. */
    @Override
    public Uni<Double> requestsPerMinute(Instant now) {
        String[] keys = lastMinuteBuckets(now);
        return counters.mget(keys).map(values -> requestsPerMinute(values));
    }

    static String[] lastMinuteBuckets(Instant now) {
        long current = Math.floorDiv(now.getEpochSecond(), BUCKET_SECONDS);
        int count = (int) (60 / BUCKET_SECONDS);
        String[] keys = new String[count];
        for (int i = 0; i < count; i++) {
            keys[i] = REQUESTS_KEY_PREFIX + (current - count + i);
        }
        return keys;
    }

    static double requestsPerMinute(Map<String, Long> buckets) {
        return buckets.values().stream().filter(Objects::nonNull).mapToLong(Long::longValue).sum();
    }

    /** used_memory (bytes) of an INFO reply, in MB rounded to 2 decimals; null if absent. */
    static Double usedMemoryMb(String info) {
        if (info == null) {
            return null;
        }
        Matcher m = USED_MEMORY.matcher(info.replace("\r", ""));
        if (!m.find()) {
            return null;
        }
        double mb = Long.parseLong(m.group(1)) / (1024.0 * 1024.0);
        return Math.round(mb * 100) / 100.0;
    }

    private Session read(String publicKey, String json) {
        try {
            return Session.of(mapper.readTree(json));
        } catch (JsonProcessingException e) {
            LOG.warnf("event=session_unreadable user=%s reason=%s", publicKey, e.getOriginalMessage());
            return null;
        }
    }
}
