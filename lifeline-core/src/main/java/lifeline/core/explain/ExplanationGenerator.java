package lifeline.core.explain;

import lifeline.core.domain.BloodRequest;
import lifeline.core.domain.DonorSnapshot;
import lifeline.core.factor.FactorContribution;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic template-based generator producing plain-language explanations
 * for ranked donor matches.
 * <p>
 * Under SRS EXP-2:
 * "A plain-language explanation is generated from the breakdown by template logic (not by an LLM)."
 */
public final class ExplanationGenerator {

    private ExplanationGenerator() {
    }

    /**
     * Generates a faithful explanation from the factor breakdown and flags.
     *
     * @param donor         candidate donor snapshot
     * @param request       active blood request
     * @param contributions factor breakdown list
     * @return structured explanation containing plain-language text and flags
     */
    public static Explanation generate(
        DonorSnapshot donor,
        BloodRequest request,
        List<FactorContribution> contributions
    ) {
        Objects.requireNonNull(donor, "donor cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        Objects.requireNonNull(contributions, "contributions cannot be null");

        boolean staleLocation = contributions.stream()
            .anyMatch(c -> "STALE_LOCATION".equals(c.reasonCode()));

        boolean rareTypeConserved = contributions.stream()
            .anyMatch(c -> "RARE_TYPE_CONSERVED".equals(c.reasonCode()));

        boolean exactMatch = donor.bloodGroup() == request.bloodGroup();

        // Identify the top contributing factor
        FactorContribution topFactor = contributions.stream()
            .max(Comparator.comparingDouble(FactorContribution::contribution))
            .orElse(null);

        String topFactorName = topFactor != null ? topFactor.factorName() : "NONE";

        StringBuilder sb = new StringBuilder();

        // Mention top contributing factor
        if (topFactor != null) {
            sb.append(String.format("Top factor: %s (contribution: %+.2f). ", topFactorName, topFactor.contribution()));
        }

        // Blood group compatibility context
        if (exactMatch) {
            sb.append("Exact blood group match (").append(donor.bloodGroup()).append("). ");
        } else if (rareTypeConserved) {
            sb.append("Rare blood type (").append(donor.bloodGroup())
              .append(") conserved: non-exact substitution penalty applied. ");
        } else {
            sb.append("Compatible blood group (").append(donor.bloodGroup())
              .append(" -> ").append(request.bloodGroup()).append("). ");
        }

        // Location context
        if (staleLocation) {
            sb.append("Location is stale: evaluated using registered home zone (")
              .append(donor.homeZone()).append(").");
        } else {
            sb.append("Location confirmed fresh and verified.");
        }

        return new Explanation(
            sb.toString().trim(),
            topFactorName,
            staleLocation,
            rareTypeConserved
        );
    }
}
