package com.alnlabs.ridebuddy.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatConversationRepository extends JpaRepository<ChatConversationEntity, UUID> {
    Optional<ChatConversationEntity> findByRideIdAndCoRiderId(UUID rideId, UUID coRiderId);

    List<ChatConversationEntity> findByHostIdOrCoRiderIdOrderByLastMessageAtDescCreatedAtDesc(
            UUID hostId,
            UUID coRiderId
    );
}
