package lifeline.eval.study.explanation;

import lifeline.core.domain.BloodRequest;
import lifeline.core.domain.DonorSnapshot;
import lifeline.core.explain.Explanation;
import lifeline.core.factor.FactorContribution;
import lifeline.core.factor.ProximityFactor;
import lifeline.core.factor.RarityFactor;
import lifeline.core.factor.ResponsivenessFactor;

import java.util.*;

/**
 * Authoritative catalog and validator for all reason codes emitted by the LifeLine matching engine.
 * <p>
 * Under Claim C5 and SRS EXP-1 through EXP-4:
 * Every engine factor emits structured reason codes that must be faithfully represented
 * in the plain-language explanation and associated audit flags.
 */
public final class ReasonCodeCatalog {

    public record ReasonCodeSpec(
        String code,
        String factorName,
        String description,
        boolean triggersStaleLocationFlag,
        boolean triggersRareTypeConservedFlag,
        String expectedSummaryKeyword
    ) {}

    private static final Map<String, ReasonCodeSpec> CATALOG = new LinkedHashMap<>();

    static {
        register(new ReasonCodeSpec(
            ProximityFactor.REASON_CURRENT,
            ProximityFactor.NAME,
            "Fresh GPS/cellular coordinates confirmed within the freshness window",
            false,
            false,
            "confirmed fresh"
        ));
        register(new ReasonCodeSpec(
            ProximityFactor.REASON_HOME,
            ProximityFactor.NAME,
            "No current coordinates provided; registered home zone evaluated",
            false,
            false,
            "Location confirmed"
        ));
        register(new ReasonCodeSpec(
            ProximityFactor.REASON_STALE_LOCATION,
            ProximityFactor.NAME,
            "Current location older than freshness window; fallen back to registered home zone",
            true,
            false,
            "Location is stale"
        ));
        register(new ReasonCodeSpec(
            RarityFactor.REASON_EXACT_MATCH,
            RarityFactor.NAME,
            "Candidate blood group identically matches requested blood group (zero penalty)",
            false,
            false,
            "Exact blood group match"
        ));
        register(new ReasonCodeSpec(
            RarityFactor.REASON_RARE_TYPE_CONSERVED,
            RarityFactor.NAME,
            "Candidate is a rare compatible non-exact type; soft conservation penalty applied",
            false,
            true,
            "conserved: non-exact substitution penalty applied"
        ));
        register(new ReasonCodeSpec(
            ResponsivenessFactor.REASON_PRIOR_ESTIMATE,
            ResponsivenessFactor.NAME,
            "Candidate has zero recorded historical contacts; smoothed prior Beta(alpha, beta) used",
            false,
            false,
            "Top factor: RESPONSIVENESS"
        ));
        register(new ReasonCodeSpec(
            ResponsivenessFactor.REASON_SMOOTHED_RATE,
            ResponsivenessFactor.NAME,
            "Candidate has recorded historical contacts; Bayesian smoothed acceptance rate used",
            false,
            false,
            "Top factor: RESPONSIVENESS"
        ));
    }

    private static void register(ReasonCodeSpec spec) {
        CATALOG.put(spec.code(), spec);
    }

    private ReasonCodeCatalog() {}

    /**
     * Returns the unmodifiable view of all known reason code specifications.
     */
    public static Map<String, ReasonCodeSpec> allKnownCodes() {
        return Collections.unmodifiableMap(CATALOG);
    }

    /**
     * Checks if a code is recognized by the engine catalog.
     */
    public static boolean isKnown(String code) {
        return CATALOG.containsKey(code);
    }

    /**
     * Retrieves the specification for a known code.
     */
    public static Optional<ReasonCodeSpec> specFor(String code) {
        return Optional.ofNullable(CATALOG.get(code));
    }

    /**
     * Verifies that the Explanation faithfully reflects the reason codes present in the factor contributions.
     *
     * @param donor candidate donor snapshot
     * @param request blood request
     * @param contributions factor contributions
     * @param explanation generated explanation
     * @return list of verification error messages (empty if valid)
     */
    public static List<String> verifyExplanationFaithfulness(
        DonorSnapshot donor,
        BloodRequest request,
        List<FactorContribution> contributions,
        Explanation explanation
    ) {
        List<String> errors = new ArrayList<>();

        boolean hasStaleLocation = contributions.stream()
            .anyMatch(c -> ProximityFactor.REASON_STALE_LOCATION.equals(c.reasonCode()));
        boolean hasRareConserved = contributions.stream()
            .anyMatch(c -> RarityFactor.REASON_RARE_TYPE_CONSERVED.equals(c.reasonCode()));

        // 1. Verify audit flags match reason codes
        if (explanation.staleLocationFlag() != hasStaleLocation) {
            errors.add(String.format("Mismatch for donor %s: staleLocationFlag is %s but STALE_LOCATION reason code presence is %s",
                donor.id(), explanation.staleLocationFlag(), hasStaleLocation));
        }

        if (explanation.rareTypeConservedFlag() != hasRareConserved) {
            errors.add(String.format("Mismatch for donor %s: rareTypeConservedFlag is %s but RARE_TYPE_CONSERVED reason code presence is %s",
                donor.id(), explanation.rareTypeConservedFlag(), hasRareConserved));
        }

        // 2. Verify summary text semantics
        String summary = explanation.summary();
        if (hasStaleLocation && !summary.contains("stale")) {
            errors.add("Explanation summary missing 'stale' keyword when STALE_LOCATION reason code present");
        }
        if (hasRareConserved && !summary.contains("conserved")) {
            errors.add("Explanation summary missing 'conserved' keyword when RARE_TYPE_CONSERVED reason code present");
        }
        if (donor.bloodGroup() == request.bloodGroup() && !summary.contains("Exact blood group match")) {
            errors.add("Explanation summary missing 'Exact blood group match' for identical blood group");
        }

        // 3. Verify top factor name and contribution in explanation
        FactorContribution maxContrib = contributions.stream()
            .max(Comparator.comparingDouble(FactorContribution::contribution))
            .orElse(null);
        String expectedTop = maxContrib != null ? maxContrib.factorName() : "NONE";
        if (!explanation.topContributingFactor().equals(expectedTop)) {
            errors.add(String.format("Explanation top factor '%s' does not match max contribution factor '%s'",
                explanation.topContributingFactor(), expectedTop));
        }

        return errors;
    }
}

