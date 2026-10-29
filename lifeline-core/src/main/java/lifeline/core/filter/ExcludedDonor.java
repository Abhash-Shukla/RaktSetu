package lifeline.core.filter;

import lifeline.core.domain.DonorSnapshot;

import java.util.Objects;

/**
 * Record of an excluded candidate donor with a machine-readable reason and descriptive detail.
 *
 * @param donor  the excluded donor snapshot
 * @param reason machine-readable exclusion reason
 * @param detail plain-text explanation of the exclusion condition
 */
public record ExcludedDonor(
    DonorSnapshot donor,
    ExclusionReason reason,
    String detail
) {
    public ExcludedDonor {
        Objects.requireNonNull(donor, "donor cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        Objects.requireNonNull(detail, "detail cannot be null");
    }
}
