package com.alnlabs.ridebuddy.request;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ride_requests")
public class RideRequestEntity {

    @Id
    private UUID id;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(name = "origin_lat", nullable = false)
    private double originLat;

    @Column(name = "origin_lng", nullable = false)
    private double originLng;

    @Column(name = "origin_label", nullable = false)
    private String originLabel;

    @Column(name = "destination_lat", nullable = false)
    private double destinationLat;

    @Column(name = "destination_lng", nullable = false)
    private double destinationLng;

    @Column(name = "destination_label", nullable = false)
    private String destinationLabel;

    @Column(name = "depart_at", nullable = false)
    private Instant departAt;

    @Column(name = "seats_needed", nullable = false)
    private int seatsNeeded = 1;

    @Column(name = "comfort_preferred", nullable = false)
    private boolean comfortPreferred;

    @Column(nullable = false)
    private String status = "open";

    @Column(name = "matched_ride_id")
    private UUID matchedRideId;

    @Column(name = "matched_booking_id")
    private UUID matchedBookingId;

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
    public UUID getRequesterId() { return requesterId; }
    public void setRequesterId(UUID requesterId) { this.requesterId = requesterId; }
    public double getOriginLat() { return originLat; }
    public void setOriginLat(double originLat) { this.originLat = originLat; }
    public double getOriginLng() { return originLng; }
    public void setOriginLng(double originLng) { this.originLng = originLng; }
    public String getOriginLabel() { return originLabel; }
    public void setOriginLabel(String originLabel) { this.originLabel = originLabel; }
    public double getDestinationLat() { return destinationLat; }
    public void setDestinationLat(double destinationLat) { this.destinationLat = destinationLat; }
    public double getDestinationLng() { return destinationLng; }
    public void setDestinationLng(double destinationLng) { this.destinationLng = destinationLng; }
    public String getDestinationLabel() { return destinationLabel; }
    public void setDestinationLabel(String destinationLabel) { this.destinationLabel = destinationLabel; }
    public Instant getDepartAt() { return departAt; }
    public void setDepartAt(Instant departAt) { this.departAt = departAt; }
    public int getSeatsNeeded() { return seatsNeeded; }
    public void setSeatsNeeded(int seatsNeeded) { this.seatsNeeded = seatsNeeded; }
    public boolean isComfortPreferred() { return comfortPreferred; }
    public void setComfortPreferred(boolean comfortPreferred) { this.comfortPreferred = comfortPreferred; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public UUID getMatchedRideId() { return matchedRideId; }
    public void setMatchedRideId(UUID matchedRideId) { this.matchedRideId = matchedRideId; }
    public UUID getMatchedBookingId() { return matchedBookingId; }
    public void setMatchedBookingId(UUID matchedBookingId) { this.matchedBookingId = matchedBookingId; }
}
