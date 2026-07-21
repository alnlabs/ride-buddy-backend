package com.alnlabs.ridebuddy.routing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/routes")
public class RoutingController {

    private final RoutingService routingService;

    public RoutingController(RoutingService routingService) {
        this.routingService = routingService;
    }

    /**
     * Top 3 driving routes by ETA (live traffic when Google key is configured).
     */
    @PostMapping("/drive")
    public RoutingService.RoutesResponse drive(@Valid @RequestBody DriveRequest body) {
        return routingService.routes(
                body.fromLat(), body.fromLng(), body.toLat(), body.toLng()
        );
    }

    public record DriveRequest(
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double fromLat,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double fromLng,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double toLat,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double toLng
    ) {}
}
