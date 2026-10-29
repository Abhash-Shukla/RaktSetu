package lifeline.eval.metric;

import lifeline.core.domain.BloodGroup;
import lifeline.core.domain.BloodRequest;
import lifeline.core.domain.DonorSnapshot;
import lifeline.core.domain.ZoneMap;
import lifeline.sim.engine.SimulationEvent;
import lifeline.sim.engine.SimulationEventType;
import lifeline.sim.engine.SimulationResult;
import lifeline.sim.model.LatentDonor;
import lifeline.sim.oracle.Oracle;
import lifeline.sim.world.World;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Calculates evaluation metrics strictly as specified in docs/EVALUATION_PROTOCOL.md Section 4.
 */
public final class MetricCalculator {

    public static final double RARE_GROUP_FREQUENCY_THRESHOLD = 0.05; // O-, B-, A-, AB-

    private MetricCalculator() {
    }

    /**
     * Calculates comprehensive evaluation metrics from a simulation run.
     *
     * @param result   completed simulation result
     * @param requests all requests submitted to the simulation
     * @param world    simulation world (donors, geography, bank inventory)
     * @param oracle   ground-truth acceptance oracle
     * @return populated EvaluationMetrics record
     */
    public static EvaluationMetrics calculate(
        SimulationResult result,
        List<BloodRequest> requests,
        World world,
        Oracle oracle
    ) {
        Objects.requireNonNull(result, "result cannot be null");
        Objects.requireNonNull(requests, "requests cannot be null");
        Objects.requireNonNull(world, "world cannot be null");

        Map<String, BloodRequest> requestMap = new HashMap<>();
        for (BloodRequest req : requests) {
            requestMap.put(req.id(), req);
        }

        Map<String, LatentDonor> donorMap = world.latentDonors();
        ZoneMap zoneMap = world.zoneMap();

        Set<String> fulfilledRequests = new HashSet<>();
        Map<String, Instant> fulfillmentTimes = new HashMap<>();
        Map<String, String> firstContactedDonor = new HashMap<>();
        Map<String, Instant> firstContactTime = new HashMap<>();

        Map<String, Integer> donorContacts = new HashMap<>();
        for (String donorId : donorMap.keySet()) {
            donorContacts.put(donorId, 0);
        }

        int rareDonorAllocations = 0;
        int rareDonorWastedAllocations = 0;

        for (SimulationEvent event : result.eventLog()) {
            String reqId = event.requestId();

            if (event.type() == SimulationEventType.DONOR_NOTIFIED && event.donorId() != null) {
                donorContacts.merge(event.donorId(), 1, Integer::sum);

                if (event.attemptNumber() == 1 && !firstContactedDonor.containsKey(reqId)) {
                    firstContactedDonor.put(reqId, event.donorId());
                    firstContactTime.put(reqId, event.timestamp());
                }
            } else if (event.type() == SimulationEventType.DONOR_ACCEPTED && event.donorId() != null) {
                LatentDonor donor = donorMap.get(event.donorId());
                BloodRequest req = requestMap.get(reqId);
                if (donor != null && req != null) {
                    boolean donorRare = isRareGroup(donor.bloodGroup());
                    boolean requestRare = isRareGroup(req.bloodGroup());

                    if (donorRare) {
                        rareDonorAllocations++;
                        if (!requestRare) {
                            rareDonorWastedAllocations++;
                        }
                    }
                }
            } else if (event.type() == SimulationEventType.REQUEST_FULFILLED) {
                fulfilledRequests.add(reqId);
                fulfillmentTimes.put(reqId, event.timestamp());
            }
        }

        // 1. Fulfillment within deadline
        int totalRequestsCount = requests.size();
        double fulfillmentWithinDeadline = totalRequestsCount == 0
            ? 0.0
            : (double) fulfilledRequests.size() / totalRequestsCount;

        // 2. Contacts per fulfilled request
        int totalContacts = result.totalNotificationsSent();
        double contactsPerFulfilled = fulfilledRequests.isEmpty()
            ? 0.0
            : (double) totalContacts / fulfilledRequests.size();

        // 3. Time-to-fulfill (median, p90, censored count)
        List<Double> durationsMinutes = new ArrayList<>();
        for (String reqId : fulfilledRequests) {
            BloodRequest req = requestMap.get(reqId);
            Instant fTime = fulfillmentTimes.get(reqId);
            if (req != null && fTime != null) {
                double mins = Duration.between(req.createdAt(), fTime).toSeconds() / 60.0;
                durationsMinutes.add(Math.max(0.0, mins));
            }
        }
        Collections.sort(durationsMinutes);

        double medianTimeToFulfill = quantile(durationsMinutes, 0.50);
        double p90TimeToFulfill = quantile(durationsMinutes, 0.90);
        int censoredCount = totalRequestsCount - fulfilledRequests.size();

        // 4. Unfulfilled rate by blood group
        Map<BloodGroup, Integer> groupTotal = new EnumMap<>(BloodGroup.class);
        Map<BloodGroup, Integer> groupUnfulfilled = new EnumMap<>(BloodGroup.class);
        for (BloodGroup bg : BloodGroup.values()) {
            groupTotal.put(bg, 0);
            groupUnfulfilled.put(bg, 0);
        }

        for (BloodRequest req : requests) {
            BloodGroup bg = req.bloodGroup();
            groupTotal.merge(bg, 1, Integer::sum);
            if (!fulfilledRequests.contains(req.id())) {
                groupUnfulfilled.merge(bg, 1, Integer::sum);
            }
        }

        Map<BloodGroup, Double> unfulfilledRateByGroup = new EnumMap<>(BloodGroup.class);
        for (BloodGroup bg : BloodGroup.values()) {
            int tot = groupTotal.get(bg);
            int unf = groupUnfulfilled.get(bg);
            unfulfilledRateByGroup.put(bg, tot == 0 ? 0.0 : (double) unf / tot);
        }

        // 5. Rare-type waste rate
        double rareTypeWasteRate = rareDonorAllocations == 0
            ? 0.0
            : (double) rareDonorWastedAllocations / rareDonorAllocations;

        // 6. Per-donor contact load (Gini and Max)
        List<Integer> contactCounts = new ArrayList<>(donorContacts.values());
        Collections.sort(contactCounts);
        int maxContacts = contactCounts.isEmpty() ? 0 : contactCounts.get(contactCounts.size() - 1);
        double gini = computeGini(contactCounts);

        // 7. Ranking Regret and NDCG Gap
        double totalRegret = 0.0;
        double totalNdcgGap = 0.0;
        int evaluatedRequestsCount = 0;

        if (oracle != null) {
            for (BloodRequest req : requests) {
                String chosenDonorId = firstContactedDonor.get(req.id());
                Instant contactTime = firstContactTime.getOrDefault(req.id(), req.createdAt());
                double timeOfDayHour = contactTime.atZone(ZoneOffset.UTC).getHour()
                    + contactTime.atZone(ZoneOffset.UTC).getMinute() / 60.0;

                // Candidate pool evaluated at first attempt
                List<Double> candidateTrueProbs = new ArrayList<>();
                double chosenProb = 0.0;
                double maxOracleProb = 0.0;

                for (LatentDonor ld : donorMap.values()) {
                    if (!lifeline.core.compatibility.CompatibilityTable.isCompatible(ld.bloodGroup(), req.bloodGroup())) {
                        continue;
                    }
                    if (!ld.snapshot().availability()) {
                        continue;
                    }

                    double distKm = zoneMap.distanceKm(ld.homeZone(), req.zone());
                    double p = oracle.trueAcceptanceProbability(ld.id(), req, distKm, 0, timeOfDayHour);
                    candidateTrueProbs.add(p);

                    if (p > maxOracleProb) {
                        maxOracleProb = p;
                    }
                    if (chosenDonorId != null && ld.id().equals(chosenDonorId)) {
                        chosenProb = p;
                    }
                }

                if (!candidateTrueProbs.isEmpty()) {
                    evaluatedRequestsCount++;
                    if (chosenDonorId != null) {
                        totalRegret += Math.max(0.0, maxOracleProb - chosenProb);
                    }

                    // Compute NDCG gap
                    candidateTrueProbs.sort(Comparator.reverseOrder());
                    double idcg = 0.0;
                    int k = Math.min(10, candidateTrueProbs.size());
                    for (int i = 0; i < k; i++) {
                        double rel = candidateTrueProbs.get(i);
                        idcg += (Math.pow(2.0, rel) - 1.0) / (Math.log(i + 2.0) / Math.log(2.0));
                    }

                    double dcg = 0.0;
                    if (chosenDonorId != null) {
                        dcg = (Math.pow(2.0, chosenProb) - 1.0) / (Math.log(2.0) / Math.log(2.0)); // top-1 position
                    }

                    double ndcg = idcg == 0.0 ? 1.0 : Math.min(1.0, dcg / idcg);
                    totalNdcgGap += (1.0 - ndcg);
                }
            }
        }

        double meanRegret = evaluatedRequestsCount == 0 ? 0.0 : totalRegret / evaluatedRequestsCount;
        double meanNdcgGap = evaluatedRequestsCount == 0 ? 0.0 : totalNdcgGap / evaluatedRequestsCount;

        return new EvaluationMetrics(
            fulfillmentWithinDeadline,
            contactsPerFulfilled,
            medianTimeToFulfill,
            p90TimeToFulfill,
            censoredCount,
            unfulfilledRateByGroup,
            rareTypeWasteRate,
            gini,
            maxContacts,
            meanRegret,
            meanNdcgGap
        );
    }

