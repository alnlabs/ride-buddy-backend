package com.alnlabs.ridebuddy.profile;

import com.alnlabs.ridebuddy.common.ApiException;
import com.alnlabs.ridebuddy.common.GeoUtils;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class UserPlaceService {

    private final UserPlaceRepository placeRepo;
    private final ProfileRepository profileRepo;
    private final ProfileService profileService;

    public UserPlaceService(
            UserPlaceRepository placeRepo,
            ProfileRepository profileRepo,
            ProfileService profileService
    ) {
        this.placeRepo = placeRepo;
        this.profileRepo = profileRepo;
        this.profileService = profileService;
    }

    public List<UserPlaceResponse> list(UUID userId) {
        return placeRepo.findByUserIdOrderByKindAscCreatedAtAsc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public UserPlaceResponse create(UUID userId, UpsertPlaceRequest req) {
        String kind = normalizeKind(req.kind());
        validateCoords(req.lat(), req.lng());
        String privateLabel = requireLabel(req.privateLabel(), kind);
        String publicShort = blankTo(req.publicShort(), privateLabel);
        boolean makePrimary = Boolean.TRUE.equals(req.primary())
                || placeRepo.findByUserIdAndKindOrderByCreatedAtAsc(userId, kind).isEmpty();

        if (makePrimary) {
            placeRepo.clearPrimary(userId, kind);
        }

        UserPlaceEntity e = new UserPlaceEntity();
        e.setUserId(userId);
        e.setKind(kind);
        e.setPrivateLabel(privateLabel);
        e.setPublicShort(publicShort);
        e.setFullAddress(blankToNull(req.fullAddress()));
        e.setLat(req.lat());
        e.setLng(req.lng());
        e.setPrimary(makePrimary);
        placeRepo.save(e);
        syncPrimaryToProfile(userId, kind);
        profileService.refreshStrength(userId);
        return toResponse(e);
    }

    @Transactional
    public UserPlaceResponse update(UUID userId, UUID placeId, UpsertPlaceRequest req) {
        UserPlaceEntity e = placeRepo.findByIdAndUserId(placeId, userId)
                .orElseThrow(() -> ApiException.notFound("Place not found"));
        if (req.kind() != null && !req.kind().isBlank() && !e.getKind().equals(normalizeKind(req.kind()))) {
            throw ApiException.badRequest("Cannot change place kind");
        }
        if (req.lat() != null && req.lng() != null) {
            validateCoords(req.lat(), req.lng());
            e.setLat(req.lat());
            e.setLng(req.lng());
        }
        if (req.privateLabel() != null && !req.privateLabel().isBlank()) {
            e.setPrivateLabel(req.privateLabel().trim());
        }
        if (req.publicShort() != null && !req.publicShort().isBlank()) {
            e.setPublicShort(req.publicShort().trim());
        }
        if (req.fullAddress() != null) {
            e.setFullAddress(blankToNull(req.fullAddress()));
        }
        if (Boolean.TRUE.equals(req.primary())) {
            placeRepo.clearPrimary(userId, e.getKind());
            e.setPrimary(true);
        }
        placeRepo.save(e);
        syncPrimaryToProfile(userId, e.getKind());
        profileService.refreshStrength(userId);
        return toResponse(e);
    }

    @Transactional
    public void delete(UUID userId, UUID placeId) {
        UserPlaceEntity e = placeRepo.findByIdAndUserId(placeId, userId)
                .orElseThrow(() -> ApiException.notFound("Place not found"));
        String kind = e.getKind();
        boolean wasPrimary = e.isPrimary();
        placeRepo.delete(e);
        if (wasPrimary) {
            List<UserPlaceEntity> rest = placeRepo.findByUserIdAndKindOrderByCreatedAtAsc(userId, kind);
            if (!rest.isEmpty()) {
                UserPlaceEntity next = rest.get(0);
                next.setPrimary(true);
                placeRepo.save(next);
            }
            syncPrimaryToProfile(userId, kind);
        }
        profileService.refreshStrength(userId);
    }

    @Transactional
    public UserPlaceResponse setPrimary(UUID userId, UUID placeId) {
        UserPlaceEntity e = placeRepo.findByIdAndUserId(placeId, userId)
                .orElseThrow(() -> ApiException.notFound("Place not found"));
        placeRepo.clearPrimary(userId, e.getKind());
        e.setPrimary(true);
        placeRepo.save(e);
        syncPrimaryToProfile(userId, e.getKind());
        return toResponse(e);
    }

    /** Keep legacy profile home/office columns aligned with primary places. */
    private void syncPrimaryToProfile(UUID userId, String kind) {
        ProfileEntity p = profileRepo.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Profile not found"));
        UserPlaceEntity primary = placeRepo.findFirstByUserIdAndKindAndPrimaryTrue(userId, kind).orElse(null);
        if ("home".equals(kind)) {
            if (primary == null) {
                p.setHomeLat(null);
                p.setHomeLng(null);
                p.setHomeLabel(null);
                p.setHomeAreaSlug(null);
            } else {
                p.setHomeLat(primary.getLat());
                p.setHomeLng(primary.getLng());
                p.setHomeLabel(primary.getPrivateLabel());
                p.setHomeAreaSlug(GeoUtils.areaSlug(primary.getPublicShort()));
            }
        } else {
            if (primary == null) {
                p.setOfficeLat(null);
                p.setOfficeLng(null);
                p.setOfficeLabel(null);
                p.setOfficeAreaSlug(null);
            } else {
                p.setOfficeLat(primary.getLat());
                p.setOfficeLng(primary.getLng());
                p.setOfficeLabel(primary.getPrivateLabel());
                p.setOfficeAreaSlug(GeoUtils.areaSlug(primary.getPublicShort()));
            }
        }
        profileRepo.save(p);
    }

    private UserPlaceResponse toResponse(UserPlaceEntity e) {
        return new UserPlaceResponse(
                e.getId(),
                e.getKind(),
                e.getPrivateLabel(),
                e.getPublicShort(),
                e.getFullAddress(),
                e.getLat(),
                e.getLng(),
                e.isPrimary()
        );
    }

    private static String normalizeKind(String kind) {
        if (kind == null || kind.isBlank()) {
            throw ApiException.badRequest("kind must be home or office");
        }
        String k = kind.trim().toLowerCase(Locale.ROOT);
        if (!"home".equals(k) && !"office".equals(k)) {
            throw ApiException.badRequest("kind must be home or office");
        }
        return k;
    }

    private static void validateCoords(Double lat, Double lng) {
        if (lat == null || lng == null) {
            throw ApiException.badRequest("lat and lng are required");
        }
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw ApiException.badRequest("Invalid coordinates");
        }
    }

    private static String requireLabel(String label, String kind) {
        if (label != null && !label.isBlank()) {
            return label.trim();
        }
        return "home".equals(kind) ? "Home" : "Office";
    }

    private static String blankTo(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.trim();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    public record UserPlaceResponse(
            UUID id,
            String kind,
            String privateLabel,
            String publicShort,
            String fullAddress,
            double lat,
            double lng,
            boolean primary
    ) {}

    public record UpsertPlaceRequest(
            String kind,
            String privateLabel,
            String publicShort,
            String fullAddress,
            Double lat,
            Double lng,
            Boolean primary
    ) {}
}
