package lifeline.core.factor;

import lifeline.core.domain.BloodRequest;
import lifeline.core.domain.DonorSnapshot;

/**
 * Pluggable scoring factor for candidate donor ranking.
 * Each factor computes an unweighted raw value, looks up its urgency weight,
 * and produces a traceable {@link FactorContribution}.
 */
public interface ScoreFactor {

    /**
     * Unique identifier for this factor (e.g. "PROXIMITY", "RARITY", "RESPONSIVENESS").
     */
    String name();

    /**
     * Evaluates a candidate donor against an active blood request within a scoring context.
     *
     * @param d   candidate donor snapshot
     * @param r   active blood request
     * @param ctx scoring context (topology, freshness, population distribution, weights)
     * @return traceable contribution of this factor
     */
    FactorContribution score(DonorSnapshot d, BloodRequest r, Context ctx);
}
