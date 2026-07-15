package com.alnlabs.ridebuddy.trip;

import com.alnlabs.ridebuddy.booking.BookingEntity;
import com.alnlabs.ridebuddy.booking.BookingRepository;
import com.alnlabs.ridebuddy.common.ApiException;
import com.alnlabs.ridebuddy.profile.ProfileEntity;
import com.alnlabs.ridebuddy.profile.ProfileInterestEntity;
import com.alnlabs.ridebuddy.profile.ProfileInterestRepository;
import com.alnlabs.ridebuddy.profile.ProfileRepository;
import com.alnlabs.ridebuddy.ride.RideEntity;
import com.alnlabs.ridebuddy.ride.RideService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TripGuidelinesService {

    private static final Map<String, String> INTEREST_PROMPTS = Map.ofEntries(
            Map.entry("badminton", "Local courts or recent games"),
            Map.entry("cricket", "IPL, weekend matches, or favourite players"),
            Map.entry("football", "Clubs you follow or weekend kickabouts"),
            Map.entry("coffee", "Go-to café near office or home"),
            Map.entry("chai", "Morning chai spots on the route"),
            Map.entry("movies", "Recent watch or theatre plans"),
            Map.entry("music", "Playlists, gigs, or instruments"),
            Map.entry("travel", "Weekend getaways or places on your list"),
            Map.entry("coding", "Side projects, stacks, or tools you enjoy"),
            Map.entry("startups", "Ideas you are exploring outside work"),
            Map.entry("books", "What you are reading lately"),
            Map.entry("photography", "Spots you like shooting around the city"),
            Map.entry("food", "Lunch spots coworkers should know about"),
            Map.entry("cooking", "Quick recipes or favourite cuisines"),
            Map.entry("yoga", "Studios or morning routines"),
            Map.entry("running", "Routes or upcoming runs"),
            Map.entry("cycling", "Safe loops or group rides"),
            Map.entry("board games", "Game nights or favourites"),
            Map.entry("video games", "What you are playing these days"),
            Map.entry("pets", "Pets at home — always a warm topic"),
            Map.entry("parenting", "School runs and juggling schedules"),
            Map.entry("design", "Side creative work or inspiration"),
            Map.entry("volunteering", "Causes you care about locally")
    );

    private final RideService rideService;
    private final BookingRepository bookingRepo;
    private final ProfileRepository profileRepo;
    private final ProfileInterestRepository interestRepo;

    public TripGuidelinesService(
            RideService rideService,
            BookingRepository bookingRepo,
            ProfileRepository profileRepo,
            ProfileInterestRepository interestRepo
    ) {
        this.rideService = rideService;
        this.bookingRepo = bookingRepo;
        this.profileRepo = profileRepo;
        this.interestRepo = interestRepo;
    }

    public TripGuidelinesResponse forRide(UUID viewerId, UUID rideId) {
        RideEntity ride = rideService.require(rideId);
        boolean viewerIsHost = ride.getOwnerId().equals(viewerId);
        UUID partnerId = viewerIsHost ? null : ride.getOwnerId();
        String role = viewerIsHost ? "host" : "co_rider";
        return build(viewerId, partnerId, role, "before", displayName(partnerId));
    }

    public TripGuidelinesResponse forBooking(UUID viewerId, UUID bookingId) {
        BookingEntity booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> ApiException.notFound("Booking not found"));
        RideEntity ride = rideService.require(booking.getRideId());
        boolean viewerIsHost = ride.getOwnerId().equals(viewerId);
        if (!viewerIsHost && !booking.getPassengerId().equals(viewerId)) {
            throw ApiException.forbidden("Not allowed to view these guidelines");
        }
        UUID partnerId = viewerIsHost ? booking.getPassengerId() : ride.getOwnerId();
        String role = viewerIsHost ? "host" : "co_rider";
        String phase = "accepted".equals(booking.getStatus()) ? "during" : "before";
        return build(viewerId, partnerId, role, phase, displayName(partnerId));
    }

    private TripGuidelinesResponse build(
            UUID viewerId,
            UUID partnerId,
            String role,
            String phase,
            String partnerDisplayName
    ) {
        List<String> viewerInterests = tagsFor(viewerId);
        List<String> partnerInterests = partnerId != null ? tagsFor(partnerId) : List.of();
        List<String> shared = sharedInterests(viewerInterests, partnerInterests);

        String heading = headingFor(role, phase);
        String intro = introFor(role, phase, partnerDisplayName, !shared.isEmpty());

        return new TripGuidelinesResponse(
                phase,
                role,
                heading,
                intro,
                commonFor(role),
                expectationsFor(role),
                conversationHints(shared, partnerInterests, viewerInterests),
                shared,
                partnerDisplayName,
                !viewerInterests.isEmpty(),
                !partnerInterests.isEmpty()
        );
    }

    private List<String> tagsFor(UUID userId) {
        return interestRepo.findByUserIdOrderBySortOrderAscCreatedAtAsc(userId).stream()
                .map(ProfileInterestEntity::getTag)
                .map(t -> t.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private static List<String> sharedInterests(List<String> a, List<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return List.of();
        }
        Set<String> setB = new LinkedHashSet<>(b);
        return a.stream().filter(setB::contains).distinct().limit(5).toList();
    }

    private String displayName(UUID userId) {
        if (userId == null) {
            return null;
        }
        return profileRepo.findById(userId)
                .map(ProfileEntity::getDisplayName)
                .filter(n -> n != null && !n.isBlank())
                .map(String::trim)
                .orElse("your co-rider");
    }

    private static String headingFor(String role, String phase) {
        if ("during".equals(phase)) {
            return "host".equals(role) ? "Trip instructions" : "Trip instructions";
        }
        return "host".equals(role) ? "Host guidelines" : "Guidelines";
    }

    private static String introFor(String role, String phase, String partnerName, boolean hasShared) {
        if ("during".equals(phase)) {
            if (hasShared && partnerName != null) {
                return "You are sharing a ride with " + partnerName
                        + ". Some people keep it to the commute; others enjoy a bit of conversation — both are welcome.";
            }
            return "You are on a shared commute. Keep it smooth for everyone — chat if it feels natural, quiet if not.";
        }
        if ("host".equals(role)) {
            return "You are hosting a carpool. Clear expectations up front makes repeat rides easier for everyone.";
        }
        if (hasShared && partnerName != null) {
            return "You and " + partnerName + " share a few interests — use them only if you both feel like talking.";
        }
        return "A quick read before you ride — for a smooth trip and, if you want, an easy way to connect later.";
    }

    private static List<GuidelineItem> commonFor(String role) {
        if ("host".equals(role)) {
            return List.of(
                    new GuidelineItem("Confirm pickup", "Message your co-rider with the exact spot and leave time."),
                    new GuidelineItem("Cash as agreed", "Collect the shared seat amount in cash when you meet."),
                    new GuidelineItem("Your car, your rules", "Seat belts, bags, and food — set what works for you calmly."),
                    new GuidelineItem("Follow their cue", "Some people prefer silence on the way — that is normal."),
                    new GuidelineItem("Stay on route", "If you need a small detour, mention it before you change course.")
            );
        }
        return List.of(
                new GuidelineItem("Be on time", "Reach the pickup point a few minutes early — hosts wait on the route."),
                new GuidelineItem("Cash to the host", "Pay the shared seat amount in cash when you meet, as shown in the app."),
                new GuidelineItem("Confirm pickup & drop", "Double-check labels before you leave — small fixes save detours."),
                new GuidelineItem("Treat it like a favour", "It is a personal car, not a cab — keep it tidy and respectful."),
                new GuidelineItem("Quiet is okay", "You do not owe small talk. Many rides are simply A to B.")
        );
    }

    private static List<GuidelineItem> expectationsFor(String role) {
        return List.of(
                new GuidelineItem("Just the commute", "Plenty of people only want the ride — arrive, ride, done. No awkwardness."),
                new GuidelineItem("Open to connect", "If chat flows, shared interests can turn one trip into regular carpools."),
                new GuidelineItem("No pressure either way", "Read the room. A nod and music is as valid as a long conversation.")
        );
    }

    private List<ConversationHint> conversationHints(
            List<String> shared,
            List<String> partnerInterests,
            List<String> viewerInterests
    ) {
        List<String> pool = new ArrayList<>();
        pool.addAll(shared);
        for (String tag : partnerInterests) {
            if (!pool.contains(tag)) {
                pool.add(tag);
            }
        }
        for (String tag : viewerInterests) {
            if (!pool.contains(tag)) {
                pool.add(tag);
            }
        }

        return pool.stream()
                .limit(6)
                .map(tag -> new ConversationHint(formatTag(tag), promptFor(tag)))
                .collect(Collectors.toList());
    }

    private static String formatTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return tag;
        }
        String[] parts = tag.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) {
                sb.append(p.substring(1));
            }
        }
        return sb.toString();
    }

    private static String promptFor(String tag) {
        String key = tag.toLowerCase(Locale.ROOT);
        String direct = INTEREST_PROMPTS.get(key);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, String> e : INTEREST_PROMPTS.entrySet()) {
            if (key.contains(e.getKey()) || e.getKey().contains(key)) {
                return e.getValue();
            }
        }
        return "Swap recommendations or recent experiences around " + formatTag(tag);
    }

    public record GuidelineItem(String title, String body) {}

    public record ConversationHint(String interest, String suggestion) {}

    public record TripGuidelinesResponse(
            String phase,
            String role,
            String heading,
            String intro,
            List<GuidelineItem> common,
            List<GuidelineItem> expectations,
            List<ConversationHint> conversationHints,
            List<String> sharedInterests,
            String partnerDisplayName,
            boolean viewerHasInterests,
            boolean partnerHasInterests
    ) {}
}
