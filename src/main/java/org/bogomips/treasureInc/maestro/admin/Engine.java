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
}
