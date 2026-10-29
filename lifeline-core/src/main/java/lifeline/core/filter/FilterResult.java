package lifeline.core.filter;

import lifeline.core.domain.DonorSnapshot;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of executing the {@link CandidateFilter} on a donor candidate pool.
 *
 * @param eligibleDonors unmodifiable list of eligible donors ready for ranking
 * @param excludedDonors unmodifiable list of excluded donors with their exclusion reasons
 */
public record FilterResult(
    List<DonorSnapshot> eligibleDonors,
    List<ExcludedDonor> excludedDonors
) {
    public FilterResult {
        Objects.requireNonNull(eligibleDonors, "eligibleDonors cannot be null");
        Objects.requireNonNull(excludedDonors, "excludedDonors cannot be null");
        eligibleDonors = Collections.unmodifiableList(List.copyOf(eligibleDonors));
        excludedDonors = Collections.unmodifiableList(List.copyOf(excludedDonors));
    }

    /**
     * Checks if a donor with the given ID was deemed eligible.
     */
    public boolean isEligible(String donorId) {
        return eligibleDonors.stream().anyMatch(d -> d.id().equals(donorId));
    }

    /**
     * Finds the exclusion reason for the given donor ID, if excluded.
     */
    public Optional<ExcludedDonor> findExclusion(String donorId) {
        return excludedDonors.stream().filter(e -> e.donor().id().equals(donorId)).findFirst();
    }
}
