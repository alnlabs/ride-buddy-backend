package com.alnlabs.ridebuddy.trip;

import com.alnlabs.ridebuddy.common.AuthUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class TripGuidelinesController {

    private final TripGuidelinesService guidelinesService;

    public TripGuidelinesController(TripGuidelinesService guidelinesService) {
        this.guidelinesService = guidelinesService;
    }

    @GetMapping("/rides/{rideId}/trip-guidelines")
    public TripGuidelinesService.TripGuidelinesResponse forRide(@PathVariable UUID rideId) {
        return guidelinesService.forRide(AuthUser.requireUserId(), rideId);
    }

    @GetMapping("/bookings/{bookingId}/trip-guidelines")
    public TripGuidelinesService.TripGuidelinesResponse forBooking(@PathVariable UUID bookingId) {
        return guidelinesService.forBooking(AuthUser.requireUserId(), bookingId);
    }
}
