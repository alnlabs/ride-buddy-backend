package com.alnlabs.ridebuddy.booking;

import com.alnlabs.ridebuddy.common.ApiException;
import com.alnlabs.ridebuddy.request.RideRequestService;
import com.alnlabs.ridebuddy.ride.RideEntity;
import com.alnlabs.ridebuddy.ride.RideService;
import jakarta.transaction.Transactional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class BookingService {

    private final BookingRepository bookingRepo;
    private final RideService rideService;
    private final RideRequestService rideRequestService;

    public BookingService(
            BookingRepository bookingRepo,
            RideService rideService,
            @Lazy RideRequestService rideRequestService
    ) {
        this.bookingRepo = bookingRepo;
        this.rideService = rideService;
        this.rideRequestService = rideRequestService;
    }

    @Transactional
    public BookingResponse request(UUID passengerId, CreateBookingRequest req) {
        RideEntity ride = rideService.require(req.rideId());
        if (!"open".equals(ride.getStatus())) {
            throw ApiException.badRequest("Ride is not open for booking");
        }
        if (ride.getOwnerId().equals(passengerId)) {
            throw ApiException.badRequest("Host cannot book their own ride");
        }
        if (bookingRepo.findByRideIdAndPassengerId(ride.getId(), passengerId).isPresent()) {
            throw ApiException.conflict("You already requested this ride");
        }
        int seats = req.seatsRequested() != null ? req.seatsRequested() : 1;
        if (seats < 1 || seats > ride.getAvailableSeats()) {
            throw ApiException.badRequest("Invalid seatsRequested");
        }

        BookingEntity b = new BookingEntity();
        b.setRideId(ride.getId());
        b.setPassengerId(passengerId);
        b.setStatus("requested");
        b.setSeatsRequested(seats);
        b.setAmount(ride.getPricePerSeat().multiply(BigDecimal.valueOf(seats)));
        b.setPaymentMethod("cash");
        b.setPickupLat(req.pickupLat());
        b.setPickupLng(req.pickupLng());
        b.setPickupLabel(req.pickupLabel());
        b.setDropLat(req.dropLat());
        b.setDropLng(req.dropLng());
        b.setDropLabel(req.dropLabel());
        bookingRepo.save(b);
        return toResponse(b, ride);
    }

    @Transactional
    public BookingResponse decide(UUID ownerId, UUID bookingId, boolean accept) {
        BookingEntity b = bookingRepo.findById(bookingId)
                .orElseThrow(() -> ApiException.notFound("Booking not found"));
        RideEntity ride = rideService.require(b.getRideId());
        if (!ride.getOwnerId().equals(ownerId)) {
            throw ApiException.forbidden("Only the ride host can accept/reject");
        }
        if (!"requested".equals(b.getStatus())) {
            throw ApiException.badRequest("Booking is not pending");
        }
        if (accept) {
            rideService.adjustSeats(ride, -b.getSeatsRequested());
            b.setStatus("accepted");
            bookingRepo.save(b);
            rideRequestService.linkRequestOnBookingAccepted(b.getPassengerId(), ride.getId(), b.getId());
        } else {
            b.setStatus("rejected");
            bookingRepo.save(b);
        }
        return toResponse(b, ride);
    }

    @Transactional
    public BookingResponse cancel(UUID passengerId, UUID bookingId) {
        BookingEntity b = bookingRepo.findByIdAndPassengerId(bookingId, passengerId)
                .orElseThrow(() -> ApiException.notFound("Booking not found"));
        RideEntity ride = rideService.require(b.getRideId());
        if ("accepted".equals(b.getStatus())) {
            rideService.adjustSeats(ride, b.getSeatsRequested());
        } else if (!"requested".equals(b.getStatus())) {
            throw ApiException.badRequest("Booking cannot be cancelled");
        }
        b.setStatus("cancelled");
        bookingRepo.save(b);
        return toResponse(b, ride);
    }

    public List<BookingResponse> myTrips(UUID passengerId) {
        return bookingRepo.findByPassengerIdOrderByCreatedAtDesc(passengerId).stream()
                .map(b -> toResponse(b, rideService.require(b.getRideId())))
                .toList();
    }

    public List<BookingResponse> forRide(UUID ownerId, UUID rideId) {
        RideEntity ride = rideService.require(rideId);
        if (!ride.getOwnerId().equals(ownerId)) {
            throw ApiException.forbidden("Not your ride");
        }
        return bookingRepo.findByRideIdOrderByCreatedAtAsc(rideId).stream()
                .map(b -> toResponse(b, ride))
                .toList();
    }

    private BookingResponse toResponse(BookingEntity b, RideEntity ride) {
        return new BookingResponse(
                b.getId(),
                b.getRideId(),
                b.getPassengerId(),
                b.getStatus(),
                b.getSeatsRequested(),
                b.getAmount(),
                b.getPaymentMethod(),
                b.getPickupLat(),
                b.getPickupLng(),
                b.getPickupLabel(),
                b.getDropLat(),
                b.getDropLng(),
                b.getDropLabel(),
                ride.getOriginLabel(),
                ride.getDestinationLabel(),
                ride.getDepartAt(),
                ride.getStatus()
        );
    }

    public record CreateBookingRequest(
            UUID rideId,
            Integer seatsRequested,
            Double pickupLat,
            Double pickupLng,
            String pickupLabel,
            Double dropLat,
            Double dropLng,
            String dropLabel
    ) {}

    public record BookingResponse(
            UUID id,
            UUID rideId,
            UUID passengerId,
            String status,
            int seatsRequested,
            BigDecimal amount,
            String paymentMethod,
            Double pickupLat,
            Double pickupLng,
            String pickupLabel,
            Double dropLat,
            Double dropLng,
            String dropLabel,
            String rideOriginLabel,
            String rideDestinationLabel,
            java.time.Instant departAt,
            String rideStatus
    ) {}
}
