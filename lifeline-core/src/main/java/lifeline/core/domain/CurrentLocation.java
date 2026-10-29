package lifeline.core.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Timestamped current location reported by a donor.
 *
 * @param zone        current geographical zone ID
 * @param confirmedAt timestamp when this location was reported or confirmed
 */
public record CurrentLocation(
    String zone,
    Instant confirmedAt
) {
    public CurrentLocation {
        Objects.requireNonNull(zone, "zone cannot be null");
        Objects.requireNonNull(confirmedAt, "confirmedAt cannot be null");
    }
}
