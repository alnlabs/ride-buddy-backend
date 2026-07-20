package com.alnlabs.ridebuddy.request;

import com.alnlabs.ridebuddy.booking.BookingEntity;
import com.alnlabs.ridebuddy.booking.BookingRepository;
import com.alnlabs.ridebuddy.common.ApiException;
import com.alnlabs.ridebuddy.common.GeoUtils;
import com.alnlabs.ridebuddy.config.AppProperties;
import com.alnlabs.ridebuddy.profile.ProfileService;
import com.alnlabs.ridebuddy.ride.RideEntity;
import com.alnlabs.ridebuddy.ride.RideRepository;
import com.alnlabs.ridebuddy.ride.RideService;
import com.alnlabs.ridebuddy.share.PostShareSupport;
import com.alnlabs.ridebuddy.share.SharePayload;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class RideRequestService {

    private static final double DEFAULT_RADIUS_KM = 8.0;
    private static final double MAX_TRIP_KM = 100.0;

    private final RideRequestRepository requestRepo;
    private final RideOfferRepository offerRepo;
    private final RideService rideService;
    private final RideRepository rideRepo;
    private final BookingRepository bookingRepo;
    private final ProfileService profileService;
    private final AppProperties appProperties;

    public RideRequestService(
            RideRequestRepository requestRepo,
            RideOfferRepository offerRepo,
            RideService rideService,
            RideRepository rideRepo,
            BookingRepository bookingRepo,
            ProfileService profileService,
            AppProperties appProperties
    ) {
        this.requestRepo = requestRepo;
        this.offerRepo = offerRepo;
        this.rideService = rideService;
        this.rideRepo = rideRepo;
        this.bookingRepo = bookingRepo;
        this.profileService = profileService;
        this.appProperties = appProperties;
    }

    @Transactional
    public RideRequestResponse create(UUID requesterId, CreateRideRequestBody req) {
        if (req.originLat() == null || req.originLng() == null
                || req.destinationLat() == null || req.destinationLng() == null) {
            throw ApiException.badRequest("Origin and destination coordinates are required");
        }
        if (!GeoUtils.withinKm(req.originLat(), req.originLng(), req.destinationLat(), req.destinationLng(), MAX_TRIP_KM)) {
            throw ApiException.badRequest("Destination must be within " + (int) MAX_TRIP_KM + " km of the start");
        }
        if (req.departAt() == null) {
            throw ApiException.badRequest("departAt is required");
        }
        int seats = req.seatsNeeded() != null ? req.seatsNeeded() : 1;
        if (seats < 1 || seats > 3) {
            throw ApiException.badRequest("seatsNeeded must be 1–3");
        }

        RideRequestEntity e = new RideRequestEntity();
        e.setRequesterId(requesterId);
        e.setOriginLat(req.originLat());
        e.setOriginLng(req.originLng());
        e.setOriginLabel(firstNonBlank(req.originPublicShort(), req.originLabel(), "Origin"));
        e.setOriginFullAddress(blankToNull(req.originFullAddress()));
        e.setOriginPrivateLabel(blankToNull(req.originPrivateLabel()));
        e.setDestinationLat(req.destinationLat());
        e.setDestinationLng(req.destinationLng());
        e.setDestinationLabel(firstNonBlank(req.destinationPublicShort(), req.destinationLabel(), "Destination"));
        e.setDestinationFullAddress(blankToNull(req.destinationFullAddress()));
        e.setDestinationPrivateLabel(blankToNull(req.destinationPrivateLabel()));
        e.setDepartAt(req.departAt());
        e.setSeatsNeeded(seats);
        e.setComfortPreferred(Boolean.TRUE.equals(req.comfortPreferred()));
        e.setStatus("open");
        requestRepo.save(e);
        return toResponse(e, requesterId);
    }

    public List<RideRequestResponse> mine(UUID requesterId) {
        return requestRepo.findByRequesterIdOrderByDepartAtDesc(requesterId).stream()
                .map(e -> toResponse(e, requesterId))
                .toList();
    }

    public RideRequestResponse get(UUID id, UUID viewerId) {
        RideRequestEntity e = requestRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("Ride request not found"));
        // Requester or anyone authenticated can view open requests (owners browsing inbox detail)
        if (!"open".equals(e.getStatus()) && !e.getRequesterId().equals(viewerId)) {
            // Still allow requester + owners who offered
            boolean offered = offerRepo.findByRequestIdOrderByCreatedAtDesc(id).stream()
                    .anyMatch(o -> o.getOwnerId().equals(viewerId));
            if (!e.getRequesterId().equals(viewerId) && !offered) {
                throw ApiException.forbidden("Not allowed to view this request");
            }
        }
        return toResponse(e, viewerId);
    }

    public SharePayload share(UUID id) {
        RideRequestEntity e = requestRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("Ride request not found"));
        if (!"open".equals(e.getStatus())) {
            throw ApiException.badRequest("Only open seat requests can be shared");
        }
        String link = appProperties.share().needBaseUrl() + "/" + e.getId();
        String deepLink = "ridebuddy:///ride/need/" + e.getId();
        ProfileService.PosterCard poster = profileService.posterCard(e.getRequesterId());
        return PostShareSupport.need(
                e.getId(),
                e.getOriginLabel(),
                e.getOriginFullAddress(),
                e.getDestinationLabel(),
                e.getDestinationFullAddress(),
                e.getDepartAt(),
                e.getSeatsNeeded(),
                e.isComfortPreferred(),
                poster,
                link,
                deepLink
        );
    }

    @Transactional
    public RideRequestResponse cancel(UUID requesterId, UUID id) {
        RideRequestEntity e = requestRepo.findByIdAndRequesterId(id, requesterId)
                .orElseThrow(() -> ApiException.notFound("Ride request not found"));
        if (!"open".equals(e.getStatus())) {
            throw ApiException.badRequest("Only open requests can be cancelled");
        }
        e.setStatus("cancelled");
        requestRepo.save(e);
        for (RideOfferEntity offer : offerRepo.findByRequestIdAndStatus(id, "offered")) {
            offer.setStatus("cancelled");
            offerRepo.save(offer);
        }
        return toResponse(e, requesterId);
    }

    public List<RideService.RideResponse> matches(UUID viewerId, UUID requestId) {
        RideRequestEntity e = requestRepo.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("Ride request not found"));
        if (!e.getRequesterId().equals(viewerId)) {
            throw ApiException.forbidden("Not your request");
        }
        return rideService.search(
                viewerId,
                new RideService.SearchRequest(
                        e.getOriginLat(),
                        e.getOriginLng(),
                        e.getDestinationLat(),
                        e.getDestinationLng(),
                        DEFAULT_RADIUS_KM,
                        e.isComfortPreferred() ? true : null,
                        null
                )
        );
    }

    /** Open need-a-ride posts near the owner's open rides. */
    public List<InboxItem> inbox(UUID ownerId) {
        List<RideEntity> myRides = rideRepo.findByOwnerIdAndStatusInOrderByDepartAtAsc(
                ownerId, List.of("open", "full"));
        if (myRides.isEmpty()) {
            return List.of();
        }
        List<RideRequestEntity> openReqs = requestRepo.findByStatusAndDepartAtAfterOrderByDepartAtAsc(
                "open", Instant.now().minusSeconds(3600));

        List<InboxItem> items = new ArrayList<>();
        for (RideRequestEntity req : openReqs) {
            if (req.getRequesterId().equals(ownerId)) {
                continue;
            }
            RideEntity best = null;
            double bestScore = Double.MAX_VALUE;
            for (RideEntity ride : myRides) {
                if (!"open".equals(ride.getStatus()) || ride.getAvailableSeats() < req.getSeatsNeeded()) {
                    continue;
                }
                if (!GeoUtils.withinKm(
                        ride.getOriginLat(), ride.getOriginLng(),
                        ride.getDestinationLat(), ride.getDestinationLng(),
                        MAX_TRIP_KM)) {
                    continue;
                }
                double o = GeoUtils.distanceKm(
                        req.getOriginLat(), req.getOriginLng(),
                        ride.getOriginLat(), ride.getOriginLng());
                double d = GeoUtils.distanceKm(
                        req.getDestinationLat(), req.getDestinationLng(),
                        ride.getDestinationLat(), ride.getDestinationLng());
                if (o > DEFAULT_RADIUS_KM && d > DEFAULT_RADIUS_KM) {
                    continue;
                }
                double score = o + d;
                // Soft comfort preference: prefer comfort rides when needed, don't hide compact.
                if (req.isComfortPreferred() && !ride.isComfortRide()) {
                    score += 2.5;
                }
                if (score < bestScore) {
                    bestScore = score;
                    best = ride;
                }
            }
            if (best != null) {
                boolean alreadyOffered = offerRepo.findByRequestIdAndRideId(req.getId(), best.getId()).isPresent();
                items.add(new InboxItem(toResponse(req, ownerId), best.getId(), bestScore, alreadyOffered));
            }
        }
        items.sort(Comparator.comparing(InboxItem::detourKm));
        return items;
    }

    @Transactional
    public RideOfferResponse offer(UUID ownerId, CreateOfferBody body) {
        RideRequestEntity req = requestRepo.findById(body.requestId())
                .orElseThrow(() -> ApiException.notFound("Ride request not found"));
        if (!"open".equals(req.getStatus())) {
            throw ApiException.badRequest("Request is not open");
        }
        if (req.getRequesterId().equals(ownerId)) {
            throw ApiException.badRequest("Cannot offer on your own request");
        }
        RideEntity ride = rideService.require(body.rideId());
        if (!ride.getOwnerId().equals(ownerId)) {
            throw ApiException.forbidden("Not your ride");
        }
        if (!"open".equals(ride.getStatus())) {
            throw ApiException.badRequest("Ride is not open");
        }
        if (ride.getAvailableSeats() < req.getSeatsNeeded()) {
            throw ApiException.badRequest("Not enough seats on this ride");
        }
        if (offerRepo.findByRequestIdAndRideId(req.getId(), ride.getId()).isPresent()) {
            throw ApiException.conflict("You already offered this ride for the request");
        }

        RideOfferEntity offer = new RideOfferEntity();
        offer.setRequestId(req.getId());
        offer.setRideId(ride.getId());
        offer.setOwnerId(ownerId);
        offer.setStatus("offered");
        offerRepo.save(offer);
        return toOfferResponse(offer, req, ride);
    }

    public List<RideOfferResponse> offersForRequest(UUID viewerId, UUID requestId) {
        RideRequestEntity req = requestRepo.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("Ride request not found"));
        if (!req.getRequesterId().equals(viewerId)) {
            throw ApiException.forbidden("Not your request");
        }
        return offerRepo.findByRequestIdOrderByCreatedAtDesc(requestId).stream()
                .map(o -> toOfferResponse(o, req, rideService.require(o.getRideId())))
                .toList();
    }

    public List<RideOfferResponse> myOffers(UUID ownerId) {
        return offerRepo.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(o -> {
                    RideRequestEntity req = requestRepo.findById(o.getRequestId()).orElse(null);
                    RideEntity ride = rideService.require(o.getRideId());
                    return toOfferResponse(o, req, ride);
                })
                .toList();
    }

    @Transactional
    public RideOfferResponse decideOffer(UUID requesterId, UUID offerId, boolean accept) {
        RideOfferEntity offer = offerRepo.findById(offerId)
                .orElseThrow(() -> ApiException.notFound("Offer not found"));
        RideRequestEntity req = requestRepo.findById(offer.getRequestId())
                .orElseThrow(() -> ApiException.notFound("Ride request not found"));
        if (!req.getRequesterId().equals(requesterId)) {
            throw ApiException.forbidden("Only the requester can accept/reject offers");
        }
        if (!"offered".equals(offer.getStatus())) {
            throw ApiException.badRequest("Offer is not pending");
        }
        if (!"open".equals(req.getStatus())) {
            throw ApiException.badRequest("Request is no longer open");
        }

        RideEntity ride = rideService.require(offer.getRideId());

        if (!accept) {
            offer.setStatus("rejected");
            offerRepo.save(offer);
            return toOfferResponse(offer, req, ride);
        }

        if (!"open".equals(ride.getStatus()) || ride.getAvailableSeats() < req.getSeatsNeeded()) {
            throw ApiException.badRequest("Ride no longer has enough seats");
        }

        BookingEntity booking = bookingRepo.findByRideIdAndPassengerId(ride.getId(), requesterId)
                .orElse(null);
        if (booking != null) {
            if ("accepted".equals(booking.getStatus()) || "requested".equals(booking.getStatus())) {
                throw ApiException.conflict("You already have a booking on this ride");
            }
            booking.setStatus("accepted");
            booking.setSeatsRequested(req.getSeatsNeeded());
            booking.setAmount(ride.getPricePerSeat().multiply(BigDecimal.valueOf(req.getSeatsNeeded())));
            booking.setPickupLat(req.getOriginLat());
            booking.setPickupLng(req.getOriginLng());
            booking.setPickupLabel(req.getOriginLabel());
            booking.setDropLat(req.getDestinationLat());
            booking.setDropLng(req.getDestinationLng());
            booking.setDropLabel(req.getDestinationLabel());
        } else {
            booking = new BookingEntity();
            booking.setRideId(ride.getId());
            booking.setPassengerId(requesterId);
            booking.setStatus("accepted");
            booking.setSeatsRequested(req.getSeatsNeeded());
            booking.setAmount(ride.getPricePerSeat().multiply(BigDecimal.valueOf(req.getSeatsNeeded())));
            booking.setPaymentMethod("cash");
            booking.setPickupLat(req.getOriginLat());
            booking.setPickupLng(req.getOriginLng());
            booking.setPickupLabel(req.getOriginLabel());
            booking.setDropLat(req.getDestinationLat());
            booking.setDropLng(req.getDestinationLng());
            booking.setDropLabel(req.getDestinationLabel());
        }
        bookingRepo.save(booking);
        rideService.adjustSeats(ride, -req.getSeatsNeeded());

        offer.setStatus("accepted");
        offerRepo.save(offer);

        req.setStatus("matched");
        req.setMatchedRideId(ride.getId());
        req.setMatchedBookingId(booking.getId());
        requestRepo.save(req);

        for (RideOfferEntity other : offerRepo.findByRequestIdAndStatus(req.getId(), "offered")) {
            other.setStatus("cancelled");
            offerRepo.save(other);
        }

        return toOfferResponse(offer, req, ride);
    }

    /** When owner accepts a normal booking, auto-close a matching open need request. */
    @Transactional
    public void linkRequestOnBookingAccepted(UUID passengerId, UUID rideId, UUID bookingId) {
        RideEntity ride = rideService.require(rideId);
        List<RideRequestEntity> open = requestRepo.findByRequesterIdOrderByDepartAtDesc(passengerId).stream()
                .filter(r -> "open".equals(r.getStatus()))
                .toList();
        for (RideRequestEntity req : open) {
            double o = GeoUtils.distanceKm(
                    req.getOriginLat(), req.getOriginLng(),
                    ride.getOriginLat(), ride.getOriginLng());
            double d = GeoUtils.distanceKm(
                    req.getDestinationLat(), req.getDestinationLng(),
                    ride.getDestinationLat(), ride.getDestinationLng());
            if (o <= DEFAULT_RADIUS_KM || d <= DEFAULT_RADIUS_KM) {
                req.setStatus("matched");
                req.setMatchedRideId(rideId);
                req.setMatchedBookingId(bookingId);
                requestRepo.save(req);
                for (RideOfferEntity other : offerRepo.findByRequestIdAndStatus(req.getId(), "offered")) {
                    other.setStatus("cancelled");
                    offerRepo.save(other);
                }
                break;
            }
        }
    }

    private RideRequestResponse toResponse(RideRequestEntity e, UUID viewerId) {
        boolean ownerView = viewerId != null && viewerId.equals(e.getRequesterId());
        return new RideRequestResponse(
                e.getId(),
                e.getRequesterId(),
                e.getOriginLat(),
                e.getOriginLng(),
                e.getOriginLabel(),
                e.getOriginFullAddress(),
                ownerView ? e.getOriginPrivateLabel() : null,
                e.getDestinationLat(),
                e.getDestinationLng(),
                e.getDestinationLabel(),
                e.getDestinationFullAddress(),
                ownerView ? e.getDestinationPrivateLabel() : null,
                e.getDepartAt(),
                e.getSeatsNeeded(),
                e.isComfortPreferred(),
                e.getStatus(),
                e.getMatchedRideId(),
                e.getMatchedBookingId(),
                profileService.posterCard(e.getRequesterId())
        );
    }

    private RideOfferResponse toOfferResponse(RideOfferEntity o, RideRequestEntity req, RideEntity ride) {
        return new RideOfferResponse(
                o.getId(),
                o.getRequestId(),
                o.getRideId(),
                o.getOwnerId(),
                o.getStatus(),
                req != null ? toResponse(req, o.getOwnerId()) : null,
                rideService.get(ride.getId(), o.getOwnerId())
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

    public record CreateRideRequestBody(
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
            Integer seatsNeeded,
            Boolean comfortPreferred
    ) {}

    public record CreateOfferBody(UUID requestId, UUID rideId) {}

    public record RideRequestResponse(
            UUID id,
            UUID requesterId,
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
            int seatsNeeded,
            boolean comfortPreferred,
            String status,
            UUID matchedRideId,
            UUID matchedBookingId,
            ProfileService.PosterCard poster
    ) {}

    public record RideOfferResponse(
            UUID id,
            UUID requestId,
            UUID rideId,
            UUID ownerId,
            String status,
            RideRequestResponse request,
            RideService.RideResponse ride
    ) {}

    public record InboxItem(
            RideRequestResponse request,
            UUID suggestedRideId,
            double detourKm,
            boolean alreadyOffered
    ) {}
}
