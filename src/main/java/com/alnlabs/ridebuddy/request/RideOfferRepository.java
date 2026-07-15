package com.alnlabs.ridebuddy.request;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RideOfferRepository extends JpaRepository<RideOfferEntity, UUID> {
    List<RideOfferEntity> findByRequestIdOrderByCreatedAtDesc(UUID requestId);

    List<RideOfferEntity> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    Optional<RideOfferEntity> findByRequestIdAndRideId(UUID requestId, UUID rideId);

    List<RideOfferEntity> findByRequestIdAndStatus(UUID requestId, String status);
}
