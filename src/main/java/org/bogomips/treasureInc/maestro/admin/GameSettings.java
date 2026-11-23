package org.bogomips.treasureInc.maestro.admin;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Body of {@code GET /admin/settings} (and of the changes): the settings of the game, kept by the engine.
 *
 * @param maintenance      the maintenance planned or under way
 * @param minClientVersion the oldest version of the Unity client that may log in ("0": any)
 * @param updatedAt        the last change (ISO-8601), null before the first one
 */
@JsonPropertyOrder({"maintenance", "minClientVersion", "updatedAt"})
public record GameSettings(Maintenance maintenance, String minClientVersion, String updatedAt) {

    /**
     * @param enabled switched on: the game is refused from start (at once when null) to end (until switched off when
     *                null)
     * @param active  under way now
     * @param reason  what the players read
     * @param start   ISO-8601, null when not planned
     * @param end     ISO-8601, null when not planned
     */
    @JsonPropertyOrder({"enabled", "active", "reason", "start", "end"})
    public record Maintenance(boolean enabled, boolean active, String reason, String start, String end) {
    }

    /** Body of {@code PUT /admin/settings/maintenance}: the engine checks it (400 with what is wrong). */
    public record MaintenanceRequest(Boolean enabled, String reason, String start, String end) {
    }

    /** Body of {@code PUT /admin/settings/min-client-version}, e.g. {"version": "0.22"}. */
    public record VersionRequest(String version) {
    }
}