    public static boolean isRareGroup(BloodGroup group) {
        return group == BloodGroup.O_NEG
            || group == BloodGroup.AB_NEG
            || group == BloodGroup.A_NEG
            || group == BloodGroup.B_NEG;
    }

    public static double quantile(List<Double> sortedList, double q) {
        if (sortedList.isEmpty()) {
            return 0.0;
        }
        if (sortedList.size() == 1) {
            return sortedList.get(0);
        }
        double pos = q * (sortedList.size() - 1);
        int lower = (int) Math.floor(pos);
        int upper = (int) Math.ceil(pos);
        double weight = pos - lower;
        return sortedList.get(lower) * (1.0 - weight) + sortedList.get(upper) * weight;
    }

    public static double computeGini(List<Integer> sortedCounts) {
        int n = sortedCounts.size();
        if (n == 0) {
            return 0.0;
        }
        long sum = 0;
        for (int c : sortedCounts) {
            sum += c;
        }
        if (sum == 0) {
            return 0.0;
        }

        long weightedSum = 0;
        for (int i = 0; i < n; i++) {
            weightedSum += (long) (i + 1) * sortedCounts.get(i);
        }

        double gini = (2.0 * weightedSum) / ((double) n * sum) - ((double) (n + 1) / n);
        return Math.max(0.0, Math.min(1.0, gini));
    }
}
