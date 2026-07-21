package com.alnlabs.ridebuddy.schedule;

import com.alnlabs.ridebuddy.common.ApiException;
import com.alnlabs.ridebuddy.common.GeoUtils;
import com.alnlabs.ridebuddy.request.RideRequestService;
import com.alnlabs.ridebuddy.ride.RideService;
import com.alnlabs.ridebuddy.vehicle.VehicleService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RideScheduleService {

    private static final double MAX_TRIP_KM = 100.0;
    private static final Set<String> FREQUENCIES = Set.of(
            "daily", "weekdays", "weekends", "weekly", "monthly", "custom_days"
    );

    private final RideScheduleRepository scheduleRepo;
    private final RideService rideService;
    private final RideRequestService requestService;
    private final VehicleService vehicleService;

    public RideScheduleService(
            RideScheduleRepository scheduleRepo,
            RideService rideService,
            RideRequestService requestService,
            VehicleService vehicleService
    ) {
        this.scheduleRepo = scheduleRepo;
        this.rideService = rideService;
        this.requestService = requestService;
        this.vehicleService = vehicleService;
    }

    @Transactional
    public ScheduleResponse create(UUID ownerId, CreateScheduleRequest req) {
        if (req.kind() == null || (!"ride".equals(req.kind()) && !"need".equals(req.kind()))) {
            throw ApiException.badRequest("kind must be ride or need");
        }
        if (req.frequency() == null || !FREQUENCIES.contains(req.frequency())) {
            throw ApiException.badRequest("Invalid frequency");
        }
        if (req.departLocalTime() == null) {
            throw ApiException.badRequest("departLocalTime is required");
        }
        if (req.originLat() == null || req.originLng() == null
                || req.destinationLat() == null || req.destinationLng() == null) {
            throw ApiException.badRequest("Origin and destination coordinates are required");
        }
        if (!GeoUtils.withinKm(req.originLat(), req.originLng(), req.destinationLat(), req.destinationLng(), MAX_TRIP_KM)) {
            throw ApiException.badRequest("Destination must be within " + (int) MAX_TRIP_KM + " km of the start");
        }

        String days = normalizeDays(req.frequency(), req.daysOfWeek());
        if (("weekly".equals(req.frequency()) || "custom_days".equals(req.frequency()))
                && (days == null || days.isBlank())) {
            throw ApiException.badRequest("daysOfWeek is required for weekly / specific days");
        }
        if ("monthly".equals(req.frequency()) && (req.dayOfMonth() == null || req.dayOfMonth() < 1 || req.dayOfMonth() > 31)) {
            throw ApiException.badRequest("dayOfMonth (1–31) is required for monthly");
        }

        if ("ride".equals(req.kind())) {
            if (req.vehicleId() == null) {
                throw ApiException.badRequest("vehicleId is required for ride schedules");
            }
            vehicleService.requireOwnedActive(ownerId, req.vehicleId());
        } else {
            int seats = req.seatsNeeded() != null ? req.seatsNeeded() : 1;
            if (seats < 1 || seats > 3) {
                throw ApiException.badRequest("seatsNeeded must be 1–3");
            }
        }

        RideScheduleEntity s = new RideScheduleEntity();
        s.setOwnerId(ownerId);
        s.setKind(req.kind());
        s.setFrequency(req.frequency());
        s.setDaysOfWeek(days);
        s.setDayOfMonth(req.dayOfMonth());
        s.setDepartLocalTime(req.departLocalTime());
        s.setTimezone(req.timezone() != null && !req.timezone().isBlank() ? req.timezone() : "Asia/Kolkata");
        s.setActive(true);
        s.setVehicleId(req.vehicleId());
        s.setAvailableSeats(req.availableSeats());
        s.setPricePerSeat(req.pricePerSeat() != null ? req.pricePerSeat() : BigDecimal.ZERO);
        s.setComfortRide(Boolean.TRUE.equals(req.comfortRide()));
        s.setSeatsNeeded(req.seatsNeeded() != null ? req.seatsNeeded() : 1);
        s.setComfortPreferred(Boolean.TRUE.equals(req.comfortPreferred()));
        s.setOriginLat(req.originLat());
        s.setOriginLng(req.originLng());
        s.setOriginLabel(firstNonBlank(req.originPublicShort(), req.originLabel(), "Origin"));
        s.setOriginFullAddress(blankToNull(req.originFullAddress()));
        s.setOriginPrivateLabel(blankToNull(req.originPrivateLabel()));
        s.setDestinationLat(req.destinationLat());
        s.setDestinationLng(req.destinationLng());
        s.setDestinationLabel(firstNonBlank(req.destinationPublicShort(), req.destinationLabel(), "Destination"));
        s.setDestinationFullAddress(blankToNull(req.destinationFullAddress()));
        s.setDestinationPrivateLabel(blankToNull(req.destinationPrivateLabel()));
        scheduleRepo.save(s);

        materializeUpcoming(s);
        return toResponse(s);
    }

    public List<ScheduleResponse> mine(UUID ownerId) {
        return scheduleRepo.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ScheduleResponse setActive(UUID ownerId, UUID id, boolean active) {
        RideScheduleEntity s = scheduleRepo.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> ApiException.notFound("Schedule not found"));
        s.setActive(active);
        scheduleRepo.save(s);
        if (active) {
            materializeUpcoming(s);
        }
        return toResponse(s);
    }

    @Transactional
    public ScheduleResponse cancel(UUID ownerId, UUID id) {
        return setActive(ownerId, id, false);
    }

    /** Called by scheduler: materialize today + tomorrow for all active schedules. */
    @Transactional
    public void materializeAllActive() {
        for (RideScheduleEntity s : scheduleRepo.findByActiveTrue()) {
            materializeUpcoming(s);
        }
    }

    private void materializeUpcoming(RideScheduleEntity s) {
        ZoneId zone = ZoneId.of(s.getTimezone() != null ? s.getTimezone() : "Asia/Kolkata");
        LocalDate today = LocalDate.now(zone);
        for (int offset = 0; offset <= 1; offset++) {
            LocalDate day = today.plusDays(offset);
            if (!matches(s, day)) {
                continue;
            }
            Instant departAt = ZonedDateTime.of(day, s.getDepartLocalTime(), zone).toInstant();
            if (!departAt.isAfter(Instant.now())) {
                continue;
            }
            if ("ride".equals(s.getKind())) {
                rideService.materializeScheduled(
                        s.getOwnerId(),
                        s.getVehicleId(),
                        s.getId(),
                        day,
                        departAt,
                        s.isComfortRide(),
                        s.getAvailableSeats(),
                        s.getPricePerSeat(),
                        s.getOriginLat(),
                        s.getOriginLng(),
                        s.getOriginLabel(),
                        s.getOriginFullAddress(),
                        s.getOriginPrivateLabel(),
                        s.getDestinationLat(),
                        s.getDestinationLng(),
                        s.getDestinationLabel(),
                        s.getDestinationFullAddress(),
                        s.getDestinationPrivateLabel()
                );
            } else {
                requestService.materializeScheduled(
                        s.getOwnerId(),
                        s.getId(),
                        day,
                        departAt,
                        s.getSeatsNeeded() != null ? s.getSeatsNeeded() : 1,
                        s.isComfortPreferred(),
                        s.getOriginLat(),
                        s.getOriginLng(),
                        s.getOriginLabel(),
                        s.getOriginFullAddress(),
                        s.getOriginPrivateLabel(),
                        s.getDestinationLat(),
                        s.getDestinationLng(),
                        s.getDestinationLabel(),
                        s.getDestinationFullAddress(),
                        s.getDestinationPrivateLabel()
                );
            }
        }
    }

    static boolean matches(RideScheduleEntity s, LocalDate day) {
        int iso = day.getDayOfWeek().getValue(); // 1=Mon … 7=Sun
        return switch (s.getFrequency()) {
            case "daily" -> true;
            case "weekdays" -> iso >= 1 && iso <= 5;
            case "weekends" -> iso >= 6;
            case "weekly", "custom_days" -> parseDays(s.getDaysOfWeek()).contains(iso);
            case "monthly" -> s.getDayOfMonth() != null
                    && day.getDayOfMonth() == s.getDayOfMonth()
                    && day.lengthOfMonth() >= s.getDayOfMonth();
            default -> false;
        };
    }

    private static Set<Integer> parseDays(String raw) {
        if (raw == null || raw.isBlank()) return Set.of();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .map(Integer::parseInt)
                .collect(Collectors.toSet());
    }

    private static String normalizeDays(String frequency, List<Integer> daysOfWeek) {
        if ("weekdays".equals(frequency)) return "1,2,3,4,5";
        if ("weekends".equals(frequency)) return "6,7";
        if ("daily".equals(frequency)) return "1,2,3,4,5,6,7";
        if (daysOfWeek == null || daysOfWeek.isEmpty()) return null;
        List<Integer> cleaned = new ArrayList<>();
        for (Integer d : daysOfWeek) {
            if (d == null || d < 1 || d > 7) {
                throw ApiException.badRequest("daysOfWeek values must be 1–7 (Mon–Sun)");
            }
            if (!cleaned.contains(d)) cleaned.add(d);
        }
        cleaned.sort(Integer::compareTo);
        return cleaned.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private ScheduleResponse toResponse(RideScheduleEntity s) {
        List<Integer> days = parseDays(s.getDaysOfWeek()).stream().sorted().toList();
        return new ScheduleResponse(
                s.getId(),
                s.getOwnerId(),
                s.getKind(),
                s.getFrequency(),
                days,
                s.getDayOfMonth(),
                s.getDepartLocalTime(),
                s.getTimezone(),
                s.isActive(),
                s.getVehicleId(),
                s.getAvailableSeats(),
                s.getPricePerSeat(),
                s.isComfortRide(),
                s.getSeatsNeeded(),
                s.isComfortPreferred(),
                s.getOriginLat(),
                s.getOriginLng(),
                s.getOriginLabel(),
                s.getOriginFullAddress(),
                s.getOriginPrivateLabel(),
                s.getDestinationLat(),
                s.getDestinationLng(),
                s.getDestinationLabel(),
                s.getDestinationFullAddress(),
                s.getDestinationPrivateLabel()
        );
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return "Place";
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    public record CreateScheduleRequest(
            String kind,
            String frequency,
            List<Integer> daysOfWeek,
            Integer dayOfMonth,
            LocalTime departLocalTime,
            String timezone,
            UUID vehicleId,
            Integer availableSeats,
            BigDecimal pricePerSeat,
            Boolean comfortRide,
            Integer seatsNeeded,
            Boolean comfortPreferred,
            Double originLat,
            Double originLng,
            String originLabel,
            String originPublicShort,
            String originFullAddress,
            String originPrivateLabel,
            Double destinationLat,
            Double destinationLng,
            String destinationLabel,
            String destinationPublicShort,
            String destinationFullAddress,
            String destinationPrivateLabel
    ) {}

    public record ScheduleResponse(
            UUID id,
            UUID ownerId,
            String kind,
            String frequency,
            List<Integer> daysOfWeek,
            Integer dayOfMonth,
            LocalTime departLocalTime,
            String timezone,
            boolean active,
            UUID vehicleId,
            Integer availableSeats,
            BigDecimal pricePerSeat,
            boolean comfortRide,
            Integer seatsNeeded,
            boolean comfortPreferred,
            double originLat,
            double originLng,
            String originLabel,
            String originFullAddress,
            String originPrivateLabel,
            double destinationLat,
            double destinationLng,
            String destinationLabel,
            String destinationFullAddress,
            String destinationPrivateLabel
    ) {}
}
