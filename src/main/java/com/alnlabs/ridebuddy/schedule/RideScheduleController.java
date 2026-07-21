package com.alnlabs.ridebuddy.schedule;

import com.alnlabs.ridebuddy.common.AuthUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ride-schedules")
public class RideScheduleController {

    private final RideScheduleService scheduleService;

    public RideScheduleController(RideScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @PostMapping
    public RideScheduleService.ScheduleResponse create(@RequestBody RideScheduleService.CreateScheduleRequest body) {
        return scheduleService.create(AuthUser.requireUserId(), body);
    }

    @GetMapping("/mine")
    public List<RideScheduleService.ScheduleResponse> mine() {
        return scheduleService.mine(AuthUser.requireUserId());
    }

    @PostMapping("/{id}/pause")
    public RideScheduleService.ScheduleResponse pause(@PathVariable UUID id) {
        return scheduleService.setActive(AuthUser.requireUserId(), id, false);
    }

    @PostMapping("/{id}/resume")
    public RideScheduleService.ScheduleResponse resume(@PathVariable UUID id) {
        return scheduleService.setActive(AuthUser.requireUserId(), id, true);
    }

    @PostMapping("/{id}/cancel")
    public RideScheduleService.ScheduleResponse cancel(@PathVariable UUID id) {
        return scheduleService.cancel(AuthUser.requireUserId(), id);
    }
}
