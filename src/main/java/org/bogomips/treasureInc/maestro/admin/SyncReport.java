package org.bogomips.treasureInc.maestro.admin;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** Body of {@code POST /admin/sync}: when the engine started the synchronization, how long it took, its outcome. */
@JsonPropertyOrder({"startedAt", "durationMs", "syncedUsers", "errors"})
public record SyncReport(String startedAt, long durationMs, int syncedUsers, int errors) {
}
