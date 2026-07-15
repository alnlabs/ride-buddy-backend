package com.alnlabs.ridebuddy.vehicle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleRepository extends JpaRepository<VehicleEntity, UUID> {
    List<VehicleEntity> findByOwnerIdAndIsActiveTrueOrderByIsPrimaryDescCreatedAtAsc(UUID ownerId);
    long countByOwnerIdAndIsActiveTrue(UUID ownerId);
    Optional<VehicleEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE VehicleEntity v SET v.isPrimary = false WHERE v.ownerId = :ownerId AND v.isActive = true")
    void clearPrimary(@Param("ownerId") UUID ownerId);
}
