package com.alnlabs.ridebuddy.ride;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface RideRepository extends JpaRepository<RideEntity, UUID> {
    List<RideEntity> findByOwnerIdOrderByDepartAtDesc(UUID ownerId);
    List<RideEntity> findByOwnerIdAndStatusInOrderByDepartAtAsc(UUID ownerId, List<String> statuses);
    List<RideEntity> findByStatusAndDepartAtAfterOrderByDepartAtAsc(String status, Instant after);
    List<RideEntity> findByStatusInAndExpiresAtBefore(List<String> statuses, Instant before);
    boolean existsByScheduleIdAndOccurrenceDate(UUID scheduleId, java.time.LocalDate occurrenceDate);
}
