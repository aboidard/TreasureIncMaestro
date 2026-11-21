package org.bogomips.treasureInc.maestro.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Map;

/**
 * Body of {@code GET /admin/status}. The shape is a contract with the admin web page: a value that cannot be
 * computed (e.g. Redis down) is null, never missing.
 */
@JsonPropertyOrder({"generatedAt", "connectedUsers", "desyncedUsers", "redisMemoryMb", "requestsPerMinute", "extra"})
public record StatusReport(
        String generatedAt,
        Long connectedUsers,
        Long desyncedUsers,
        Double redisMemoryMb,
        Double requestsPerMinute,
        Map<String, Extra> extra) {

    /** One more metric for the page: a French label, a number or a string (null when unknown), an optional unit. */
    @JsonPropertyOrder({"label", "value", "unit"})
    public record Extra(
            String label,
            Object value,
            @JsonInclude(JsonInclude.Include.NON_NULL) String unit) {
    }
}
