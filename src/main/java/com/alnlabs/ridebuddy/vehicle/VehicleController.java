package com.alnlabs.ridebuddy.vehicle;

import com.alnlabs.ridebuddy.common.AuthUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GetMapping
    public List<VehicleService.VehicleResponse> list() {
        return vehicleService.list(AuthUser.requireUserId());
    }

    @PostMapping
    public VehicleService.VehicleResponse create(@RequestBody VehicleService.UpsertVehicleRequest body) {
        return vehicleService.create(AuthUser.requireUserId(), body);
    }

    @PutMapping("/{id}")
    public VehicleService.VehicleResponse update(
            @PathVariable UUID id,
            @RequestBody VehicleService.UpsertVehicleRequest body
    ) {
        return vehicleService.update(AuthUser.requireUserId(), id, body);
    }

    @PostMapping("/{id}/primary")
    public VehicleService.VehicleResponse setPrimary(@PathVariable UUID id) {
        return vehicleService.setPrimary(AuthUser.requireUserId(), id);
    }

    @DeleteMapping("/{id}")
    public void deactivate(@PathVariable UUID id) {
        vehicleService.deactivate(AuthUser.requireUserId(), id);
    }
}
