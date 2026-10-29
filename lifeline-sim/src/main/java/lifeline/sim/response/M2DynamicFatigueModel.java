package lifeline.sim.response;

import lifeline.core.domain.BloodRequest;
import lifeline.sim.model.LatentDonor;
import lifeline.sim.model.SimulationParameters;

import java.time.Duration;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * M2 Response Model: Dynamics the engine does not model.
 * M1 + donor fatigue from repeated contacts (P-19) + time-of-day circadian variation (P-20).
 */
public final class M2DynamicFatigueModel implements ResponseModel {

    public static final String NAME = "M2";

    private final M1BaselineLogisticModel m1Base;
    private final SimulationParameters params;

    public M2DynamicFatigueModel(SimulationParameters params) {
        this.params = Objects.requireNonNull(params, "params cannot be null");
        this.m1Base = new M1BaselineLogisticModel(params);
    }

    public M2DynamicFatigueModel() {
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
        double pM1 = m1Base.trueAcceptanceProbability(donor, request, distanceKm, contactsLast30Days, timeOfDayHour);
        if (pM1 <= 0.0) {
            return 0.0;
        }

        // 1. Fatigue factor: acceptance x 0.8 per contact in previous 30 days (P-19)
        double fatigueBase = donor.fatigueSensitivity() > 0 ? donor.fatigueSensitivity() : params.m2FatigueFactor();
        double fatigueMultiplier = Math.pow(fatigueBase, Math.max(0, contactsLast30Days));

        // 2. Time-of-day effect: +/- 20% relative (P-20), peak afternoon, dip early morning
        double hour = ((timeOfDayHour % 24.0) + 24.0) % 24.0;
        double circadian = Math.sin(2.0 * Math.PI * (hour - 6.0) / 24.0);
        double timeOfDayMultiplier = 1.0 + params.m2TimeOfDayAmplitude() * circadian;

        double p = pM1 * fatigueMultiplier * timeOfDayMultiplier;

        // Cap at 0.95 (P-16)
        return Math.min(0.95, Math.max(0.0, p));
    }

    @Override
    public Duration drawResponseDelay(LatentDonor donor, RandomGenerator rng) {
        return m1Base.drawResponseDelay(donor, rng);
    }
}
