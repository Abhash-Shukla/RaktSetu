package lifeline.sim.response;

import lifeline.core.domain.BloodRequest;
import lifeline.core.domain.Urgency;
import lifeline.sim.model.LatentDonor;
import lifeline.sim.model.SimulationParameters;

import java.time.Duration;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * M3 Response Model: Mis-specified stress test.
 * <p>
 * Under EVALUATION_PROTOCOL.md Section 3:
 * Distance effect is near zero (P-22), and donor possesses a hidden unobservable
 * zone-affinity effect (+/- 0.5 logit, P-22) not visible to the engine.
 */
public final class M3MisspecifiedModel implements ResponseModel {

    public static final String NAME = "M3";

    private final SimulationParameters params;

    public M3MisspecifiedModel(SimulationParameters params) {
        this.params = Objects.requireNonNull(params, "params cannot be null");
    }

    public M3MisspecifiedModel() {
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

        // Base probability: willingness * reliability
        double p0 = Math.min(0.95, Math.max(0.05, donor.willingness() * donor.reliability()));

        // In M3, distance coefficient is near zero (P-22: distance has virtually zero effect)
        double distCoeff = params.m3DistanceCoefficient();
        double distDecay = Math.exp(-distCoeff * Math.max(0.0, distanceKm));

        // Add hidden zone-affinity term in logit space (+/- 0.5 logit, P-22)
        double logitP0 = Math.log(p0 / (1.0 - p0));
        double affinity = donor.hiddenZoneAffinity();
        double logitAffinity = logitP0 + affinity;

        // Sigmoid back to probability space
        double p = 1.0 / (1.0 + Math.exp(-logitAffinity));
        p *= distDecay;

        // Urgency sensitivity: CRITICAL +15% relative acceptance (P-21)
        if (request.urgency() == Urgency.CRITICAL) {
            p *= (1.0 + params.m1UrgencyCriticalBonus());
        }

        // Cap at 0.95 (P-16)
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
