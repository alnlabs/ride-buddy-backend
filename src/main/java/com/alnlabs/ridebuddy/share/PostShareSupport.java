package com.alnlabs.ridebuddy.share;

import com.alnlabs.ridebuddy.profile.ProfileService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Builds WhatsApp-ready text and OG-friendly fields for ride / need posts. */
public final class PostShareSupport {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEE, d MMM · h:mm a", Locale.ENGLISH);

    private PostShareSupport() {}

    public static String formatWhen(Instant departAt) {
        if (departAt == null) {
            return "Time TBA";
        }
        return WHEN.format(departAt.atZone(ZONE)) + " IST";
    }

    public static String shortPlace(String label) {
        if (label == null || label.isBlank()) {
            return "Unknown";
        }
        String[] parts = label.split(",");
        return Stream.of(parts)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .limit(3)
                .collect(Collectors.joining(", "));
    }

    /** Prefer full address for share text; fall back to public/short label. */
    public static String sharePlace(String fullAddress, String publicLabel) {
        if (fullAddress != null && !fullAddress.isBlank()) {
            return fullAddress.trim();
        }
        if (publicLabel != null && !publicLabel.isBlank()) {
            return publicLabel.trim();
        }
        return "Unknown";
    }

    public static String roleLine(ProfileService.PosterCard poster) {
        if (poster == null) {
            return null;
        }
        String line = Stream.of(poster.jobRole(), poster.company())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" · "));
        return line.isBlank() ? null : line;
    }

    public static String displayName(ProfileService.PosterCard poster, String fallback) {
        if (poster != null && poster.displayName() != null && !poster.displayName().isBlank()) {
            return poster.displayName().trim();
        }
        return fallback;
    }

    public static String routeMeta(Double distanceM, Double durationS) {
        StringBuilder tripMeta = new StringBuilder();
        if (distanceM != null && distanceM > 0) {
            double km = distanceM / 1000.0;
            tripMeta.append(km >= 10
                    ? String.format(Locale.ENGLISH, "%.0f km", km)
                    : String.format(Locale.ENGLISH, "%.1f km", km));
        }
        if (durationS != null && durationS > 0) {
            int mins = (int) Math.round(durationS / 60.0);
            String dur = mins < 60 ? mins + " min" : (mins / 60) + "h " + (mins % 60) + "m";
            if (tripMeta.length() > 0) {
                tripMeta.append(" · ");
            }
            tripMeta.append(dur);
        }
        return tripMeta.length() == 0 ? null : tripMeta.toString();
    }

    public static String priceLabel(BigDecimal pricePerSeat) {
        if (pricePerSeat == null) {
            return null;
        }
        return "₹" + pricePerSeat.stripTrailingZeros().toPlainString() + " / seat";
    }

    public static SharePayload ride(
            UUID id,
            String from,
            String fromFull,
            String to,
            String toFull,
            Instant departAt,
            int seats,
            BigDecimal pricePerSeat,
            boolean comfort,
            String vehicleLine,
            Double distanceM,
            Double durationS,
            ProfileService.PosterCard poster,
            String link,
            String deepLink
    ) {
        String when = formatWhen(departAt);
        String fromLabel = sharePlace(fromFull, from);
        String toLabel = sharePlace(toFull, to);
        String person = displayName(poster, "a Ride Buddy");
        String role = roleLine(poster);
        String route = routeMeta(distanceM, durationS);
        String price = priceLabel(pricePerSeat);

        StringBuilder meta = new StringBuilder();
        meta.append(seats).append(seats == 1 ? " seat" : " seats");
        if (price != null) {
            meta.append(" · ").append(price);
        }
        if (comfort) {
            meta.append(" · Comfort");
        }
        if (route != null) {
            meta.append(" · ").append(route);
        }

        String title = shortPlace(from) + " → " + shortPlace(to);
        String subtitle = when + " · " + seats + (seats == 1 ? " seat" : " seats")
                + (price != null ? " · " + price : "");

        StringBuilder text = new StringBuilder();
        text.append("🚗 *Ride Buddy — seat available*\n");
        text.append("Office carpool · cash seat share (not a taxi)\n\n");
        text.append("📍 *From*\n").append(fromLabel).append("\n\n");
        text.append("📌 *To*\n").append(toLabel).append("\n\n");
        text.append("🕒 *When*\n").append(when).append("\n\n");
        text.append("💺 *Seats*\n").append(seats).append(seats == 1 ? " seat" : " seats");
        if (comfort) {
            text.append(" · Comfort (max 2 in back)");
        }
        text.append("\n\n");
        if (price != null) {
            text.append("💰 *Share*\n").append(price).append("\n\n");
        }
        if (route != null) {
            text.append("🛣️ *Route*\n").append(route).append("\n\n");
        }
        if (vehicleLine != null && !vehicleLine.isBlank()) {
            text.append("🚘 *Car*\n").append(vehicleLine.trim()).append("\n\n");
        }
        text.append("👤 *Host*\n*").append(person).append("*");
        if (role != null) {
            text.append('\n').append(role);
        }
        text.append("\n\n🔗 *Open in Ride Buddy*\n").append(link);

        return new SharePayload(
                id,
                "ride",
                title,
                subtitle,
                when,
                fromLabel,
                toLabel,
                meta.toString(),
                person,
                role,
                text.toString().trim(),
                link,
                deepLink
        );
    }

    public static SharePayload need(
            UUID id,
            String from,
            String fromFull,
            String to,
            String toFull,
            Instant departAt,
            int seatsNeeded,
            boolean comfortPreferred,
            ProfileService.PosterCard poster,
            String link,
            String deepLink
    ) {
        String when = formatWhen(departAt);
        String fromLabel = sharePlace(fromFull, from);
        String toLabel = sharePlace(toFull, to);
        String person = displayName(poster, "a Ride Buddy");
        String role = roleLine(poster);

        StringBuilder meta = new StringBuilder();
        meta.append("Needs ").append(seatsNeeded).append(seatsNeeded == 1 ? " seat" : " seats");
        if (comfortPreferred) {
            meta.append(" · Comfort preferred");
        }

        String title = shortPlace(from) + " → " + shortPlace(to);
        String subtitle = when + " · looking for " + seatsNeeded + (seatsNeeded == 1 ? " seat" : " seats");

        StringBuilder text = new StringBuilder();
        text.append("🙋 *Ride Buddy — needs a seat*\n");
        text.append("Office carpool · offer a seat if you’re driving that way\n\n");
        text.append("📍 *From*\n").append(fromLabel).append("\n\n");
        text.append("📌 *To*\n").append(toLabel).append("\n\n");
        text.append("🕒 *When*\n").append(when).append("\n\n");
        text.append("💺 *Looking for*\n").append(seatsNeeded).append(seatsNeeded == 1 ? " seat" : " seats");
        if (comfortPreferred) {
            text.append(" · Comfort preferred");
        }
        text.append("\n\n");
        text.append("👤 *Posted by*\n*").append(person).append("*");
        if (role != null) {
            text.append('\n').append(role);
        }
        text.append("\n\n🔗 *Open in Ride Buddy*\n").append(link);

        return new SharePayload(
                id,
                "need",
                title,
                subtitle,
                when,
                fromLabel,
                toLabel,
                meta.toString(),
                person,
                role,
                text.toString().trim(),
                link,
                deepLink
        );
    }
}
