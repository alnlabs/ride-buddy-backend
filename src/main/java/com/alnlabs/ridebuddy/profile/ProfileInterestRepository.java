package com.alnlabs.ridebuddy.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProfileInterestRepository extends JpaRepository<ProfileInterestEntity, UUID> {
    List<ProfileInterestEntity> findByUserIdOrderBySortOrderAscCreatedAtAsc(UUID userId);
    void deleteByUserId(UUID userId);
    long countByUserId(UUID userId);
}
