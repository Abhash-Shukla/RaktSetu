package lifeline.core.factor;

import lifeline.core.domain.BloodRequest;
import lifeline.core.domain.DonorSnapshot;

/**
 * Soft rarity conservation factor.
 * <p>
 * Penalizes routing a rare-type donor to a recipient whose requirement could be served
 * by a more common compatible blood group.
 * <p>
 * The penalty applies ONLY when the candidate's blood group is NOT the exact group requested.
 * When the donor's blood group is the exact group requested, no penalty is applied.
 */
public final class RarityFactor implements ScoreFactor {

    public static final String NAME = "RARITY";
    public static final String REASON_EXACT_MATCH = "EXACT_GROUP";
    public static final String REASON_RARE_TYPE_CONSERVED = "RARE_TYPE_CONSERVED";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public FactorContribution score(DonorSnapshot d, BloodRequest r, Context ctx) {
        double weight = ctx.weightProfile().rarity();

        if (d.bloodGroup() == r.bloodGroup()) {
            // Exact match: no conservation penalty applied
            return FactorContribution.of(NAME, 0.0, weight, REASON_EXACT_MATCH);
        }

        // Non-exact compatible match: apply rarity penalty proportional to donor group scarcity
        double rarityScore = ctx.populationFrequencies().rarityScoreOf(d.bloodGroup());
        double rawValue = -rarityScore;

        return FactorContribution.of(NAME, rawValue, weight, REASON_RARE_TYPE_CONSERVED);
    }
}
