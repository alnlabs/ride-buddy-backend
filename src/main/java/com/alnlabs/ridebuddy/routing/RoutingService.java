package com.alnlabs.ridebuddy.routing;

import com.alnlabs.ridebuddy.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Live-traffic driving routes via Google Routes API (preferred), OSRM fallback.
 * Returns up to 3 alternatives sorted by ETA (fastest first).
 */
@Service
public class RoutingService {

    private static final Logger log = LoggerFactory.getLogger(RoutingService.class);
    private static final String GOOGLE_ROUTES_URL =
            "https://routes.googleapis.com/directions/v2:computeRoutes";
    private static final String OSRM_BASE = "https://router.project-osrm.org";
    private static final int MAX_VIA_PLACES = 2;
    private static final Pattern ONTO_LANDMARK = Pattern.compile(
            "(?i)\\b(?:onto|on|via|toward|towards)\\s+([A-Za-z0-9][A-Za-z0-9 .'/\\-]{2,48})"
    );
    /** Main / popular corridors only — not every “… Road”. */
    private static final Pattern MAJOR_CORRIDOR = Pattern.compile(
            "(?i)\\b("
                    + "expressway|freeway|highway|hwy\\.?"
                    + "|outer\\s*ring|inner\\s*ring|ring\\s*road|\\borr\\b|\\birr\\b"
                    + "|nh\\s*-?\\d+|sh\\s*-?\\d+|national\\s*highway|state\\s*highway"
                    + "|flyover|overbridge|bypass|elevated"
                    + "|pvnr|nehru\\s*outer|tank\\s*bund|necklace\\s*road"
                    + "|boulevard|blvd\\.?|corridor|ghat"
                    + ")\\b"
    );
    /** Gully / internal roads — never show in via. */
    private static final Pattern MINOR_REJECT = Pattern.compile(
            "(?i)\\b("
                    + "lane|gully|gali|bylane|by-?lane|alley|path|pathway"
                    + "|colony|society|layout|enclave|apartment|apartments"
                    + "|nagar|phase\\s*\\d+|sector\\s*\\d+"
                    + "|cross|x-?roads?|street|st\\.?|cul-?de-?sac"
                    + "|service\\s*road|internal|village|ward|slum"
                    + ")\\b"
    );

    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .build();
    private final String googleMapsApiKey;

    public RoutingService(
            ObjectMapper objectMapper,
            @Value("${app.google-maps-api-key:}") String googleMapsApiKey
    ) {
        this.objectMapper = objectMapper;
        this.googleMapsApiKey = googleMapsApiKey == null ? "" : googleMapsApiKey.trim();
    }

    public RoutesResponse routes(double fromLat, double fromLng, double toLat, double toLng) {
        if (!isLatLng(fromLat, fromLng) || !isLatLng(toLat, toLng)) {
            throw ApiException.badRequest("Invalid from/to coordinates");
        }
        if (googleMapsApiKey.isEmpty()
                || "your_google_maps_key".equalsIgnoreCase(googleMapsApiKey)) {
            log.debug("No Google Maps key — using OSRM");
            return new RoutesResponse(osrmRoutes(fromLat, fromLng, toLat, toLng), "osrm");
        }
        List<DriveRouteDto> live = googleRoutes(fromLat, fromLng, toLat, toLng);
        if (!live.isEmpty()) {
            return new RoutesResponse(live, "google_traffic");
        }
        log.warn("Google Routes empty/failed — falling back to OSRM");
        return new RoutesResponse(osrmRoutes(fromLat, fromLng, toLat, toLng), "osrm");
    }

