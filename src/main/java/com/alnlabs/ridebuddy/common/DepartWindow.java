package com.alnlabs.ridebuddy.common;

import java.time.Duration;
import java.time.Instant;

public final class DepartWindow {
    private DepartWindow() {}

    /** Posts must depart within this window from create (overnight into next morning allowed). */
    public static final Duration MAX_AHEAD = Duration.ofHours(24);

    /** Keep listing live briefly after depart for boarding / trip start. */
    public static final Duration EXPIRE_GRACE = Duration.ofHours(2);

    public static void requireUpcomingWithinDay(Instant departAt) {
        if (departAt == null) {
            throw ApiException.badRequest("departAt is required");
        }
        Instant now = Instant.now();
        if (!departAt.isAfter(now)) {
            throw ApiException.badRequest("Departure must be in the future");
        }
        if (departAt.isAfter(now.plus(MAX_AHEAD))) {
            throw ApiException.badRequest("Departure must be within the next 24 hours");
        }
    }

    public static Instant expiresAt(Instant departAt) {
        return departAt.plus(EXPIRE_GRACE);
    }
}
