package com.alnlabs.ridebuddy.common;

public final class GeoUtils {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private GeoUtils() {}

    /** Haversine distance in kilometers. */
    public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    public static boolean withinKm(double lat1, double lng1, double lat2, double lng2, double radiusKm) {
        return distanceKm(lat1, lng1, lat2, lng2) <= radiusKm;
    }

    public static String areaSlug(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        String[] parts = label.split(",");
        String area = parts[0].trim();
        return area.toLowerCase().replaceAll("[^a-z0-9]+", "-");
    }
}
