package com.alnlabs.ridebuddy.chat;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {
    List<ChatMessageEntity> findByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);

    @Query("""
            SELECT m FROM ChatMessageEntity m
            WHERE m.conversationId = :conversationId
              AND (:before IS NULL OR m.createdAt < :before)
            ORDER BY m.createdAt DESC
            """)
    List<ChatMessageEntity> findPage(
            @Param("conversationId") UUID conversationId,
            @Param("before") Instant before,
            Pageable pageable
    );

    long countByConversationIdAndCreatedAtAfterAndSenderIdNot(
            UUID conversationId,
            Instant after,
            UUID senderId
    );
}
