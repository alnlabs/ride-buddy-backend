package com.alnlabs.ridebuddy.request;

import com.alnlabs.ridebuddy.common.AuthUser;
import com.alnlabs.ridebuddy.ride.RideService;
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
@RequestMapping("/api/v1")
public class RideRequestController {

    private final RideRequestService service;

    public RideRequestController(RideRequestService service) {
        this.service = service;
    }

    @PostMapping("/ride-requests")
    public RideRequestService.RideRequestResponse create(@RequestBody RideRequestService.CreateRideRequestBody body) {
        return service.create(AuthUser.requireUserId(), body);
    }

    @GetMapping("/ride-requests/mine")
    public List<RideRequestService.RideRequestResponse> mine() {
        return service.mine(AuthUser.requireUserId());
    }

    @GetMapping("/ride-requests/inbox")
    public List<RideRequestService.InboxItem> inbox() {
        return service.inbox(AuthUser.requireUserId());
    }

    @GetMapping("/ride-requests/{id}")
    public RideRequestService.RideRequestResponse get(@PathVariable UUID id) {
        return service.get(id, AuthUser.requireUserId());
    }

    @GetMapping("/ride-requests/{id}/share")
    public com.alnlabs.ridebuddy.share.SharePayload share(@PathVariable UUID id) {
        return service.share(id);
    }

    @PostMapping("/ride-requests/{id}/cancel")
    public RideRequestService.RideRequestResponse cancel(@PathVariable UUID id) {
        return service.cancel(AuthUser.requireUserId(), id);
    }

    @GetMapping("/ride-requests/{id}/matches")
    public List<RideService.RideResponse> matches(@PathVariable UUID id) {
        return service.matches(AuthUser.requireUserId(), id);
    }

    @GetMapping("/ride-requests/{id}/offers")
    public List<RideRequestService.RideOfferResponse> offersForRequest(@PathVariable UUID id) {
        return service.offersForRequest(AuthUser.requireUserId(), id);
    }

    @PostMapping("/ride-offers")
    public RideRequestService.RideOfferResponse offer(@RequestBody RideRequestService.CreateOfferBody body) {
        return service.offer(AuthUser.requireUserId(), body);
    }

    @GetMapping("/ride-offers/mine")
    public List<RideRequestService.RideOfferResponse> myOffers() {
        return service.myOffers(AuthUser.requireUserId());
    }

    @PostMapping("/ride-offers/{id}/decide")
    public RideRequestService.RideOfferResponse decide(
            @PathVariable UUID id,
            @RequestBody Map<String, Boolean> body
    ) {
        boolean accept = Boolean.TRUE.equals(body.get("accept"));
        return service.decideOffer(AuthUser.requireUserId(), id, accept);
    }
}
