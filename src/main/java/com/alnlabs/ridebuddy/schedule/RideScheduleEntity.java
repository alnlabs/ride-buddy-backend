package com.alnlabs.ridebuddy.schedule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "ride_schedules")
public class RideScheduleEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String kind;

    @Column(nullable = false)
    private String frequency;

    @Column(name = "days_of_week")
    private String daysOfWeek;

    @Column(name = "day_of_month")
    private Integer dayOfMonth;

    @Column(name = "depart_local_time", nullable = false)
    private LocalTime departLocalTime;

    @Column(nullable = false)
    private String timezone = "Asia/Kolkata";

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "vehicle_id")
    private UUID vehicleId;

    @Column(name = "available_seats")
    private Integer availableSeats;

    @Column(name = "price_per_seat")
    private BigDecimal pricePerSeat = BigDecimal.ZERO;

    @Column(name = "is_comfort_ride", nullable = false)
    private boolean comfortRide;

    @Column(name = "seats_needed")
    private Integer seatsNeeded;

    @Column(name = "comfort_preferred", nullable = false)
    private boolean comfortPreferred;

    @Column(name = "origin_lat", nullable = false)
    private double originLat;

    @Column(name = "origin_lng", nullable = false)
    private double originLng;

    @Column(name = "origin_label", nullable = false)
    private String originLabel;

    @Column(name = "origin_full_address")
    private String originFullAddress;

    @Column(name = "origin_private_label")
    private String originPrivateLabel;

    @Column(name = "destination_lat", nullable = false)
    private double destinationLat;

    @Column(name = "destination_lng", nullable = false)
    private double destinationLng;

    @Column(name = "destination_label", nullable = false)
    private String destinationLabel;

    @Column(name = "destination_full_address")
    private String destinationFullAddress;

    @Column(name = "destination_private_label")
    private String destinationPrivateLabel;

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
    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }
    public String getDaysOfWeek() { return daysOfWeek; }
    public void setDaysOfWeek(String daysOfWeek) { this.daysOfWeek = daysOfWeek; }
    public Integer getDayOfMonth() { return dayOfMonth; }
    public void setDayOfMonth(Integer dayOfMonth) { this.dayOfMonth = dayOfMonth; }
    public LocalTime getDepartLocalTime() { return departLocalTime; }
    public void setDepartLocalTime(LocalTime departLocalTime) { this.departLocalTime = departLocalTime; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public UUID getVehicleId() { return vehicleId; }
    public void setVehicleId(UUID vehicleId) { this.vehicleId = vehicleId; }
    public Integer getAvailableSeats() { return availableSeats; }
    public void setAvailableSeats(Integer availableSeats) { this.availableSeats = availableSeats; }
    public BigDecimal getPricePerSeat() { return pricePerSeat; }
    public void setPricePerSeat(BigDecimal pricePerSeat) { this.pricePerSeat = pricePerSeat; }
    public boolean isComfortRide() { return comfortRide; }
    public void setComfortRide(boolean comfortRide) { this.comfortRide = comfortRide; }
    public Integer getSeatsNeeded() { return seatsNeeded; }
    public void setSeatsNeeded(Integer seatsNeeded) { this.seatsNeeded = seatsNeeded; }
    public boolean isComfortPreferred() { return comfortPreferred; }
    public void setComfortPreferred(boolean comfortPreferred) { this.comfortPreferred = comfortPreferred; }
    public double getOriginLat() { return originLat; }
    public void setOriginLat(double originLat) { this.originLat = originLat; }
    public double getOriginLng() { return originLng; }
    public void setOriginLng(double originLng) { this.originLng = originLng; }
    public String getOriginLabel() { return originLabel; }
    public void setOriginLabel(String originLabel) { this.originLabel = originLabel; }
    public String getOriginFullAddress() { return originFullAddress; }
    public void setOriginFullAddress(String originFullAddress) { this.originFullAddress = originFullAddress; }
    public String getOriginPrivateLabel() { return originPrivateLabel; }
    public void setOriginPrivateLabel(String originPrivateLabel) { this.originPrivateLabel = originPrivateLabel; }
    public double getDestinationLat() { return destinationLat; }
    public void setDestinationLat(double destinationLat) { this.destinationLat = destinationLat; }
    public double getDestinationLng() { return destinationLng; }
    public void setDestinationLng(double destinationLng) { this.destinationLng = destinationLng; }
    public String getDestinationLabel() { return destinationLabel; }
    public void setDestinationLabel(String destinationLabel) { this.destinationLabel = destinationLabel; }
    public String getDestinationFullAddress() { return destinationFullAddress; }
    public void setDestinationFullAddress(String destinationFullAddress) { this.destinationFullAddress = destinationFullAddress; }
    public String getDestinationPrivateLabel() { return destinationPrivateLabel; }
    public void setDestinationPrivateLabel(String destinationPrivateLabel) { this.destinationPrivateLabel = destinationPrivateLabel; }
}
