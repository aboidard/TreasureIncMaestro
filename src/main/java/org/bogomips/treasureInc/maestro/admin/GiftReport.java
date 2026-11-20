package org.bogomips.treasureInc.maestro.admin;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** Answer of {@code POST /admin/gifts}: the id of the sending, written on each gift, and how many players got one. */
@JsonPropertyOrder({"batch", "sent"})
public record GiftReport(String batch, int sent) {
}
