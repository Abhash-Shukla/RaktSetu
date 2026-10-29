package lifeline.core.filter;

import lifeline.core.compatibility.CompatibilityTable;
import lifeline.core.config.CooldownConfig;
import lifeline.core.config.EngineConfig;
import lifeline.core.domain.BloodGroup;
import lifeline.core.domain.BloodRequest;
import lifeline.core.domain.DonorSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Hard eligibility filter for donor matching candidate pools.
 * <p>
 * Evaluates candidate donors on 4 hard constraints:
 * <ol>
 *   <li>ABO/Rh compatibility (via {@link CompatibilityTable})</li>
 *   <li>Donor self-reported availability (must be true)</li>
 *   <li>Donor account active (not paused)</li>
 *   <li>Statutory cooldown window (strictly 90 days for men, 120 days for women per NBTC guidance)</li>
 * </ol>
 * <p>
 * Excluded donors are completely excluded from ranking, and every excluded donor carries
 * a machine-readable {@link ExclusionReason}.
 */
public final class CandidateFilter {

    private static final Logger log = LoggerFactory.getLogger(CandidateFilter.class);

    private final CooldownConfig cooldownConfig;

    /**
     * Constructs a CandidateFilter with custom cooldown configuration.
     *
     * @param cooldownConfig cooldown days configuration
     */
    public CandidateFilter(CooldownConfig cooldownConfig) {
        this.cooldownConfig = Objects.requireNonNull(cooldownConfig, "cooldownConfig cannot be null");
    }

    /**
     * Constructs a CandidateFilter from complete engine configuration.
     *
     * @param engineConfig engine configuration
     */
    public CandidateFilter(EngineConfig engineConfig) {
        this(Objects.requireNonNull(engineConfig, "engineConfig cannot be null").cooldown());
    }

    /**
     * Constructs a CandidateFilter with default statutory cooldown configuration (90d male, 120d female).
     */
    public CandidateFilter() {
        this(CooldownConfig.defaults());
    }

    /**
     * Filters candidate donors against an active blood request.
     * Evaluates cooldown relative to the request's creation date.
     *
     * @param donors  candidate donors to filter
     * @param request target blood request
     * @return filter result containing eligible donors and recorded exclusions
     */
    public FilterResult filter(Collection<DonorSnapshot> donors, BloodRequest request) {
        Objects.requireNonNull(request, "request cannot be null");
        LocalDate asOfDate = LocalDate.ofInstant(request.createdAt(), ZoneOffset.UTC);
        return filter(donors, request.bloodGroup(), asOfDate, request.id());
    }

    /**
     * Filters candidate donors against an active blood request with an explicit reference date.
     *
     * @param donors   candidate donors to filter
     * @param request  target blood request
     * @param asOfDate reference date for cooldown calculation
     * @return filter result containing eligible donors and recorded exclusions
     */
    public FilterResult filter(Collection<DonorSnapshot> donors, BloodRequest request, LocalDate asOfDate) {
        Objects.requireNonNull(request, "request cannot be null");
        Objects.requireNonNull(asOfDate, "asOfDate cannot be null");
        return filter(donors, request.bloodGroup(), asOfDate, request.id());
    }

    /**
     * Filters candidate donors for a required blood group and reference date.
     *
     * @param donors        candidate donors to filter
     * @param requiredGroup required recipient blood group
     * @param asOfDate      reference date for cooldown calculation
     * @param requestId     identifier for debug traceability
     * @return filter result containing eligible donors and recorded exclusions
     */
    public FilterResult filter(
        Collection<DonorSnapshot> donors,
        BloodGroup requiredGroup,
        LocalDate asOfDate,
        String requestId
    ) {
        Objects.requireNonNull(donors, "donors cannot be null");
        Objects.requireNonNull(requiredGroup, "requiredGroup cannot be null");
        Objects.requireNonNull(asOfDate, "asOfDate cannot be null");
        String reqId = requestId != null ? requestId : "UNKNOWN";

        log.debug("[request={}] Filtering {} candidate donors for required group {} as of {}",
            reqId, donors.size(), requiredGroup, asOfDate);

        List<DonorSnapshot> eligible = new ArrayList<>();
        List<ExcludedDonor> excluded = new ArrayList<>();

        for (DonorSnapshot donor : donors) {
            ExcludedDonor exclusion = evaluateEligibility(donor, requiredGroup, asOfDate);
            if (exclusion == null) {
                eligible.add(donor);
            } else {
                excluded.add(exclusion);
                log.debug("[request={}] Donor {} excluded: reason={}, detail={}",
                    reqId, donor.id(), exclusion.reason(), exclusion.detail());
            }
        }

        log.debug("[request={}] Filtering complete: {} eligible, {} excluded",
            reqId, eligible.size(), excluded.size());

        return new FilterResult(eligible, excluded);
    }

    private ExcludedDonor evaluateEligibility(DonorSnapshot donor, BloodGroup recipientGroup, LocalDate asOfDate) {
        // 1. Compatibility check
        if (!CompatibilityTable.isCompatible(donor.bloodGroup(), recipientGroup)) {
            return new ExcludedDonor(
                donor,
                ExclusionReason.INCOMPATIBLE_BLOOD_GROUP,
                "Donor group " + donor.bloodGroup() + " is incompatible with recipient group " + recipientGroup
            );
        }

        // 2. Availability check
        if (!donor.availability()) {
            return new ExcludedDonor(
                donor,
                ExclusionReason.UNAVAILABLE,
                "Donor is marked unavailable"
            );
        }

        // 3. Paused check
        if (donor.paused()) {
            return new ExcludedDonor(
                donor,
                ExclusionReason.PAUSED,
                "Donor account is paused"
            );
        }

        // 4. Cooldown check
        if (donor.lastDonationDate() != null) {
            long daysSinceLastDonation = ChronoUnit.DAYS.between(donor.lastDonationDate(), asOfDate);
            int requiredDays = cooldownConfig.cooldownDaysFor(donor.sex());
            if (daysSinceLastDonation < requiredDays) {
                return new ExcludedDonor(
                    donor,
                    ExclusionReason.IN_COOLDOWN,
                    String.format(
                        "Donated %d days ago (%s); required cooldown for %s is %d days (available in %d days)",
                        daysSinceLastDonation,
                        donor.lastDonationDate(),
                        donor.sex(),
                        requiredDays,
                        requiredDays - daysSinceLastDonation
                    )
                );
            }
        }

        return null;
    }
}
