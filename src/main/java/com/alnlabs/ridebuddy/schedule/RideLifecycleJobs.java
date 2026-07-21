package com.alnlabs.ridebuddy.schedule;

import com.alnlabs.ridebuddy.booking.BookingEntity;
import com.alnlabs.ridebuddy.booking.BookingRepository;
import com.alnlabs.ridebuddy.request.RideOfferEntity;
import com.alnlabs.ridebuddy.request.RideOfferRepository;
import com.alnlabs.ridebuddy.request.RideRequestEntity;
import com.alnlabs.ridebuddy.request.RideRequestRepository;
import com.alnlabs.ridebuddy.ride.RideEntity;
import com.alnlabs.ridebuddy.ride.RideRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class RideLifecycleJobs {

    private static final Logger log = LoggerFactory.getLogger(RideLifecycleJobs.class);

    private final RideRepository rideRepo;
    private final RideRequestRepository requestRepo;
    private final RideOfferRepository offerRepo;
    private final BookingRepository bookingRepo;
    private final RideScheduleService scheduleService;

    public RideLifecycleJobs(
            RideRepository rideRepo,
            RideRequestRepository requestRepo,
            RideOfferRepository offerRepo,
            BookingRepository bookingRepo,
            RideScheduleService scheduleService
    ) {
        this.rideRepo = rideRepo;
        this.requestRepo = requestRepo;
        this.offerRepo = offerRepo;
        this.bookingRepo = bookingRepo;
        this.scheduleService = scheduleService;
    }

    @Scheduled(fixedDelayString = "900000") // 15 minutes
    @Transactional
    public void expireAndMaterialize() {
        Instant now = Instant.now();
        int expiredRides = 0;
        for (RideEntity ride : rideRepo.findByStatusInAndExpiresAtBefore(List.of("open", "full"), now)) {
            ride.setStatus("expired");
            rideRepo.save(ride);
            for (BookingEntity b : bookingRepo.findByRideIdOrderByCreatedAtAsc(ride.getId())) {
                if ("requested".equals(b.getStatus())) {
                    b.setStatus("cancelled");
                    bookingRepo.save(b);
                }
            }
            expiredRides++;
        }

        int expiredNeeds = 0;
        for (RideRequestEntity req : requestRepo.findByStatusAndExpiresAtBefore("open", now)) {
            req.setStatus("expired");
            requestRepo.save(req);
            for (RideOfferEntity offer : offerRepo.findByRequestIdAndStatus(req.getId(), "offered")) {
                offer.setStatus("cancelled");
                offerRepo.save(offer);
            }
            expiredNeeds++;
        }

        scheduleService.materializeAllActive();

        if (expiredRides > 0 || expiredNeeds > 0) {
            log.info("Expired {} rides and {} needs", expiredRides, expiredNeeds);
        }
    }
}
