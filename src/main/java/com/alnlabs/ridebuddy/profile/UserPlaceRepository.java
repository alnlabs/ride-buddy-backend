package com.alnlabs.ridebuddy.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserPlaceRepository extends JpaRepository<UserPlaceEntity, UUID> {

    List<UserPlaceEntity> findByUserIdOrderByKindAscCreatedAtAsc(UUID userId);

    List<UserPlaceEntity> findByUserIdAndKindOrderByCreatedAtAsc(UUID userId, String kind);

    Optional<UserPlaceEntity> findByIdAndUserId(UUID id, UUID userId);

    Optional<UserPlaceEntity> findFirstByUserIdAndKindAndPrimaryTrue(UUID userId, String kind);

    @Modifying(clearAutomatically = true)
    @Query("update UserPlaceEntity p set p.primary = false where p.userId = :userId and p.kind = :kind and p.primary = true")
    void clearPrimary(@Param("userId") UUID userId, @Param("kind") String kind);
}
