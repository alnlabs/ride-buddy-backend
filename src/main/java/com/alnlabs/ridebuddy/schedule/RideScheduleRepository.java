package com.alnlabs.ridebuddy.schedule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RideScheduleRepository extends JpaRepository<RideScheduleEntity, UUID> {
    List<RideScheduleEntity> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
    List<RideScheduleEntity> findByActiveTrue();
    Optional<RideScheduleEntity> findByIdAndOwnerId(UUID id, UUID ownerId);
}
