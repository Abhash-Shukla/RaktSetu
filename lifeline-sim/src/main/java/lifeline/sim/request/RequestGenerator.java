package lifeline.sim.request;

import lifeline.core.domain.BloodGroup;
import lifeline.core.domain.BloodRequest;
import lifeline.core.domain.Urgency;
import lifeline.sim.model.SimulationParameters;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.random.RandomGenerator;

/**
 * Generates synthetic Poisson blood request arrivals over a simulated horizon.
 * Sourced from docs/PARAMETERS.md P-05 to P-08.
 */
public final class RequestGenerator {

    private final SimulationParameters params;
    private final Map<BloodGroup, Double> bloodGroupFrequencies;

    public RequestGenerator(SimulationParameters params, Map<BloodGroup, Double> bloodGroupFrequencies) {
        this.params = Objects.requireNonNull(params, "params cannot be null");
        this.bloodGroupFrequencies = Objects.requireNonNull(bloodGroupFrequencies, "bloodGroupFrequencies cannot be null");
    }

    /**
     * Generates all request arrivals across all zones over the simulation horizon.
     *
     * @param zones     list of zone identifiers
     * @param startTime simulation start time
     * @param rng       seeded random generator
     * @return chronologically sorted list of generated blood requests
     */
    public List<BloodRequest> generateRequests(List<String> zones, Instant startTime, RandomGenerator rng) {
        List<BloodRequest> requests = new ArrayList<>();
        int horizonMinutes = params.horizonDays() * 24 * 60;
        double lambdaPerMinute = params.arrivalMeanPerZonePerDay() / (24.0 * 60.0);

        int requestIdSeq = 1;

        for (String zone : zones) {
            double currentMinute = 0.0;

            while (true) {
                // Exponential interarrival draw: -ln(U) / lambda
                double u = Math.max(1e-12, rng.nextDouble());
                double intervalMinutes = -Math.log(u) / lambdaPerMinute;
                currentMinute += intervalMinutes;

                if (currentMinute >= horizonMinutes) {
                    break;
                }

                Instant createdAt = startTime.plus(Duration.ofMinutes((long) currentMinute));

                // Sample urgency mix (P-06): CRITICAL 20%, URGENT 40%, ROUTINE 40%
                Urgency urgency = sampleUrgency(rng);

                // Deadlines (P-07): CRITICAL 2h, URGENT 6h, ROUTINE 24h
                Duration deadlineDuration = switch (urgency) {
                    case CRITICAL -> Duration.ofHours(params.deadlineHoursCritical());
                    case URGENT -> Duration.ofHours(params.deadlineHoursUrgent());
                    case ROUTINE -> Duration.ofHours(params.deadlineHoursRoutine());
                };
                Instant deadline = createdAt.plus(deadlineDuration);

                // Units per request: 1-4, mean ~2.0 (P-08)
                int units = sampleUnits(rng);

                // Blood group: P-01 / P-02 mix
                BloodGroup bloodGroup = sampleBloodGroup(rng);

                String id = String.format("REQ-%06d", requestIdSeq++);
                requests.add(new BloodRequest(id, bloodGroup, units, urgency, zone, createdAt, deadline));
            }
        }

        requests.sort(Comparator.comparing(BloodRequest::createdAt));
        return Collections.unmodifiableList(requests);
    }

    private Urgency sampleUrgency(RandomGenerator rng) {
        double r = rng.nextDouble();
        if (r < params.urgencyShareCritical()) {
            return Urgency.CRITICAL;
        } else if (r < params.urgencyShareCritical() + params.urgencyShareUrgent()) {
            return Urgency.URGENT;
        } else {
            return Urgency.ROUTINE;
        }
    }

    private int sampleUnits(RandomGenerator rng) {
        double r = rng.nextDouble();
        if (r < 0.35) return 1;
        if (r < 0.75) return 2;
        if (r < 0.90) return 3;
        return 4;
    }

    private BloodGroup sampleBloodGroup(RandomGenerator rng) {
        double r = rng.nextDouble();
        double cumulative = 0.0;
        for (Map.Entry<BloodGroup, Double> entry : bloodGroupFrequencies.entrySet()) {
            cumulative += entry.getValue();
            if (r <= cumulative) {
                return entry.getKey();
            }
        }
        return BloodGroup.O_POS;
    }
}
