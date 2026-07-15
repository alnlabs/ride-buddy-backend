package com.alnlabs.ridebuddy.profile;

import com.alnlabs.ridebuddy.common.ApiException;
import com.alnlabs.ridebuddy.common.GeoUtils;
import com.alnlabs.ridebuddy.config.AuthProperties;
import com.alnlabs.ridebuddy.mail.MailService;
import com.alnlabs.ridebuddy.vehicle.VehicleRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

@Service
public class ProfileService {

    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Set<String> SOCIAL_MAIL_DOMAINS = Set.of(
            "gmail.com", "googlemail.com", "yahoo.com", "yahoo.co.in", "hotmail.com",
            "outlook.com", "live.com", "msn.com", "icloud.com", "me.com", "mac.com",
            "aol.com", "proton.me", "protonmail.com", "zoho.com", "yandex.com", "mail.com",
            "gmx.com", "rediffmail.com"
    );

    private final ProfileRepository profileRepo;
    private final ProfileInterestRepository interestRepo;
    private final VehicleRepository vehicleRepo;
    private final MailService mailService;
    private final AuthProperties authProperties;

    public ProfileService(
            ProfileRepository profileRepo,
            ProfileInterestRepository interestRepo,
            VehicleRepository vehicleRepo,
            MailService mailService,
            AuthProperties authProperties
    ) {
        this.profileRepo = profileRepo;
        this.interestRepo = interestRepo;
        this.vehicleRepo = vehicleRepo;
        this.mailService = mailService;
        this.authProperties = authProperties;
    }

    public ProfileResponse getMe(UUID userId) {
        ProfileEntity p = require(userId);
        List<ProfileInterestEntity> interests = interestRepo.findByUserIdOrderBySortOrderAscCreatedAtAsc(userId);
        return toResponse(p, interests);
    }

    public PosterCard posterCard(UUID userId) {
        ProfileEntity p = profileRepo.findById(userId).orElse(null);
        if (p == null) {
            return new PosterCard(userId, "Rider", null, null, List.of(), false);
        }
        List<String> top = topInterestTags(userId);
        String name = p.getDisplayName() != null && !p.getDisplayName().isBlank()
                ? p.getDisplayName().trim()
                : "Rider";
        return new PosterCard(
                userId,
                name,
                blankToNull(p.getJobRole()),
                blankToNull(p.getCompany()),
                top,
                p.isOfficeEmailVerified()
        );
    }

    public List<String> topInterestTags(UUID userId) {
        return interestRepo.findByUserIdOrderBySortOrderAscCreatedAtAsc(userId).stream()
                .map(ProfileInterestEntity::getTag)
                .limit(5)
                .toList();
    }

    @Transactional
    public ProfileResponse updateMe(UUID userId, UpdateProfileRequest req) {
        ProfileEntity p = require(userId);
        if (req.displayName() != null) {
            p.setDisplayName(req.displayName().trim());
        }
        if (req.avatarUrl() != null) {
            p.setAvatarUrl(req.avatarUrl());
        }
        if (req.experienceBio() != null) {
            p.setExperienceBio(req.experienceBio());
        }
        if (req.yearsExperience() != null) {
            p.setYearsExperience(req.yearsExperience());
        }
        if (req.canOfferRides() != null) {
            p.setCanOfferRides(req.canOfferRides());
        }
        if (req.emergencyContactName() != null) {
            p.setEmergencyContactName(req.emergencyContactName());
        }
        if (req.emergencyContactPhone() != null) {
            p.setEmergencyContactPhone(req.emergencyContactPhone());
        }
        if (req.jobRole() != null) {
            p.setJobRole(req.jobRole().isBlank() ? null : req.jobRole().trim());
        }
        if (req.company() != null) {
            p.setCompany(req.company().isBlank() ? null : req.company().trim());
        }
        if (req.contactEmail() != null) {
            String contact = req.contactEmail().trim().toLowerCase(Locale.ROOT);
            if (contact.isBlank()) {
                p.setContactEmail(null);
            } else {
                if (!EMAIL.matcher(contact).matches()) {
                    throw ApiException.badRequest("Invalid contact email");
                }
                p.setContactEmail(contact);
            }
        }
        recalculateStrength(p, userId);
        profileRepo.save(p);
        return getMe(userId);
    }

