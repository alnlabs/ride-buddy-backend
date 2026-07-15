package com.alnlabs.ridebuddy.ride;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rides")
public class RideEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "ride_type", nullable = false)
    private String rideType = "scheduled";

    @Column(nullable = false)
    private String status = "open";

    @Column(name = "is_comfort_ride", nullable = false)
    private boolean comfortRide;

    @Column(name = "max_back_seat_passengers")
    private Integer maxBackSeatPassengers;

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

    @Column(name = "trip_started_at")
    private Instant tripStartedAt;

    @Column(name = "available_seats", nullable = false)
    private int availableSeats;

    @Column(name = "price_per_seat", nullable = false)
    private BigDecimal pricePerSeat = BigDecimal.ZERO;

    @Column(name = "is_recurring", nullable = false)
    private boolean recurring;

    /** JSON array of [lat,lng] points for the owner-selected path. */
    @Column(name = "route_geometry", columnDefinition = "TEXT")
    private String routeGeometry;

    @Column(name = "route_distance_m")
    private Double routeDistanceM;

    @Column(name = "route_duration_s")
    private Double routeDurationS;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }
    public UUID getVehicleId() { return vehicleId; }
    public void setVehicleId(UUID vehicleId) { this.vehicleId = vehicleId; }
    public String getRideType() { return rideType; }
    public void setRideType(String rideType) { this.rideType = rideType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isComfortRide() { return comfortRide; }
    public void setComfortRide(boolean comfortRide) { this.comfortRide = comfortRide; }
    public Integer getMaxBackSeatPassengers() { return maxBackSeatPassengers; }
    public void setMaxBackSeatPassengers(Integer maxBackSeatPassengers) { this.maxBackSeatPassengers = maxBackSeatPassengers; }
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
    public Instant getTripStartedAt() { return tripStartedAt; }
    public void setTripStartedAt(Instant tripStartedAt) { this.tripStartedAt = tripStartedAt; }
    public int getAvailableSeats() { return availableSeats; }
    public void setAvailableSeats(int availableSeats) { this.availableSeats = availableSeats; }
    public BigDecimal getPricePerSeat() { return pricePerSeat; }
    public void setPricePerSeat(BigDecimal pricePerSeat) { this.pricePerSeat = pricePerSeat; }
    public boolean isRecurring() { return recurring; }
    public void setRecurring(boolean recurring) { this.recurring = recurring; }
    public String getRouteGeometry() { return routeGeometry; }
    public void setRouteGeometry(String routeGeometry) { this.routeGeometry = routeGeometry; }
    public Double getRouteDistanceM() { return routeDistanceM; }
    public void setRouteDistanceM(Double routeDistanceM) { this.routeDistanceM = routeDistanceM; }
    public Double getRouteDurationS() { return routeDurationS; }
    public void setRouteDurationS(Double routeDurationS) { this.routeDurationS = routeDurationS; }
}
