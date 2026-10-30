package lifeline.eval.baseline;

import lifeline.core.domain.BloodRequest;
import lifeline.core.domain.DonorSnapshot;
import lifeline.core.domain.ZoneMap;
import lifeline.sim.engine.Matcher;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * System S3: Rule-based selection.
 * Combines observable distance, availability, and recency of last donation.
 * Strictly prohibited from accessing or using donor outreach history (historyStats).
 */
public final class S3RuleBasedMatcher implements Matcher {

    private final ZoneMap zoneMap;
    private final double referenceDistanceKm;

    public S3RuleBasedMatcher(ZoneMap zoneMap, double referenceDistanceKm) {
        this.zoneMap = Objects.requireNonNull(zoneMap, "zoneMap cannot be null");
        this.referenceDistanceKm = referenceDistanceKm > 0.0 ? referenceDistanceKm : 10.0;
    }

    public S3RuleBasedMatcher(ZoneMap zoneMap) {
        this(zoneMap, 10.0);
    }

    @Override
    public String name() {
        return "S3_RULE_BASED";
    }

    @Override
    public Optional<String> selectNextCandidate(
        BloodRequest request,
        List<DonorSnapshot> eligiblePool,
        Set<String> alreadyContacted
    ) {
        return selectNextCandidate(request, eligiblePool, alreadyContacted, request.createdAt());
    }

    @Override
    public Optional<String> selectNextCandidate(
        BloodRequest request,
        List<DonorSnapshot> eligiblePool,
        Set<String> alreadyContacted,
        Instant currentTime
    ) {
        LocalDate currentDate = currentTime.atZone(ZoneOffset.UTC).toLocalDate();

        return eligiblePool.stream()
            .filter(d -> !alreadyContacted.contains(d.id()))
            .max(Comparator
                .comparingDouble((DonorSnapshot d) -> computeRuleScore(d, request, currentDate))
                .thenComparing(DonorSnapshot::id, Comparator.reverseOrder())
            )
            .map(DonorSnapshot::id);
    }

    /**
     * Computes the rule score without inspecting historyStats.
     */
    public double computeRuleScore(DonorSnapshot donor, BloodRequest request, LocalDate currentDate) {
        // 1. Distance term (exponential decay)
        double dist = zoneMap.distanceKm(donor.homeZone(), request.zone());
        double sDist = Math.pow(2.0, -dist / referenceDistanceKm);

        // 2. Recency of last donation (longer since last donation -> higher readiness)
        double sRecency;
        if (donor.lastDonationDate() == null) {
            sRecency = 1.0;
        } else {
            long days = ChronoUnit.DAYS.between(donor.lastDonationDate(), currentDate);
            sRecency = Math.min(1.0, Math.max(0.0, days / 365.0));
        }

        // 3. Availability
        double sAvail = donor.availability() ? 1.0 : 0.0;

        // Weighted rule combination: NO historyStats used
        return 0.55 * sDist + 0.30 * sRecency + 0.15 * sAvail;
    }
}
