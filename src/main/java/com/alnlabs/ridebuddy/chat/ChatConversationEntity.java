package com.alnlabs.ridebuddy.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_conversations")
public class ChatConversationEntity {

    @Id
    private UUID id;

    @Column(name = "ride_id", nullable = false)
    private UUID rideId;

    @Column(name = "host_id", nullable = false)
    private UUID hostId;

    @Column(name = "co_rider_id", nullable = false)
    private UUID coRiderId;

    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "offer_id")
    private UUID offerId;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "last_message_preview")
    private String lastMessagePreview;

    @Column(name = "host_last_read_at")
    private Instant hostLastReadAt;

    @Column(name = "co_rider_last_read_at")
    private Instant coRiderLastReadAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getRideId() { return rideId; }
    public void setRideId(UUID rideId) { this.rideId = rideId; }
    public UUID getHostId() { return hostId; }
    public void setHostId(UUID hostId) { this.hostId = hostId; }
    public UUID getCoRiderId() { return coRiderId; }
    public void setCoRiderId(UUID coRiderId) { this.coRiderId = coRiderId; }
    public UUID getBookingId() { return bookingId; }
    public void setBookingId(UUID bookingId) { this.bookingId = bookingId; }
    public UUID getOfferId() { return offerId; }
    public void setOfferId(UUID offerId) { this.offerId = offerId; }
    public Instant getLastMessageAt() { return lastMessageAt; }
    public void setLastMessageAt(Instant lastMessageAt) { this.lastMessageAt = lastMessageAt; }
    public String getLastMessagePreview() { return lastMessagePreview; }
    public void setLastMessagePreview(String lastMessagePreview) { this.lastMessagePreview = lastMessagePreview; }
    public Instant getHostLastReadAt() { return hostLastReadAt; }
    public void setHostLastReadAt(Instant hostLastReadAt) { this.hostLastReadAt = hostLastReadAt; }
    public Instant getCoRiderLastReadAt() { return coRiderLastReadAt; }
    public void setCoRiderLastReadAt(Instant coRiderLastReadAt) { this.coRiderLastReadAt = coRiderLastReadAt; }
    public Instant getCreatedAt() { return createdAt; }
}
