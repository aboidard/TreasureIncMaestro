package org.bogomips.treasureInc.maestro.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.bogomips.treasureInc.maestro.admin.StatusReport;
import org.bogomips.treasureInc.maestro.admin.StatusService;
import org.jboss.logging.Logger;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Publishes the production summary of {@link StatusService} as Prometheus gauges (/q/metrics).
 * A gauge is read synchronously by the registry, so it never queries Redis or PostgreSQL itself (the reactive
 * clients must not be blocked on): it reads the last summary, refreshed every {@code maestro.metrics.refresh}.
 * An unknown value is reported as NaN.
 */
@ApplicationScoped
public class Metrics {

    private static final Logger LOG = Logger.getLogger(Metrics.class);

    private final StatusService status;
    private final AtomicReference<StatusReport> last = new AtomicReference<>();

    public Metrics(StatusService status) {
        this.status = status;
    }

    void register(@Observes StartupEvent ev, MeterRegistry registry) {
        gauge(registry, "maestro.users.connected", "Players with an action in the connection window", StatusReport::connectedUsers);
        gauge(registry, "maestro.users.desynced", "Players with changes in Redis not yet in PostgreSQL", StatusReport::desyncedUsers);
        gauge(registry, "maestro.redis.memory.mb", "Memory used by Redis, in MB", StatusReport::redisMemoryMb);
        gauge(registry, "maestro.engine.requests.per.minute", "Requests received by the engine over the last minute", StatusReport::requestsPerMinute);
        gauge(registry, "maestro.users.total", "Registered players", r -> extra(r, StatusService.TOTAL_USERS));
        gauge(registry, "maestro.users.new.today", "Players registered today", r -> extra(r, StatusService.NEW_USERS_TODAY));
        gauge(registry, "maestro.sync.oldest.pending.seconds", "Age of the oldest data not yet written to PostgreSQL",
                r -> extra(r, StatusService.OLDEST_PENDING_SYNC));
    }

    @Scheduled(every = "{maestro.metrics.refresh}", delayed = "5s",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    Uni<Void> refresh() {
        return status.status()
                .invoke(last::set)
                .onFailure().invoke(e -> LOG.warnf("event=metrics_refresh_failed reason=%s", e.toString()))
                .onFailure().recoverWithNull()
                .replaceWithVoid();
    }

    private void gauge(MeterRegistry registry, String name, String description, Function<StatusReport, Number> value) {
        Gauge.builder(name, last, ref -> {
                    StatusReport r = ref.get();
                    Number n = r == null ? null : value.apply(r);
                    return n == null ? Double.NaN : n.doubleValue();
                })
                .description(description)
                .register(registry);
    }

    private static Number extra(StatusReport r, String key) {
        StatusReport.Extra e = r.extra() == null ? null : r.extra().get(key);
        return e != null && e.value() instanceof Number n ? n : null;
    }
}
