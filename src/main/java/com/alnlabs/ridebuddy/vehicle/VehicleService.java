package com.alnlabs.ridebuddy.vehicle;

import com.alnlabs.ridebuddy.common.ApiException;
import com.alnlabs.ridebuddy.profile.ProfileService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class VehicleService {

    private static final int MAX_VEHICLES = 5;

    private final VehicleRepository vehicleRepo;
    private final ProfileService profileService;

    public VehicleService(VehicleRepository vehicleRepo, ProfileService profileService) {
        this.vehicleRepo = vehicleRepo;
        this.profileService = profileService;
    }

    public List<VehicleResponse> list(UUID ownerId) {
        return vehicleRepo.findByOwnerIdAndIsActiveTrueOrderByIsPrimaryDescCreatedAtAsc(ownerId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public VehicleResponse create(UUID ownerId, UpsertVehicleRequest req) {
        long count = vehicleRepo.countByOwnerIdAndIsActiveTrue(ownerId);
        if (count >= MAX_VEHICLES) {
            throw ApiException.badRequest("Maximum " + MAX_VEHICLES + " vehicles allowed");
        }
        validate(req);
        VehicleEntity v = new VehicleEntity();
        v.setOwnerId(ownerId);
        apply(v, req);
        if (count == 0 || Boolean.TRUE.equals(req.primary())) {
            vehicleRepo.clearPrimary(ownerId);
            v.setPrimary(true);
        } else {
            v.setPrimary(false);
        }
        vehicleRepo.save(v);
        profileService.refreshStrength(ownerId);
        return toResponse(v);
    }

    @Transactional
    public VehicleResponse update(UUID ownerId, UUID vehicleId, UpsertVehicleRequest req) {
        VehicleEntity v = vehicleRepo.findByIdAndOwnerId(vehicleId, ownerId)
                .orElseThrow(() -> ApiException.notFound("Vehicle not found"));
        if (!v.isActive()) {
            throw ApiException.badRequest("Vehicle is inactive");
        }
        validate(req);
        apply(v, req);
        if (Boolean.TRUE.equals(req.primary())) {
            vehicleRepo.clearPrimary(ownerId);
            v.setPrimary(true);
        }
        vehicleRepo.save(v);
        return toResponse(v);
    }

    @Transactional
    public VehicleResponse setPrimary(UUID ownerId, UUID vehicleId) {
        VehicleEntity v = vehicleRepo.findByIdAndOwnerId(vehicleId, ownerId)
                .orElseThrow(() -> ApiException.notFound("Vehicle not found"));
        if (!v.isActive()) {
            throw ApiException.badRequest("Vehicle is inactive");
        }
        vehicleRepo.clearPrimary(ownerId);
        v.setPrimary(true);
        vehicleRepo.save(v);
        return toResponse(v);
    }

    @Transactional
    public void deactivate(UUID ownerId, UUID vehicleId) {
        VehicleEntity v = vehicleRepo.findByIdAndOwnerId(vehicleId, ownerId)
                .orElseThrow(() -> ApiException.notFound("Vehicle not found"));
        v.setActive(false);
        v.setPrimary(false);
        vehicleRepo.save(v);
        List<VehicleEntity> remaining = vehicleRepo.findByOwnerIdAndIsActiveTrueOrderByIsPrimaryDescCreatedAtAsc(ownerId);
        if (!remaining.isEmpty() && remaining.stream().noneMatch(VehicleEntity::isPrimary)) {
            VehicleEntity next = remaining.get(0);
            next.setPrimary(true);
            vehicleRepo.save(next);
        }
        profileService.refreshStrength(ownerId);
    }

    public VehicleEntity requireOwnedActive(UUID ownerId, UUID vehicleId) {
        VehicleEntity v = vehicleRepo.findByIdAndOwnerId(vehicleId, ownerId)
                .orElseThrow(() -> ApiException.notFound("Vehicle not found"));
        if (!v.isActive()) {
            throw ApiException.badRequest("Vehicle is inactive");
        }
        return v;
    }

    private void validate(UpsertVehicleRequest req) {
        if (req.makeModel() == null || req.makeModel().isBlank()) {
            throw ApiException.badRequest("makeModel is required");
        }
        if (req.plateNumber() == null || req.plateNumber().isBlank()) {
            throw ApiException.badRequest("plateNumber is required");
        }
        if (req.seats() == null || req.seats() < 1 || req.seats() > 8) {
            throw ApiException.badRequest("seats must be 1–8");
        }
    }

    private void apply(VehicleEntity v, UpsertVehicleRequest req) {
        v.setNickname(req.nickname());
        v.setMakeModel(req.makeModel().trim());
        v.setPlateNumber(req.plateNumber().trim().toUpperCase());
        v.setSeats(req.seats());
        v.setColor(req.color());
        v.setActive(true);
    }

    private VehicleResponse toResponse(VehicleEntity v) {
        return new VehicleResponse(
                v.getId(),
                v.getNickname(),
                v.getMakeModel(),
                maskPlate(v.getPlateNumber()),
                v.getPlateNumber(),
                v.getSeats(),
                v.getColor(),
                v.isPrimary(),
                v.isActive()
        );
    }

    private String maskPlate(String plate) {
        if (plate == null || plate.length() < 4) {
            return "****";
        }
        return "****" + plate.substring(plate.length() - 4);
    }

    public record UpsertVehicleRequest(
            String nickname,
            String makeModel,
            String plateNumber,
            Integer seats,
            String color,
            Boolean primary
    ) {}

    public record VehicleResponse(
            UUID id,
            String nickname,
            String makeModel,
            String plateMasked,
            String plateNumber,
            int seats,
            String color,
            boolean primary,
            boolean active
    ) {}
}
