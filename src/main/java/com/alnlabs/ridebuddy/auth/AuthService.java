package com.alnlabs.ridebuddy.auth;

import com.alnlabs.ridebuddy.common.ApiException;
import com.alnlabs.ridebuddy.config.AuthProperties;
import com.alnlabs.ridebuddy.profile.ProfileEntity;
import com.alnlabs.ridebuddy.profile.ProfileRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{10,15}$");

    private final AuthProperties authProperties;
    private final OtpChallengeRepository otpRepo;
    private final UserRepository userRepo;
    private final ProfileRepository profileRepo;
    private final JwtService jwtService;

    public AuthService(
            AuthProperties authProperties,
            OtpChallengeRepository otpRepo,
            UserRepository userRepo,
            ProfileRepository profileRepo,
            JwtService jwtService
    ) {
        this.authProperties = authProperties;
        this.otpRepo = otpRepo;
        this.userRepo = userRepo;
        this.profileRepo = profileRepo;
        this.jwtService = jwtService;
    }

    @Transactional
    public Map<String, Object> requestOtp(String rawPhone) {
        String phone = normalizePhone(rawPhone);
        String code = authProperties.mockOtp()
                ? authProperties.mockOtpCode()
                : String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));

        OtpChallengeEntity challenge = new OtpChallengeEntity();
        challenge.setPhone(phone);
        challenge.setCode(code);
        challenge.setExpiresAt(Instant.now().plusSeconds(10 * 60));
        otpRepo.save(challenge);

        boolean needsDisplayName = userRepo.findByPhone(phone)
                .map(user -> profileRepo.findById(user.getId())
                        .map(this::isPlaceholderDisplayName)
                        .orElse(true))
                .orElse(true);

        return Map.of(
                "phone", phone,
                "expiresInSeconds", 600,
                "mock", authProperties.mockOtp(),
                "needsDisplayName", needsDisplayName,
                "hint", authProperties.mockOtp() ? "Use OTP " + authProperties.mockOtpCode() : "OTP sent"
        );
    }

    private boolean isPlaceholderDisplayName(ProfileEntity profile) {
        String name = profile.getDisplayName();
        return name == null || name.isBlank() || "Rider".equals(name.trim());
    }

    @Transactional
    public TokenResponse verifyOtp(String rawPhone, String code, String displayName) {
        String phone = normalizePhone(rawPhone);
        OtpChallengeEntity challenge = otpRepo.findFirstByPhoneAndConsumedFalseOrderByCreatedAtDesc(phone)
                .orElseThrow(() -> ApiException.unauthorized("No OTP pending for this phone"));

        if (challenge.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.unauthorized("OTP expired");
        }
        if (!challenge.getCode().equals(code.trim())) {
            throw ApiException.unauthorized("Invalid OTP");
        }
        challenge.setConsumed(true);
        otpRepo.save(challenge);

        UserEntity user = userRepo.findByPhone(phone).orElseGet(() -> {
            UserEntity u = new UserEntity();
            u.setPhone(phone);
            return userRepo.save(u);
        });

        ProfileEntity profile = profileRepo.findById(user.getId()).orElseGet(() -> {
            ProfileEntity p = new ProfileEntity();
            p.setUserId(user.getId());
            p.setDisplayName(displayName != null && !displayName.isBlank() ? displayName.trim() : "Rider");
            return profileRepo.save(p);
        });

        if (displayName != null && !displayName.isBlank()
                && (profile.getDisplayName() == null || "Rider".equals(profile.getDisplayName()))) {
            profile.setDisplayName(displayName.trim());
            profileRepo.save(profile);
        }

        String access = jwtService.createAccessToken(user.getId(), phone);
        String refresh = jwtService.createRefreshToken(user.getId());
        boolean needsName = isPlaceholderDisplayName(profile);

        return new TokenResponse(access, refresh, user.getId(), phone, profile.getDisplayName(), needsName);
    }

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        try {
            var claims = jwtService.parse(refreshToken);
            if (!"refresh".equals(claims.get("type", String.class))) {
                throw ApiException.unauthorized("Invalid refresh token");
            }
            UserEntity user = userRepo.findById(java.util.UUID.fromString(claims.getSubject()))
                    .orElseThrow(() -> ApiException.unauthorized("User not found"));
            ProfileEntity profile = profileRepo.findById(user.getId()).orElse(null);
            String name = profile != null ? profile.getDisplayName() : null;
            return new TokenResponse(
                    jwtService.createAccessToken(user.getId(), user.getPhone()),
                    jwtService.createRefreshToken(user.getId()),
                    user.getId(),
                    user.getPhone(),
                    name,
                    false
            );
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.unauthorized("Invalid refresh token");
        }
    }

    private String normalizePhone(String raw) {
        if (raw == null) {
            throw ApiException.badRequest("Phone is required");
        }
        String phone = raw.trim().replaceAll("\\s+", "");
        if (phone.length() == 10 && phone.chars().allMatch(Character::isDigit)) {
            phone = "+91" + phone;
        }
        if (!PHONE.matcher(phone).matches()) {
            throw ApiException.badRequest("Invalid phone number");
        }
        return phone;
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            java.util.UUID userId,
            String phone,
            String displayName,
            boolean needsDisplayName
    ) {}
}
