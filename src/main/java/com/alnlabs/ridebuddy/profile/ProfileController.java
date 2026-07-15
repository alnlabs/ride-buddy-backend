package com.alnlabs.ridebuddy.profile;

import com.alnlabs.ridebuddy.common.AuthUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/me")
    public ProfileService.ProfileResponse me() {
        return profileService.getMe(AuthUser.requireUserId());
    }

    @PutMapping("/me")
    public ProfileService.ProfileResponse update(@RequestBody ProfileService.UpdateProfileRequest body) {
        return profileService.updateMe(AuthUser.requireUserId(), body);
    }

    @PutMapping("/me/places")
    public ProfileService.ProfileResponse places(@RequestBody ProfileService.UpdatePlacesRequest body) {
        return profileService.updatePlaces(AuthUser.requireUserId(), body);
    }

    @PutMapping("/me/interests")
    public ProfileService.ProfileResponse interests(@RequestBody ProfileService.SetInterestsRequest body) {
        return profileService.setInterests(
                AuthUser.requireUserId(),
                body.tags() != null ? body.tags() : List.of(),
                body.topTags()
        );
    }

    /** Placeholder mail flow — starts office-email verification for employee badge. */
    @PostMapping("/me/office-email/request")
    public Map<String, Object> requestOfficeEmail(@RequestBody ProfileService.OfficeEmailRequest body) {
        return profileService.requestOfficeEmailVerification(AuthUser.requireUserId(), body.email());
    }

    @PostMapping("/me/office-email/verify")
    public ProfileService.ProfileResponse verifyOfficeEmail(@RequestBody ProfileService.OfficeEmailVerifyRequest body) {
        return profileService.verifyOfficeEmail(AuthUser.requireUserId(), body.code());
    }

    @DeleteMapping("/me/office-email")
    public ProfileService.ProfileResponse clearOfficeEmail() {
        return profileService.clearOfficeEmail(AuthUser.requireUserId());
    }
}
