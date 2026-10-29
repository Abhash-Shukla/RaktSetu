package lifeline.core.domain;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Geographical zone topology providing road-distance estimation.
 * Uses haversine great-circle distance scaled by a configurable road multiplier
 * (default 1.3 per PARAMETERS.md P-04).
 *
 * @param centroids  map from zone identifier to centroid coordinate
 * @param roadFactor road distance multiplier (&gt;= 1.0)
 */
public record ZoneMap(
    Map<String, GeoCoordinate> centroids,
    double roadFactor
) {
    private static final double EARTH_RADIUS_KM = 6371.0;

    public ZoneMap {
        Objects.requireNonNull(centroids, "centroids cannot be null");
        if (roadFactor < 1.0) {
            throw new IllegalArgumentException("roadFactor must be at least 1.0, got: " + roadFactor);
        }
        centroids = Collections.unmodifiableMap(new HashMap<>(centroids));
    }

    /**
     * Computes the estimated road travel distance in kilometers between two zones.
     * If fromZone and toZone are identical, returns 0.0.
     *
     * @param fromZone origin zone ID
     * @param toZone   destination zone ID
     * @return estimated road distance in kilometers
     * @throws IllegalArgumentException if either zone is not registered in the map
     */
    public double distanceKm(String fromZone, String toZone) {
        Objects.requireNonNull(fromZone, "fromZone cannot be null");
        Objects.requireNonNull(toZone, "toZone cannot be null");

        if (fromZone.equals(toZone)) {
            return 0.0;
        }

        GeoCoordinate c1 = centroids.get(fromZone);
        if (c1 == null) {
            throw new IllegalArgumentException("Unknown zone ID: " + fromZone);
        }
        GeoCoordinate c2 = centroids.get(toZone);
        if (c2 == null) {
            throw new IllegalArgumentException("Unknown zone ID: " + toZone);
        }

        double greatCircleKm = haversine(c1.latitude(), c1.longitude(), c2.latitude(), c2.longitude());
        return greatCircleKm * roadFactor;
    }

    /**
     * Checks if a zone exists in this map.
     */
    public boolean containsZone(String zone) {
        return zone != null && centroids.containsKey(zone);
    }

    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double rLat1 = Math.toRadians(lat1);
        double rLat2 = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0)
                + Math.cos(rLat1) * Math.cos(rLat2) * Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return EARTH_RADIUS_KM * c;
    }
}
