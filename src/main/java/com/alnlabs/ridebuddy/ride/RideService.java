package com.alnlabs.ridebuddy.ride;

import com.alnlabs.ridebuddy.common.ApiException;
import com.alnlabs.ridebuddy.common.GeoUtils;
import com.alnlabs.ridebuddy.config.AppProperties;
import com.alnlabs.ridebuddy.share.PostShareSupport;
import com.alnlabs.ridebuddy.share.SharePayload;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RideService {

    private static final double DEFAULT_RADIUS_KM = 8.0;
    /** Local / commute trips only — destination must be within this of origin. */
    private static final double MAX_TRIP_KM = 100.0;

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
        String originPublic = firstNonBlank(req.originPublicShort(), req.originLabel(), "Origin");
        ride.setOriginLabel(originPublic);
        ride.setOriginFullAddress(blankToNull(req.originFullAddress()));
        ride.setOriginPrivateLabel(blankToNull(req.originPrivateLabel()));
        ride.setDestinationLat(req.destinationLat());
        ride.setDestinationLng(req.destinationLng());
        String destPublic = firstNonBlank(req.destinationPublicShort(), req.destinationLabel(), "Destination");
        ride.setDestinationLabel(destPublic);
        ride.setDestinationFullAddress(blankToNull(req.destinationFullAddress()));
        ride.setDestinationPrivateLabel(blankToNull(req.destinationPrivateLabel()));
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
        return toResponse(ride, null, null, ownerId);
    }

    public List<RideResponse> myRides(UUID ownerId) {
        return rideRepo.findByOwnerIdOrderByDepartAtDesc(ownerId).stream()
                .map(r -> toResponse(r, null, null, ownerId))
                .toList();
    }

    public List<RideResponse> openOwned(UUID ownerId) {
        return rideRepo.findByOwnerIdAndStatusInOrderByDepartAtAsc(ownerId, List.of("open", "full"))
                .stream()
                .map(r -> toResponse(r, null, null, ownerId))
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
        return toResponse(ride, match, detour, viewerId);
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
        return toResponse(ride, null, null, ownerId);
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
            RideResponse response = toResponse(ride, match, score, viewerId);
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

        String link = appProperties.share().rideBaseUrl() + "/" + ride.getId();
        String deepLink = "ridebuddy:///ride/detail/" + ride.getId();
        ProfileService.PosterCard poster = profileService.posterCard(ride.getOwnerId());

        return PostShareSupport.ride(
                ride.getId(),
                ride.getOriginLabel(),
                ride.getOriginFullAddress(),
                ride.getDestinationLabel(),
                ride.getDestinationFullAddress(),
                ride.getDepartAt(),
                ride.getAvailableSeats(),
                ride.getPricePerSeat(),
                ride.isComfortRide(),
                vehicleLine,
                ride.getRouteDistanceM(),
                ride.getRouteDurationS(),
                poster,
                link,
                deepLink
        );
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

    private RideResponse toResponse(RideEntity r, String matchType, Double detourKm, UUID viewerId) {
        Object geometry = null;
        if (r.getRouteGeometry() != null && !r.getRouteGeometry().isBlank()) {
            try {
                geometry = objectMapper.readValue(r.getRouteGeometry(), Object.class);
            } catch (Exception ignored) {
                geometry = r.getRouteGeometry();
            }
        }
        boolean ownerView = viewerId != null && viewerId.equals(r.getOwnerId());
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
                r.getOriginFullAddress(),
                ownerView ? r.getOriginPrivateLabel() : null,
                r.getDestinationLat(),
                r.getDestinationLng(),
                r.getDestinationLabel(),
                r.getDestinationFullAddress(),
                ownerView ? r.getDestinationPrivateLabel() : null,
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

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return "Place";
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    public record CreateRideRequest(
            UUID vehicleId,
            String rideType,
            Boolean comfortRide,
            Double originLat,
            Double originLng,
            String originLabel,
            String originPublicShort,
            String originFullAddress,
            String originPrivateLabel,
            Double destinationLat,
            Double destinationLng,
            String destinationLabel,
            String destinationPublicShort,
            String destinationFullAddress,
            String destinationPrivateLabel,
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
            String originFullAddress,
            String originPrivateLabel,
            double destinationLat,
            double destinationLng,
            String destinationLabel,
            String destinationFullAddress,
            String destinationPrivateLabel,
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
}