    @Transactional
    public Map<String, Object> requestOfficeEmailVerification(UUID userId, String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) {
            throw ApiException.badRequest("Office email is required");
        }
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(email).matches()) {
            throw ApiException.badRequest("Invalid office email");
        }
        String domain = email.substring(email.indexOf('@') + 1);
        if (SOCIAL_MAIL_DOMAINS.contains(domain)) {
            throw ApiException.badRequest(
                    "Use your company email for employee verification. Gmail / Yahoo / Outlook can be added as personal email instead."
            );
        }

        ProfileEntity p = require(userId);
        String code = authProperties.mockOtp()
                ? authProperties.mockOtpCode()
                : String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));

        p.setOfficeEmail(email);
        p.setOfficeEmailVerified(false);
        p.setOfficeEmailPendingCode(code);
        p.setOfficeEmailCodeExpiresAt(Instant.now().plusSeconds(30 * 60));
        profileRepo.save(p);

        mailService.sendOfficeEmailVerification(email, code);

        return Map.of(
                "officeEmail", email,
                "officeEmailStatus", "pending",
                "employeeVerified", false,
                "mailPlaceholder", true,
                "expiresInSeconds", 1800,
                "hint", authProperties.mockOtp()
                        ? "Email service not wired yet — use code " + code
                        : "Check your office inbox for a verification code (placeholder mailer logs in server)"
        );
    }

    @Transactional
    public ProfileResponse verifyOfficeEmail(UUID userId, String code) {
        if (code == null || code.isBlank()) {
            throw ApiException.badRequest("Verification code is required");
        }
        ProfileEntity p = require(userId);
        if (p.getOfficeEmail() == null || p.getOfficeEmailPendingCode() == null) {
            throw ApiException.badRequest("No office email verification pending");
        }
        if (p.getOfficeEmailCodeExpiresAt() == null || p.getOfficeEmailCodeExpiresAt().isBefore(Instant.now())) {
            throw ApiException.unauthorized("Verification code expired — request a new one");
        }
        if (!p.getOfficeEmailPendingCode().equals(code.trim())) {
            throw ApiException.unauthorized("Invalid verification code");
        }
        p.setOfficeEmailVerified(true);
        p.setOfficeEmailPendingCode(null);
        p.setOfficeEmailCodeExpiresAt(null);
        recalculateStrength(p, userId);
        profileRepo.save(p);
        return getMe(userId);
    }

    @Transactional
    public ProfileResponse clearOfficeEmail(UUID userId) {
        ProfileEntity p = require(userId);
        p.setOfficeEmail(null);
        p.setOfficeEmailVerified(false);
        p.setOfficeEmailPendingCode(null);
        p.setOfficeEmailCodeExpiresAt(null);
        recalculateStrength(p, userId);
        profileRepo.save(p);
        return getMe(userId);
    }

    @Transactional
    public ProfileResponse updatePlaces(UUID userId, UpdatePlacesRequest req) {
        ProfileEntity p = require(userId);
        if (req.homeLat() != null && req.homeLng() != null) {
            p.setHomeLat(req.homeLat());
            p.setHomeLng(req.homeLng());
            p.setHomeLabel(req.homeLabel());
            p.setHomeAreaSlug(GeoUtils.areaSlug(req.homeLabel()));
        }
        if (req.officeLat() != null && req.officeLng() != null) {
            p.setOfficeLat(req.officeLat());
            p.setOfficeLng(req.officeLng());
            p.setOfficeLabel(req.officeLabel());
            p.setOfficeAreaSlug(GeoUtils.areaSlug(req.officeLabel()));
        }
        recalculateStrength(p, userId);
        profileRepo.save(p);
        return getMe(userId);
    }

    @Transactional
    public ProfileResponse setInterests(UUID userId, List<String> tags, List<String> topTags) {
        if (tags == null || tags.isEmpty() || tags.size() > 30) {
            throw ApiException.badRequest("Provide 1–30 interest tags");
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String tag : tags) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String t = tag.trim().toLowerCase(Locale.ROOT);
            if (t.length() > 40) {
                throw ApiException.badRequest("Interest tags must be under 40 characters");
            }
            normalized.add(t);
        }
        if (normalized.isEmpty()) {
            throw ApiException.badRequest("Provide 1–30 interest tags");
        }

        List<String> featured = resolveTopTags(normalized, topTags);

        interestRepo.deleteByUserId(userId);

        int index = 0;
        for (String tag : featured) {
            ProfileInterestEntity e = new ProfileInterestEntity();
            e.setUserId(userId);
            e.setTag(tag);
            e.setSortOrder(index++);
            interestRepo.save(e);
        }
        for (String tag : normalized) {
            if (featured.contains(tag)) {
                continue;
            }
            ProfileInterestEntity e = new ProfileInterestEntity();
            e.setUserId(userId);
            e.setTag(tag);
            e.setSortOrder(100 + index++);
            interestRepo.save(e);
        }

        ProfileEntity p = require(userId);
        recalculateStrength(p, userId);
        profileRepo.save(p);
        return getMe(userId);
    }

    private static List<String> resolveTopTags(Set<String> all, List<String> topTags) {
        List<String> featured = new ArrayList<>();
        if (topTags != null) {
            for (String tag : topTags) {
                if (tag == null || tag.isBlank()) {
                    continue;
                }
                String t = tag.trim().toLowerCase(Locale.ROOT);
                if (all.contains(t) && !featured.contains(t)) {
                    featured.add(t);
                }
                if (featured.size() >= 5) {
                    break;
                }
            }
        }
        if (featured.isEmpty()) {
            for (String tag : all) {
                featured.add(tag);
                if (featured.size() >= 5) {
                    break;
                }
            }
        }
        return featured;
    }

    @Transactional
    public void refreshStrength(UUID userId) {
        ProfileEntity p = require(userId);
        recalculateStrength(p, userId);
        profileRepo.save(p);
    }

    private void recalculateStrength(ProfileEntity p, UUID userId) {
        int score = 0;
        if (p.getDisplayName() != null && !p.getDisplayName().isBlank()) {
            score += 10;
        }
        if (blankToNull(p.getJobRole()) != null && blankToNull(p.getCompany()) != null) {
            score += 8;
        } else if (blankToNull(p.getJobRole()) != null || blankToNull(p.getCompany()) != null) {
            score += 4;
        }
        if (p.isOfficeEmailVerified()) {
            score += 10;
        } else if (blankToNull(p.getOfficeEmail()) != null) {
            score += 3;
        }
        if (blankToNull(p.getContactEmail()) != null) {
            score += 2;
        }
        if (p.getHomeLat() != null && p.getOfficeLat() != null) {
            score += 18;
        } else if (p.getHomeLat() != null || p.getOfficeLat() != null) {
            score += 9;
        }
        if (p.getExperienceBio() != null && !p.getExperienceBio().isBlank()) {
            score += 8;
        }
        long interestCount = interestRepo.countByUserId(userId);
        if (interestCount >= 5) {
            score += 14;
        } else if (interestCount > 0) {
            score += (int) (14 * interestCount / 5.0);
        }
        if (p.getEmergencyContactPhone() != null && !p.getEmergencyContactPhone().isBlank()) {
            score += 10;
        }
        long vehicles = vehicleRepo.countByOwnerIdAndIsActiveTrue(userId);
        if (vehicles >= 1) {
            score += 20;
        }
        p.setProfileStrength(Math.min(100, score));
    }

    private ProfileEntity require(UUID userId) {
        return profileRepo.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Profile not found"));
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String officeEmailStatus(ProfileEntity p) {
        if (p.isOfficeEmailVerified()) {
            return "verified";
        }
        if (blankToNull(p.getOfficeEmail()) != null && blankToNull(p.getOfficeEmailPendingCode()) != null) {
            return "pending";
        }
        if (blankToNull(p.getOfficeEmail()) != null) {
            return "unverified";
        }
        return "none";
    }

    private ProfileResponse toResponse(ProfileEntity p, List<ProfileInterestEntity> interests) {
        List<String> all = interests.stream().map(ProfileInterestEntity::getTag).toList();
        List<String> top = interests.stream().map(ProfileInterestEntity::getTag).limit(5).toList();
        return new ProfileResponse(
                p.getUserId(),
                p.getDisplayName(),
                p.getAvatarUrl(),
                p.getHomeLat(),
                p.getHomeLng(),
                p.getHomeLabel(),
                p.getHomeAreaSlug(),
                p.getOfficeLat(),
                p.getOfficeLng(),
                p.getOfficeLabel(),
                p.getOfficeAreaSlug(),
                p.getExperienceBio(),
                p.getYearsExperience(),
                p.isCanOfferRides(),
                p.getProfileStrength(),
                p.getEmergencyContactName(),
                p.getEmergencyContactPhone(),
                blankToNull(p.getJobRole()),
                blankToNull(p.getCompany()),
                blankToNull(p.getContactEmail()),
                blankToNull(p.getOfficeEmail()),
                officeEmailStatus(p),
                p.isOfficeEmailVerified(),
                all,
                top
        );
    }

    public record PosterCard(
            UUID userId,
            String displayName,
            String jobRole,
            String company,
            List<String> topInterests,
            boolean employeeVerified
    ) {}

    public record ProfileResponse(
            UUID userId,
            String displayName,
            String avatarUrl,
            Double homeLat,
            Double homeLng,
            String homeLabel,
            String homeAreaSlug,
            Double officeLat,
            Double officeLng,
            String officeLabel,
            String officeAreaSlug,
            String experienceBio,
            Integer yearsExperience,
            boolean canOfferRides,
            int profileStrength,
            String emergencyContactName,
            String emergencyContactPhone,
            String jobRole,
            String company,
            String contactEmail,
            String officeEmail,
            String officeEmailStatus,
            boolean employeeVerified,
            List<String> interests,
            List<String> topInterests
    ) {}

    public record UpdateProfileRequest(
            String displayName,
            String avatarUrl,
            String experienceBio,
            Integer yearsExperience,
            Boolean canOfferRides,
            String emergencyContactName,
            String emergencyContactPhone,
            String jobRole,
            String company,
            String contactEmail
    ) {}

    public record UpdatePlacesRequest(
            Double homeLat,
            Double homeLng,
            String homeLabel,
            Double officeLat,
            Double officeLng,
            String officeLabel
    ) {}

    public record SetInterestsRequest(List<String> tags, List<String> topTags) {}

    public record OfficeEmailRequest(String email) {}

    public record OfficeEmailVerifyRequest(String code) {}
}
