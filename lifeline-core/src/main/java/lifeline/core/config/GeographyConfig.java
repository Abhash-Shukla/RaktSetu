package lifeline.core.config;

/**
 * Geographical routing configuration.
 *
 * @param roadFactor             road distance multiplier over great-circle haversine (default 1.3 per P-04)
 * @param locationFreshnessHours maximum age in hours for a reported current location to be trusted (default 24h per P-13)
 */
public record GeographyConfig(
    double roadFactor,
    int locationFreshnessHours
) {
    public static final double DEFAULT_ROAD_FACTOR = 1.3;
    public static final int DEFAULT_FRESHNESS_HOURS = 24;

    public GeographyConfig {
        if (roadFactor < 1.0) {
            throw new IllegalArgumentException("roadFactor must be at least 1.0, got: " + roadFactor);
        }
        if (locationFreshnessHours < 0) {
            throw new IllegalArgumentException("locationFreshnessHours cannot be negative, got: " + locationFreshnessHours);
        }
    }

    public static GeographyConfig defaults() {
        return new GeographyConfig(DEFAULT_ROAD_FACTOR, DEFAULT_FRESHNESS_HOURS);
    }
}
