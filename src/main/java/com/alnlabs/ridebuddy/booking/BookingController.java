package com.alnlabs.ridebuddy.booking;

import com.alnlabs.ridebuddy.common.AuthUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public BookingService.BookingResponse request(@RequestBody BookingService.CreateBookingRequest body) {
        return bookingService.request(AuthUser.requireUserId(), body);
    }

    @GetMapping("/mine")
    public List<BookingService.BookingResponse> mine() {
        return bookingService.myTrips(AuthUser.requireUserId());
    }

    @GetMapping("/ride/{rideId}")
    public List<BookingService.BookingResponse> forRide(@PathVariable UUID rideId) {
        return bookingService.forRide(AuthUser.requireUserId(), rideId);
    }

    @PostMapping("/{id}/decide")
    public BookingService.BookingResponse decide(@PathVariable UUID id, @RequestBody Map<String, Boolean> body) {
        boolean accept = Boolean.TRUE.equals(body.get("accept"));
        return bookingService.decide(AuthUser.requireUserId(), id, accept);
    }

    @PostMapping("/{id}/cancel")
    public BookingService.BookingResponse cancel(@PathVariable UUID id) {
        return bookingService.cancel(AuthUser.requireUserId(), id);
    }
}
