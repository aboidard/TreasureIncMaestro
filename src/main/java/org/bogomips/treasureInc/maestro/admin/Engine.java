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
