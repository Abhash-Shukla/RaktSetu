package lifeline.core.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable blood requirement submitted to the LifeLine system.
 *
 * @param id         unique request identifier
 * @param bloodGroup required ABO/Rh blood group
 * @param units      number of whole blood units requested (must be &gt;= 1)
 * @param urgency    urgency tier selecting the matching weight profile
 * @param zone       geographical zone where blood is required
 * @param createdAt  timestamp when the request became active
 * @param deadline   deadline by which units must be fulfilled
 */
public record BloodRequest(
    String id,
    BloodGroup bloodGroup,
    int units,
    Urgency urgency,
    String zone,
    Instant createdAt,
    Instant deadline
) {
    public BloodRequest {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(bloodGroup, "bloodGroup cannot be null");
        if (units <= 0) {
            throw new IllegalArgumentException("units must be greater than 0, got " + units);
        }
        Objects.requireNonNull(urgency, "urgency cannot be null");
        Objects.requireNonNull(zone, "zone cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        Objects.requireNonNull(deadline, "deadline cannot be null");
        if (deadline.isBefore(createdAt)) {
            throw new IllegalArgumentException("deadline cannot be before createdAt");
        }
    }
}
