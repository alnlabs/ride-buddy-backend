package com.alnlabs.ridebuddy.ride;

import com.alnlabs.ridebuddy.common.AuthUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rides")
public class RideController {

    private final RideService rideService;

    public RideController(RideService rideService) {
        this.rideService = rideService;
    }

    @PostMapping
    public RideService.RideResponse create(@RequestBody RideService.CreateRideRequest body) {
        return rideService.create(AuthUser.requireUserId(), body);
    }

    @GetMapping("/mine")
    public List<RideService.RideResponse> mine() {
        return rideService.myRides(AuthUser.requireUserId());
    }

    @GetMapping("/open")
    public List<RideService.RideResponse> openOwned() {
        return rideService.openOwned(AuthUser.requireUserId());
    }

    @GetMapping("/search")
    public List<RideService.RideResponse> search(
            @RequestParam double originLat,
            @RequestParam double originLng,
            @RequestParam double destinationLat,
            @RequestParam double destinationLng,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(required = false) Boolean comfortOnly,
            @RequestParam(required = false) Boolean sameCommuteOnly
    ) {
        return rideService.search(
                AuthUser.requireUserId(),
                new RideService.SearchRequest(
                        originLat, originLng, destinationLat, destinationLng,
                        radiusKm, comfortOnly, sameCommuteOnly
                )
        );
    }

    @GetMapping("/{id}")
    public RideService.RideResponse get(@PathVariable UUID id) {
        return rideService.get(id, AuthUser.requireUserId());
    }

    @PostMapping("/{id}/cancel")
    public RideService.RideResponse cancel(@PathVariable UUID id) {
        return rideService.cancel(AuthUser.requireUserId(), id);
    }

    @GetMapping("/{id}/share")
    public RideService.SharePayload share(@PathVariable UUID id) {
        return rideService.share(id);
    }
}
