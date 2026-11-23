package org.bogomips.treasureInc.maestro.admin;

import io.smallrye.mutiny.Uni;

/** The game engine (TreasureIncENGINE_rebirth), reached through its Redis request channel like the API does. */
public interface Engine {

    /**
     * Asks the engine to write every pending session from Redis to PostgreSQL now.
     * Fails with {@link TimeoutException} when no engine answers in time, {@link FailureException} when it answers
     * with an error.
     */
    Uni<SyncReport> forceSync();

    /**
     * Asks the engine to put a gift in the gift box of the players it targets.
     * Fails like {@link #forceSync}, and with {@link RejectedException} when the engine refuses the gift (400).
     */
    Uni<GiftReport> sendGift(GiftRequest gift);

    /** The settings of the game (maintenance, oldest client version). Fails like {@link #forceSync}. */
    Uni<GameSettings> getSettings();

    /** Switches the maintenance on or off; fails like {@link #sendGift}. */
    Uni<GameSettings> setMaintenance(GameSettings.MaintenanceRequest maintenance);

    /** Sets the oldest version of the Unity client that may log in; fails like {@link #sendGift}. */
    Uni<GameSettings> setMinClientVersion(String version);

    /** Every event, open or not: {"events": [...]}, as the engine describes them. Fails like {@link #forceSync}. */
    Uni<com.fasterxml.jackson.databind.JsonNode> listEvents();

    /** Creates or replaces an event (the engine checks it); fails like {@link #sendGift}. */
    Uni<com.fasterxml.jackson.databind.JsonNode> saveEvent(com.fasterxml.jackson.databind.JsonNode event);

    /** Deletes an event; fails like {@link #sendGift} ({@link RejectedException} when there is none). */
    Uni<com.fasterxml.jackson.databind.JsonNode> deleteEvent(String code);

    class TimeoutException extends RuntimeException {
        public TimeoutException(String type, java.time.Duration timeout) {
            super("The engine did not answer \"" + type + "\" within " + timeout.toMillis() + " ms");
        }
    }

    class FailureException extends RuntimeException {
        public FailureException(String message) {
            super(message);
        }
    }

    /** The engine refused the request (a 4xx answer): the request itself is wrong. */
    class RejectedException extends FailureException {
        public RejectedException(String message) {
            super(message);
        }
    }
}
