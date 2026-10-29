package lifeline.core.factor;

import lifeline.core.domain.BloodRequest;
import lifeline.core.domain.DonorSnapshot;
import lifeline.core.domain.HistoryStats;

/**
 * Optional responsiveness factor computing smoothed acceptance probability from historical events.
 * <p>
 * Uses a Beta(alpha, beta) conjugate prior (default Beta(1, 2) per PARAMETERS.md P-23).
 * Disabled by default in the locked S6 engine specification (weight = 0.0).
 */
public final class ResponsivenessFactor implements ScoreFactor {

    public static final String NAME = "RESPONSIVENESS";
    public static final String REASON_PRIOR_ESTIMATE = "PRIOR_ESTIMATE";
    public static final String REASON_SMOOTHED_RATE = "SMOOTHED_HISTORY_RATE";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public FactorContribution score(DonorSnapshot d, BloodRequest r, Context ctx) {
        HistoryStats history = d.historyStats();
        int contacts = history.contacts();
        int accepts = history.accepts();

        double alpha = ctx.betaPriorAlpha();
        double beta = ctx.betaPriorBeta();
        double rawValue = (accepts + alpha) / (contacts + alpha + beta);
        double weight = ctx.weightProfile().responsiveness();
        String reasonCode = contacts == 0 ? REASON_PRIOR_ESTIMATE : REASON_SMOOTHED_RATE;

        return FactorContribution.of(NAME, rawValue, weight, reasonCode);
    }
}
