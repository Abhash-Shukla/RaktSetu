package lifeline.core.domain;

/**
 * Geographical latitude and longitude coordinate on Earth.
 *
 * @param latitude  latitude in degrees (-90.0 to 90.0)
 * @param longitude longitude in degrees (-180.0 to 180.0)
 */
public record GeoCoordinate(double latitude, double longitude) {
    public GeoCoordinate {
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude must be in [-90, 90], got: " + latitude);
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude must be in [-180, 180], got: " + longitude);
        }
    }
}
