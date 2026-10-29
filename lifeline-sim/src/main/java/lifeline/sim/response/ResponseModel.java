package lifeline.sim.response;

import lifeline.core.domain.BloodRequest;
import lifeline.sim.model.LatentDonor;

import java.time.Duration;
import java.util.random.RandomGenerator;

/**
 * Common contract for simulator response models.
 * Evaluates the true probability that a donor accepts an emergency notification,
 * and draws response delays and Bernoulli outcomes.
 */
public interface ResponseModel {

    /**
     * Unique model identifier ("M1", "M2", or "M3").
     */
    String name();

    /**
     * Computes the true acceptance probability for a donor facing a blood request.
     * Hidden from the matching engine and observable only by the simulator and oracle.
     *
     * @param donor              latent donor with unobservable traits
     * @param request            blood requirement
     * @param distanceKm         actual road distance to request zone
     * @param contactsLast30Days number of outreach contacts received by this donor in the past 30 days
     * @param timeOfDayHour      hour of day (0.0 to 24.0) when notification is received
     * @return true acceptance probability in [0.0, 1.0]
     */
    double trueAcceptanceProbability(
        LatentDonor donor,
        BloodRequest request,
        double distanceKm,
        int contactsLast30Days,
        double timeOfDayHour
    );

    /**
     * Draws response delay from the donor's lognormal delay distribution (P-17).
     *
     * @param donor latent donor
     * @param rng   seeded random generator
     * @return duration before donor responds
     */
    Duration drawResponseDelay(LatentDonor donor, RandomGenerator rng);

    /**
     * Draws a Bernoulli response outcome based on the calculated acceptance probability.
     *
     * @param probability true acceptance probability
     * @param rng         seeded random generator
     * @return true if accepted, false if declined
     */
    default boolean drawAcceptance(double probability, RandomGenerator rng) {
        if (probability <= 0.0) return false;
        if (probability >= 1.0) return true;
        return rng.nextDouble() < probability;
    }
}
