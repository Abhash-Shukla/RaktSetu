package lifeline.sim.response;

import lifeline.core.domain.BloodRequest;
import lifeline.core.domain.Urgency;
import lifeline.sim.model.LatentDonor;
import lifeline.sim.model.SimulationParameters;

import java.time.Duration;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * M1 Response Model: Baseline realism.
 * Logistic: latent willingness/reliability + distance decay + availability + urgency sensitivity (P-21).
 */
public final class M1BaselineLogisticModel implements ResponseModel {

    public static final String NAME = "M1";

    private final SimulationParameters params;

    public M1BaselineLogisticModel(SimulationParameters params) {
        this.params = Objects.requireNonNull(params, "params cannot be null");
    }

    public M1BaselineLogisticModel() {
        this(SimulationParameters.loadDefault());
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public double trueAcceptanceProbability(
        LatentDonor donor,
        BloodRequest request,
        double distanceKm,
        int contactsLast30Days,
        double timeOfDayHour
    ) {
        if (!donor.snapshot().availability()) {
            return 0.0;
        }

        // Base probability: willingness * reliability (intention-behaviour gap)
        double p0 = donor.willingness() * donor.reliability();

        // Distance decay: halves at travelToleranceKm (P-18)
        double tol = donor.travelToleranceKm() > 0 ? donor.travelToleranceKm() : params.travelToleranceKm();
        double distanceDecay = Math.pow(2.0, -Math.max(0.0, distanceKm) / tol);
        double p = p0 * distanceDecay;

        // Urgency sensitivity: CRITICAL +15% relative acceptance (P-21)
        if (request.urgency() == Urgency.CRITICAL) {
            p *= (1.0 + params.m1UrgencyCriticalBonus());
        }

        // Cap at 0.95 to maintain intention-behaviour gap (P-16, no group near 100%)
        return Math.min(0.95, Math.max(0.0, p));
    }

    @Override
    public Duration drawResponseDelay(LatentDonor donor, RandomGenerator rng) {
        double median = donor.responseDelayScale() > 0 ? donor.responseDelayScale() : params.delayMedianMinutes();
        double mu = Math.log(median);
        double sigma = params.delaySigma();

        double z = rng.nextGaussian();
        double minutes = Math.exp(mu + sigma * z);
        long delaySeconds = Math.max(60, Math.round(minutes * 60.0));
        return Duration.ofSeconds(delaySeconds);
    }
}