    private List<DriveRouteDto> googleRoutes(double fromLat, double fromLng, double toLat, double toLng) {
        try {
            // Omit departureTime — Google rejects "now" as past ("must be future").
            // Without it, TRAFFIC_AWARE_OPTIMAL uses current traffic conditions.
            Map<String, Object> body = Map.of(
                    "origin", Map.of(
                            "location", Map.of(
                                    "latLng", Map.of("latitude", fromLat, "longitude", fromLng)
                            )
                    ),
                    "destination", Map.of(
                            "location", Map.of(
                                    "latLng", Map.of("latitude", toLat, "longitude", toLng)
                            )
                    ),
                    "travelMode", "DRIVE",
                    "routingPreference", "TRAFFIC_AWARE_OPTIMAL",
                    "trafficModel", "BEST_GUESS",
                    "computeAlternativeRoutes", true,
                    "languageCode", "en-IN",
                    "regionCode", "IN",
                    "units", "METRIC"
            );
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GOOGLE_ROUTES_URL))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("X-Goog-Api-Key", googleMapsApiKey)
                    .header(
                            "X-Goog-FieldMask",
                            "routes.duration,routes.staticDuration,routes.distanceMeters,"
                                    + "routes.polyline.encodedPolyline,routes.routeLabels,"
                                    + "routes.description,"
                                    + "routes.legs.steps.navigationInstruction"
                    )
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                log.warn("Google Routes HTTP {}: {}", res.statusCode(), truncate(res.body()));
                return List.of();
            }
            JsonNode root = objectMapper.readTree(res.body());
            JsonNode routesNode = root.path("routes");
            if (!routesNode.isArray() || routesNode.isEmpty()) {
                return List.of();
            }
            List<DriveRouteDto> out = new ArrayList<>();
            for (JsonNode raw : routesNode) {
                String encoded = raw.path("polyline").path("encodedPolyline").asText(null);
                if (encoded == null || encoded.isBlank()) continue;
                List<List<Double>> points = decodePolyline(encoded);
                if (points.size() < 2) continue;
                double durationSec = parseDurationSeconds(raw.path("duration").asText(null));
                double staticSec = parseDurationSeconds(raw.path("staticDuration").asText(null));
                double delay = (durationSec > 0 && staticSec > 0 && durationSec > staticSec)
                        ? durationSec - staticSec
                        : 0;
                String via = buildViaLabel(raw, points);
                out.add(new DriveRouteDto(
                        points,
                        raw.path("distanceMeters").asDouble(0),
                        durationSec > 0 ? durationSec : staticSec,
                        delay,
                        true,
                        0,
                        via
                ));
            }
            return topThreeByTime(out);
        } catch (Exception e) {
            log.warn("Google Routes failed: {}", e.toString());
            return List.of();
        }
    }

    private List<DriveRouteDto> osrmRoutes(double fromLat, double fromLng, double toLat, double toLng) {
        try {
            String path = String.format(
                    "%s/route/v1/driving/%s,%s;%s,%s?overview=full&geometries=geojson&alternatives=true&steps=false",
                    OSRM_BASE,
                    enc(fromLng), enc(fromLat), enc(toLng), enc(toLat)
            );
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(path))
                    .timeout(Duration.ofSeconds(18))
                    .header("User-Agent", "RideBuddy/1.0 (com.alnlabs.ridebuddy)")
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                log.warn("OSRM HTTP {}: {}", res.statusCode(), truncate(res.body()));
                return List.of();
            }
            JsonNode root = objectMapper.readTree(res.body());
            if (!"Ok".equals(root.path("code").asText())) {
                return List.of();
            }
            List<DriveRouteDto> out = new ArrayList<>();
            for (JsonNode raw : root.path("routes")) {
                List<List<Double>> points = new ArrayList<>();
                for (JsonNode c : raw.path("geometry").path("coordinates")) {
                    if (c.isArray() && c.size() >= 2) {
                        points.add(List.of(c.get(1).asDouble(), c.get(0).asDouble()));
                    }
                }
                if (points.size() < 2) continue;
                out.add(new DriveRouteDto(
                        points,
                        raw.path("distance").asDouble(0),
                        raw.path("duration").asDouble(0),
                        0,
                        false,
                        0,
                        null
                ));
            }
            return topThreeByTime(out);
        } catch (Exception e) {
            log.warn("OSRM failed: {}", e.toString());
            return List.of();
        }
    }

    private static List<DriveRouteDto> topThreeByTime(List<DriveRouteDto> routes) {
        List<DriveRouteDto> sorted = routes.stream()
                .sorted(Comparator.comparingDouble(DriveRouteDto::durationSeconds))
                .limit(3)
                .toList();
        List<DriveRouteDto> indexed = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            DriveRouteDto r = sorted.get(i);
            indexed.add(new DriveRouteDto(
                    r.points(),
                    r.distanceMeters(),
                    r.durationSeconds(),
                    r.trafficDelaySeconds(),
                    r.usesLiveTraffic(),
                    i,
                    r.viaLabel()
            ));
        }
        return indexed;
    }

    static double parseDurationSeconds(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        var m = java.util.regex.Pattern.compile("^(\\d+(?:\\.\\d+)?)s$").matcher(raw.trim());
        if (m.matches()) {
            return Double.parseDouble(m.group(1));
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Google encoded polyline → [[lat, lng], ...] */
    static List<List<Double>> decodePolyline(String encoded) {
        List<List<Double>> points = new ArrayList<>();
        int index = 0;
        int lat = 0;
        int lng = 0;
        while (index < encoded.length()) {
            int shift = 0;
            int result = 0;
            int b;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            lat += ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            lng += ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);

            points.add(List.of(lat / 1e5, lng / 1e5));
        }
        return points;
    }

    private static boolean isLatLng(double lat, double lng) {
        return lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
    }

    private static String enc(double v) {
        return URLEncoder.encode(Double.toString(v), StandardCharsets.UTF_8);
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= 240 ? s : s.substring(0, 240) + "…";
    }

    /**
     * Via = main / popular corridors only (expressway, ring, NH, flyover…).
     * Drops gully / colony / lane roads.
     */
    private String buildViaLabel(JsonNode route, List<List<Double>> points) {
        LinkedHashSet<String> places = new LinkedHashSet<>();
        // Google's route summary — only if it's a main corridor (not a local road).
        String primary = normalizeVia(route.path("description").asText(null));
        if (primary != null && isMajorCorridor(primary)) {
            places.add(primary);
        }

        // From steps: only major corridors, prefer ones mentioned more than once.
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (JsonNode leg : route.path("legs")) {
            for (JsonNode step : leg.path("steps")) {
                String instr = step.path("navigationInstruction").path("instructions").asText(null);
                for (String name : landmarksFromInstruction(instr)) {
                    counts.merge(name, 1, Integer::sum);
                }
            }
        }
        counts.entrySet().stream()
                .filter(e -> isMajorCorridor(e.getKey()))
                .filter(e -> e.getValue() >= 2 || isHighProfileCorridor(e.getKey()))
                .sorted((a, b) -> {
                    int byCount = Integer.compare(b.getValue(), a.getValue());
                    if (byCount != 0) return byCount;
                    return Integer.compare(majorScore(b.getKey()), majorScore(a.getKey()));
                })
                .map(Map.Entry::getKey)
                .forEach(name -> addLandmark(places, name));

        return joinVia(places);
    }

    static List<String> landmarksFromInstruction(String instruction) {
        if (instruction == null || instruction.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        Matcher m = ONTO_LANDMARK.matcher(instruction);
        while (m.find()) {
            String token = cleanPlaceToken(m.group(1));
            if (token != null && isMajorCorridor(token)) {
                out.add(token);
            }
        }
        return out;
    }

    static void addLandmark(Set<String> places, String candidate) {
        if (candidate == null || !isMajorCorridor(candidate)) return;
        String s = cleanPlaceToken(candidate);
        if (s == null || !isMajorCorridor(s)) return;
        for (String existing : places) {
            if (existing.equalsIgnoreCase(s)
                    || existing.toLowerCase(Locale.ROOT).contains(s.toLowerCase(Locale.ROOT))
                    || s.toLowerCase(Locale.ROOT).contains(existing.toLowerCase(Locale.ROOT))) {
                return;
            }
        }
        if (places.size() < MAX_VIA_PLACES) {
            places.add(s);
        }
    }

    static boolean isMajorCorridor(String raw) {
        if (raw == null || raw.isBlank()) return false;
        String s = raw.trim();
        if (s.length() < 4 || isMinorRoad(s)) return false;
        return MAJOR_CORRIDOR.matcher(s).find();
    }

    static boolean isHighProfileCorridor(String raw) {
        if (raw == null) return false;
        String lower = raw.toLowerCase(Locale.ROOT);
        return lower.contains("expressway")
                || lower.contains("ring")
                || lower.contains("orr")
                || lower.contains("highway")
                || lower.contains("flyover")
                || lower.contains("bypass")
                || lower.contains("pvnr")
                || lower.matches(".*\\bnh\\s*-?\\d+.*")
                || lower.matches(".*\\bsh\\s*-?\\d+.*");
    }

    static boolean isMinorRoad(String raw) {
        return raw != null && MINOR_REJECT.matcher(raw).find();
    }

    static int majorScore(String raw) {
        if (raw == null) return 0;
        String lower = raw.toLowerCase(Locale.ROOT);
        int score = raw.length();
        if (lower.contains("expressway")) score += 40;
        if (lower.contains("ring") || lower.contains("orr")) score += 35;
        if (lower.contains("highway") || lower.matches(".*\\bnh\\s*-?\\d+.*")) score += 30;
        if (lower.contains("flyover") || lower.contains("bypass")) score += 20;
        if (lower.contains("pvnr") || lower.contains("tank bund") || lower.contains("necklace")) score += 25;
        return score;
    }

    static String cleanPlaceToken(String raw) {
        if (raw == null) return null;
        String s = raw.trim()
                .replaceAll("(?i)\\s+(and|&)\\s+.*$", "")
                .replaceAll("[,.].*$", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (s.regionMatches(true, 0, "via ", 0, 4)) {
            s = s.substring(4).trim();
        }
        if (s.length() < 3 || s.length() > 48) return null;
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.equals("road") || lower.equals("street") || lower.equals("the")
                || lower.equals("left") || lower.equals("right")
                || lower.equals("ramp") || lower.equals("exit")
                || lower.equals("lane") || lower.equals("path")) {
            return null;
        }
        return s;
    }

    static String joinVia(Set<String> places) {
        if (places == null || places.isEmpty()) return null;
        List<String> list = new ArrayList<>(places);
        if (list.size() > MAX_VIA_PLACES) {
            list = list.subList(0, MAX_VIA_PLACES);
        }
        return String.join(", ", list);
    }

    /** Google descriptions are often "via Outer Ring Road" — keep a clean via label. */
    static String normalizeVia(String raw) {
        String cleaned = cleanPlaceToken(raw);
        if (cleaned == null || isMinorRoad(cleaned)) return null;
        // Accept Google primary even if wording is slightly unusual, as long as not minor.
        return cleaned;
    }

    public record DriveRouteDto(
            List<List<Double>> points,
            double distanceMeters,
            double durationSeconds,
            double trafficDelaySeconds,
            boolean usesLiveTraffic,
            int index,
            String viaLabel
    ) {}

    public record RoutesResponse(List<DriveRouteDto> routes, String source) {}
}
