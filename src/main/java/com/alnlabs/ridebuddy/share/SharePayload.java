package com.alnlabs.ridebuddy.share;

import java.util.UUID;

/** Public share card + WhatsApp text for a ride or need post. */
public record SharePayload(
        UUID id,
        String type,
        String title,
        String subtitle,
        String whenLabel,
        String fromLabel,
        String toLabel,
        String metaLine,
        String personName,
        String personRole,
        String text,
        String link,
        String deepLink
) {
    /** Back-compat for older clients that read rideId. */
    public UUID rideId() {
        return "ride".equals(type) ? id : null;
    }
}
