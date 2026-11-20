package org.bogomips.treasureInc.maestro.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

@QuarkusTest
public class MetricsTest {

    @Inject
    Metrics metrics;

    @Inject
    MeterRegistry registry;

    @Test
    @RunOnVertxContext
    public void gaugesAreFedByTheStatusRefresh(UniAsserter asserter) {
        // as the scheduler calls it: on the event loop, without blocking
        asserter.execute(() -> metrics.refresh());
        asserter.execute(() -> {
            for (String name : new String[]{"maestro.users.total", "maestro.users.connected", "maestro.users.desynced",
                    "maestro.redis.memory.mb", "maestro.engine.requests.per.minute", "maestro.users.new.today",
                    "maestro.sync.oldest.pending.seconds"}) {
                assertFalse(Double.isNaN(registry.get(name).gauge().value()), name);
            }
        });
    }
}
