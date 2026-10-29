package lifeline.sim.oracle;

import lifeline.core.domain.BloodRequest;

/**
 * Upper-bound oracle interface exposing the true simulated acceptance probability.
 * <p>
 * Under AGENTS.md Section 3:
 * "The engine sees observable donor fields only.
 * Latent traits and true response probabilities live in sim and are visible only to eval
 * (for ground truth and the oracle baseline)."
 */
public interface Oracle {

    /**
     * Queries the ground-truth acceptance probability for a donor facing a request.
     * Accessible only by the evaluation module (S8 baseline and ranking regret).
     */
    double trueAcceptanceProbability(
        String donorId,
        BloodRequest request,
        double distanceKm,
        int contactsLast30Days,
        double timeOfDayHour
    );

    /**
     * Queries the ground-truth acceptance probability wrapped in {@link TrueProbability}.
     */
    default TrueProbability queryProbability(
        String donorId,
        BloodRequest request,
        double distanceKm,
        int contactsLast30Days,
        double timeOfDayHour
    ) {
        return new TrueProbability(trueAcceptanceProbability(donorId, request, distanceKm, contactsLast30Days, timeOfDayHour));
    }
}
