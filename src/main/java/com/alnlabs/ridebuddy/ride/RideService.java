package com.alnlabs.ridebuddy.ride;

import com.alnlabs.ridebuddy.common.ApiException;
import com.alnlabs.ridebuddy.common.GeoUtils;
import com.alnlabs.ridebuddy.config.AppProperties;
import com.alnlabs.ridebuddy.profile.ProfileEntity;
import com.alnlabs.ridebuddy.profile.ProfileRepository;
import com.alnlabs.ridebuddy.profile.ProfileService;
import com.alnlabs.ridebuddy.vehicle.VehicleEntity;
import com.alnlabs.ridebuddy.vehicle.VehicleRepository;
import com.alnlabs.ridebuddy.vehicle.VehicleService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RideService {

    private static final double DEFAULT_RADIUS_KM = 8.0;
    /** Local / commute trips only — destination must be within this of origin. */
    private static final double MAX_TRIP_KM = 100.0;

    private static final ZoneId SHARE_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter SHARE_WHEN =
            DateTimeFormatter.ofPattern("EEE, d MMM · h:mm a", Locale.ENGLISH);

    private final RideRepository rideRepo;
    private final VehicleService vehicleService;
    private final VehicleRepository vehicleRepo;
    private final ProfileRepository profileRepo;
    private final ProfileService profileService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public RideService(
            RideRepository rideRepo,
            VehicleService vehicleService,
            VehicleRepository vehicleRepo,
            ProfileRepository profileRepo,
            ProfileService profileService,
            AppProperties appProperties,
            ObjectMapper objectMapper
    ) {
        this.rideRepo = rideRepo;
        this.vehicleService = vehicleService;
        this.vehicleRepo = vehicleRepo;
        this.profileRepo = profileRepo;
        this.profileService = profileService;
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RideResponse create(UUID ownerId, CreateRideRequest req) {
        VehicleEntity vehicle = vehicleService.requireOwnedActive(ownerId, req.vehicleId());
        if (Boolean.TRUE.equals(req.comfortRide()) && vehicle.getSeats() < 4) {
            throw ApiException.badRequest("Comfort rides require a vehicle with at least 4 seats");
        }
        int seats = req.availableSeats() != null ? req.availableSeats() : Math.max(1, vehicle.getSeats() - 1);
        if (Boolean.TRUE.equals(req.comfortRide())) {
            seats = Math.min(seats, 2);
        } else {
            seats = Math.min(seats, Math.min(3, Math.max(1, vehicle.getSeats() - 1)));
        }
        if (seats < 1) {
            throw ApiException.badRequest("availableSeats must be >= 1");
        }
        if (req.originLat() == null || req.originLng() == null || req.destinationLat() == null || req.destinationLng() == null) {
            throw ApiException.badRequest("Origin and destination coordinates are required");
        }
        if (!GeoUtils.withinKm(req.originLat(), req.originLng(), req.destinationLat(), req.destinationLng(), MAX_TRIP_KM)) {
            throw ApiException.badRequest("Destination must be within " + (int) MAX_TRIP_KM + " km of the start location");
        }
        if (req.departAt() == null) {
            throw ApiException.badRequest("departAt is required");
        }

        RideEntity ride = new RideEntity();
        ride.setOwnerId(ownerId);
        ride.setVehicleId(vehicle.getId());
        ride.setRideType(req.rideType() != null ? req.rideType() : "scheduled");
        ride.setStatus("open");
        ride.setComfortRide(Boolean.TRUE.equals(req.comfortRide()));
        if (ride.isComfortRide()) {
            ride.setMaxBackSeatPassengers(2);
        } else {
            ride.setMaxBackSeatPassengers(Math.min(3, Math.max(1, vehicle.getSeats() - 1)));
        }
        ride.setOriginLat(req.originLat());
        ride.setOriginLng(req.originLng());
        ride.setOriginLabel(req.originLabel() != null ? req.originLabel() : "Origin");
        ride.setDestinationLat(req.destinationLat());
        ride.setDestinationLng(req.destinationLng());
        ride.setDestinationLabel(req.destinationLabel() != null ? req.destinationLabel() : "Destination");
        ride.setDepartAt(req.departAt());
        ride.setAvailableSeats(seats);
        ride.setPricePerSeat(req.pricePerSeat() != null ? req.pricePerSeat() : BigDecimal.ZERO);
        ride.setRecurring(Boolean.TRUE.equals(req.recurring()));
        if (req.routeGeometry() != null) {
            try {
                ride.setRouteGeometry(objectMapper.writeValueAsString(req.routeGeometry()));
            } catch (Exception e) {
                throw ApiException.badRequest("Invalid routeGeometry");
            }
        }
        ride.setRouteDistanceM(req.routeDistanceM());
        ride.setRouteDurationS(req.routeDurationS());
        rideRepo.save(ride);

        vehicle.setLastUsedAt(Instant.now());
        return toResponse(ride, null, null);
    }

    public List<RideResponse> myRides(UUID ownerId) {
        return rideRepo.findByOwnerIdOrderByDepartAtDesc(ownerId).stream()
                .map(r -> toResponse(r, null, null))
                .toList();
    }

    public List<RideResponse> openOwned(UUID ownerId) {
        return rideRepo.findByOwnerIdAndStatusInOrderByDepartAtAsc(ownerId, List.of("open", "full"))
                .stream()
                .map(r -> toResponse(r, null, null))
                .toList();
    }

    public RideResponse get(UUID rideId, UUID viewerId) {
        RideEntity ride = rideRepo.findById(rideId)
                .orElseThrow(() -> ApiException.notFound("Ride not found"));
        String match = null;
        Double detour = null;
        if (viewerId != null) {
            ProfileEntity profile = profileRepo.findById(viewerId).orElse(null);
            if (profile != null) {
                match = commuteMatch(profile, ride);
                detour = estimateDetourKm(profile, ride);
            }
        }
        return toResponse(ride, match, detour);
    }

    @Transactional
    public RideResponse cancel(UUID ownerId, UUID rideId) {
        RideEntity ride = rideRepo.findById(rideId)
                .orElseThrow(() -> ApiException.notFound("Ride not found"));
        if (!ride.getOwnerId().equals(ownerId)) {
            throw ApiException.forbidden("Only the host can cancel this ride");
        }
        if ("completed".equals(ride.getStatus()) || "cancelled".equals(ride.getStatus())) {
            throw ApiException.badRequest("Ride cannot be cancelled");
        }
        ride.setStatus("cancelled");
        rideRepo.save(ride);
        return toResponse(ride, null, null);
    }

    public List<RideResponse> search(UUID viewerId, SearchRequest req) {
        double originLat = req.originLat();
        double originLng = req.originLng();
        double destLat = req.destinationLat();
        double destLng = req.destinationLng();
        double radius = req.radiusKm() != null ? req.radiusKm() : DEFAULT_RADIUS_KM;

        if (!GeoUtils.withinKm(originLat, originLng, destLat, destLng, MAX_TRIP_KM)) {
            return List.of();
        }

        ProfileEntity profile = profileRepo.findById(viewerId).orElse(null);
        List<RideEntity> open = rideRepo.findByStatusAndDepartAtAfterOrderByDepartAtAsc("open", Instant.now().minusSeconds(3600));

        List<RideResponse> results = new ArrayList<>();
        for (RideEntity ride : open) {
            if (ride.getOwnerId().equals(viewerId)) {
                continue;
            }
            double originDist = GeoUtils.distanceKm(originLat, originLng, ride.getOriginLat(), ride.getOriginLng());
            double destDist = GeoUtils.distanceKm(destLat, destLng, ride.getDestinationLat(), ride.getDestinationLng());
            if (originDist > radius && destDist > radius) {
                continue;
            }
            String match = profile != null ? commuteMatch(profile, ride) : "nearby";
            if (Boolean.TRUE.equals(req.sameCommuteOnly()) && !"same_route".equals(match)) {
                continue;
            }
            double score = originDist + destDist;
            RideResponse response = toResponse(ride, match, score);
            results.add(response);
        }
        boolean preferComfort = Boolean.TRUE.equals(req.comfortOnly());
        results.sort(Comparator
                .comparing((RideResponse r) -> preferComfort && !r.comfortRide())
                .thenComparing(r -> r.detourKm() != null ? r.detourKm() : 999.0));
        return results;
    }

    public SharePayload share(UUID rideId) {
        RideEntity ride = rideRepo.findById(rideId)
                .orElseThrow(() -> ApiException.notFound("Ride not found"));
        ProfileEntity owner = profileRepo.findById(ride.getOwnerId()).orElse(null);
        String ownerName = owner != null && owner.getDisplayName() != null && !owner.getDisplayName().isBlank()
                ? owner.getDisplayName().trim()
                : "a Ride Buddy";

        VehicleEntity vehicle = vehicleRepo.findById(ride.getVehicleId()).orElse(null);
        String vehicleLine = null;
        if (vehicle != null) {
            String name = vehicle.getNickname() != null && !vehicle.getNickname().isBlank()
                    ? vehicle.getNickname().trim()
                    : vehicle.getMakeModel();
            String color = vehicle.getColor() != null && !vehicle.getColor().isBlank()
                    ? vehicle.getColor().trim() + " "
                    : "";
            vehicleLine = color + name;
        }

        String when = SHARE_WHEN.format(ride.getDepartAt().atZone(SHARE_ZONE));
        String from = shortPlace(ride.getOriginLabel());
        String to = shortPlace(ride.getDestinationLabel());
        String price = ride.getPricePerSeat().stripTrailingZeros().toPlainString();
        String link = appProperties.share().rideBaseUrl() + "/" + ride.getId();

        StringBuilder tripMeta = new StringBuilder();
        if (ride.getRouteDistanceM() != null && ride.getRouteDistanceM() > 0) {
            double km = ride.getRouteDistanceM() / 1000.0;
            String kmLabel = km >= 10
                    ? String.format(Locale.ENGLISH, "%.0f km", km)
                    : String.format(Locale.ENGLISH, "%.1f km", km);
            tripMeta.append(kmLabel);
        }
        if (ride.getRouteDurationS() != null && ride.getRouteDurationS() > 0) {
            int mins = (int) Math.round(ride.getRouteDurationS() / 60.0);
            String dur = mins < 60 ? mins + " min" : (mins / 60) + "h " + (mins % 60) + "m";
            if (tripMeta.length() > 0) {
                tripMeta.append(" · ");
            }
            tripMeta.append(dur);
        }

        StringBuilder text = new StringBuilder();
        text.append("*Ride Buddy — seat available*\n");
        text.append("Office carpool · share cost with the host (cash)\n");
        text.append("Not a taxi — co-ride together\n\n");
        text.append("*From:* ").append(from).append('\n');
        text.append("*To:* ").append(to).append('\n');
        text.append("*When:* ").append(when).append(" IST\n");
        text.append("*Seats:* ").append(ride.getAvailableSeats()).append('\n');
        text.append("*Share:* ₹").append(price).append(" / seat\n");
        if (tripMeta.length() > 0) {
            text.append("*Route:* ").append(tripMeta).append('\n');
        }
        if (vehicleLine != null) {
            text.append("*Car:* ").append(vehicleLine).append('\n');
        }
        if (ride.isComfortRide()) {
            text.append("*Comfort:* max 2 in back\n");
        }
        text.append("\nHosted by *").append(ownerName).append("*\n");
        ProfileService.PosterCard poster = profileService.posterCard(ride.getOwnerId());
        if (poster.jobRole() != null || poster.company() != null) {
            String roleLine = Stream.of(poster.jobRole(), poster.company())
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.joining(" · "));
            if (!roleLine.isBlank()) {
                text.append(roleLine).append('\n');
            }
        }
        if (poster.topInterests() != null && !poster.topInterests().isEmpty()) {
            text.append("Into: ").append(String.join(", ", poster.topInterests())).append('\n');
        }
        text.append("Open in Ride Buddy:\n").append(link);

        return new SharePayload(ride.getId(), text.toString().trim(), link);
    }

    /** Keep WhatsApp messages readable — Nominatim labels can be very long. */
    private static String shortPlace(String label) {
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

    public RideEntity require(UUID rideId) {
        return rideRepo.findById(rideId).orElseThrow(() -> ApiException.notFound("Ride not found"));
    }

    @Transactional
    public void adjustSeats(RideEntity ride, int delta) {
        int next = ride.getAvailableSeats() + delta;
        if (next < 0) {
            throw ApiException.badRequest("Not enough seats");
        }
        ride.setAvailableSeats(next);
        if (next == 0 && "open".equals(ride.getStatus())) {
            ride.setStatus("full");
        } else if (next > 0 && "full".equals(ride.getStatus())) {
            ride.setStatus("open");
        }
        rideRepo.save(ride);
    }

    private String commuteMatch(ProfileEntity p, RideEntity ride) {
        boolean homeNearOrigin = p.getHomeLat() != null
                && GeoUtils.withinKm(p.getHomeLat(), p.getHomeLng(), ride.getOriginLat(), ride.getOriginLng(), 3);
        boolean officeNearDest = p.getOfficeLat() != null
                && GeoUtils.withinKm(p.getOfficeLat(), p.getOfficeLng(), ride.getDestinationLat(), ride.getDestinationLng(), 3);
        boolean homeNearDest = p.getHomeLat() != null
                && GeoUtils.withinKm(p.getHomeLat(), p.getHomeLng(), ride.getDestinationLat(), ride.getDestinationLng(), 3);
        boolean officeNearOrigin = p.getOfficeLat() != null
                && GeoUtils.withinKm(p.getOfficeLat(), p.getOfficeLng(), ride.getOriginLat(), ride.getOriginLng(), 3);

        if (homeNearOrigin && officeNearDest) {
            return "same_route";
        }
        if (officeNearDest || homeNearDest) {
            return "same_destination";
        }
        if (homeNearOrigin || officeNearOrigin) {
            return "same_origin";
        }
        if (homeNearOrigin || officeNearDest || homeNearDest || officeNearOrigin) {
            return "partial";
        }
        return "nearby";
    }

    private Double estimateDetourKm(ProfileEntity p, RideEntity ride) {
        if (p.getHomeLat() == null || p.getOfficeLat() == null) {
            return null;
        }
        double a = GeoUtils.distanceKm(p.getHomeLat(), p.getHomeLng(), ride.getOriginLat(), ride.getOriginLng());
        double b = GeoUtils.distanceKm(p.getOfficeLat(), p.getOfficeLng(), ride.getDestinationLat(), ride.getDestinationLng());
        return a + b;
    }

    private RideResponse toResponse(RideEntity r, String matchType, Double detourKm) {
        Object geometry = null;
        if (r.getRouteGeometry() != null && !r.getRouteGeometry().isBlank()) {
            try {
                geometry = objectMapper.readValue(r.getRouteGeometry(), Object.class);
            } catch (Exception ignored) {
                geometry = r.getRouteGeometry();
            }
        }
        return new RideResponse(
                r.getId(),
                r.getOwnerId(),
                r.getVehicleId(),
                r.getRideType(),
                r.getStatus(),
                r.isComfortRide(),
                r.getOriginLat(),
                r.getOriginLng(),
                r.getOriginLabel(),
                r.getDestinationLat(),
                r.getDestinationLng(),
                r.getDestinationLabel(),
                r.getDepartAt(),
                r.getAvailableSeats(),
                r.getPricePerSeat(),
                r.isRecurring(),
                matchType,
                detourKm,
                geometry,
                r.getRouteDistanceM(),
                r.getRouteDurationS(),
                profileService.posterCard(r.getOwnerId())
        );
    }

    public record CreateRideRequest(
            UUID vehicleId,
            String rideType,
            Boolean comfortRide,
            Double originLat,
            Double originLng,
            String originLabel,
            Double destinationLat,
            Double destinationLng,
            String destinationLabel,
            Instant departAt,
            Integer availableSeats,
            BigDecimal pricePerSeat,
            Boolean recurring,
            Object routeGeometry,
            Double routeDistanceM,
            Double routeDurationS
    ) {}

    public record SearchRequest(
            double originLat,
            double originLng,
            double destinationLat,
            double destinationLng,
            Double radiusKm,
            Boolean comfortOnly,
            Boolean sameCommuteOnly
    ) {}

    public record RideResponse(
            UUID id,
            UUID ownerId,
            UUID vehicleId,
            String rideType,
            String status,
            boolean comfortRide,
            double originLat,
            double originLng,
            String originLabel,
            double destinationLat,
            double destinationLng,
            String destinationLabel,
            Instant departAt,
            int availableSeats,
            BigDecimal pricePerSeat,
            boolean recurring,
            String commuteMatchType,
            Double detourKm,
            Object routeGeometry,
            Double routeDistanceM,
            Double routeDurationS,
            ProfileService.PosterCard poster
    ) {}

    public record SharePayload(UUID rideId, String text, String link) {}
}
