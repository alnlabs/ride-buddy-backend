package com.alnlabs.ridebuddy.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OtpChallengeRepository extends JpaRepository<OtpChallengeEntity, UUID> {
    Optional<OtpChallengeEntity> findFirstByPhoneAndConsumedFalseOrderByCreatedAtDesc(String phone);
}
