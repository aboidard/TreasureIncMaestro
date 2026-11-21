package org.bogomips.treasureInc.maestro.admin;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RedisGameStateTest {

    @Test
    void usedMemoryIsReadFromInfoInMegabytes() {
        String info = "# Memory\r\nused_memory:3145728\r\nused_memory_human:3.00M\r\nused_memory_rss:9000000\r\n";
        assertEquals(3.0, RedisGameState.usedMemoryMb(info));
        assertEquals(1.18, RedisGameState.usedMemoryMb("used_memory:1234567\n"));
        assertNull(RedisGameState.usedMemoryMb("# Memory\nused_memory_human:3.00M\n"));
        assertNull(RedisGameState.usedMemoryMb(null));
    }

    @Test
    void theLastMinuteIsTheSixCompleteBucketsBeforeTheCurrentOne() {
        // 1_780_000_005 is in bucket 178_000_000, still being filled: it is left out
        String[] keys = RedisGameState.lastMinuteBuckets(Instant.ofEpochSecond(1_780_000_005L));
        assertEquals(List.of(
                "treasure_inc.stats.requests.177999994",
                "treasure_inc.stats.requests.177999995",
                "treasure_inc.stats.requests.177999996",
                "treasure_inc.stats.requests.177999997",
                "treasure_inc.stats.requests.177999998",
                "treasure_inc.stats.requests.177999999"), List.of(keys));
    }

    @Test
    void requestsPerMinuteSumsTheBucketsAndSkipsTheMissingOnes() {
        Map<String, Long> buckets = new HashMap<>();
        buckets.put("a", 12L);
        buckets.put("b", null);
        buckets.put("c", 30L);
        assertEquals(42.0, RedisGameState.requestsPerMinute(buckets));
        assertEquals(0.0, RedisGameState.requestsPerMinute(Map.of()));
    }
}
