package com.alnlabs.ridebuddy.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<BookingEntity, UUID> {
    List<BookingEntity> findByPassengerIdOrderByCreatedAtDesc(UUID passengerId);
    List<BookingEntity> findByRideIdOrderByCreatedAtAsc(UUID rideId);
    Optional<BookingEntity> findByRideIdAndPassengerId(UUID rideId, UUID passengerId);
    Optional<BookingEntity> findByIdAndPassengerId(UUID id, UUID passengerId);
}
