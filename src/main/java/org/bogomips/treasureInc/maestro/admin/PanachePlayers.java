package org.bogomips.treasureInc.maestro.admin;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.bogomips.treasureInc.maestro.user.User;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@ApplicationScoped
class PanachePlayers implements StatusService.Players {

    /** Both counts in one session, one after the other: a reactive session does not run two queries at once. */
    @Override
    public Uni<Counts> counts(Instant startOfDay) {
        // the engine writes createdat as a UTC date-time without zone
        Timestamp since = Timestamp.valueOf(LocalDateTime.ofInstant(startOfDay, ZoneOffset.UTC));
        return Panache.withSession(() -> User.count()
                .flatMap(total -> User.countByCreatedAtGreaterThanOrEqual(since)
                        .map(today -> new Counts(total, today))));
    }
}
