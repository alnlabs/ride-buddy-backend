package com.alnlabs.ridebuddy.profile;

import com.alnlabs.ridebuddy.common.AuthUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final ProfileService profileService;
    private final UserPlaceService userPlaceService;

    public ProfileController(ProfileService profileService, UserPlaceService userPlaceService) {
        this.profileService = profileService;
        this.userPlaceService = userPlaceService;
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

    @GetMapping("/me/saved-places")
    public List<UserPlaceService.UserPlaceResponse> listSavedPlaces() {
        return userPlaceService.list(AuthUser.requireUserId());
    }

    @PostMapping("/me/saved-places")
    public UserPlaceService.UserPlaceResponse createSavedPlace(@RequestBody UserPlaceService.UpsertPlaceRequest body) {
        return userPlaceService.create(AuthUser.requireUserId(), body);
    }

    @PutMapping("/me/saved-places/{id}")
    public UserPlaceService.UserPlaceResponse updateSavedPlace(
            @PathVariable UUID id,
            @RequestBody UserPlaceService.UpsertPlaceRequest body
    ) {
        return userPlaceService.update(AuthUser.requireUserId(), id, body);
    }

    @DeleteMapping("/me/saved-places/{id}")
    public Map<String, Object> deleteSavedPlace(@PathVariable UUID id) {
        userPlaceService.delete(AuthUser.requireUserId(), id);
        return Map.of("ok", true);
    }

    @PostMapping("/me/saved-places/{id}/primary")
    public UserPlaceService.UserPlaceResponse setPrimarySavedPlace(@PathVariable UUID id) {
        return userPlaceService.setPrimary(AuthUser.requireUserId(), id);
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
