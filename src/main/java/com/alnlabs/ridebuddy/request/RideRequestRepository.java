package com.alnlabs.ridebuddy.request;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RideRequestRepository extends JpaRepository<RideRequestEntity, UUID> {
    List<RideRequestEntity> findByRequesterIdOrderByDepartAtDesc(UUID requesterId);

    List<RideRequestEntity> findByStatusAndDepartAtAfterOrderByDepartAtAsc(String status, Instant after);

    Optional<RideRequestEntity> findByIdAndRequesterId(UUID id, UUID requesterId);
}
