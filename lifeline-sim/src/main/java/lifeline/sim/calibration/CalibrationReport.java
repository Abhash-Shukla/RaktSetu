package lifeline.sim.calibration;

import lifeline.core.domain.BloodGroup;
import lifeline.core.domain.BloodRequest;
import lifeline.core.domain.DonorSnapshot;
import lifeline.core.domain.Urgency;
import lifeline.core.domain.ZoneMap;
import lifeline.sim.model.LatentDonor;
import lifeline.sim.model.SimulationParameters;
import lifeline.sim.response.M1BaselineLogisticModel;
import lifeline.sim.response.ResponseModel;
import lifeline.sim.world.World;
import lifeline.sim.world.WorldGenerator;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Calibration report tool verifying Section B calibration targets and literature anchors
 * in docs/PARAMETERS.md (Wu et al. 2022, Ou-Yang et al. 2020, Patidar & Dhiman 2021).
 */
public final class CalibrationReport {

    public record Metrics(
        double meanAcceptanceProbability,
        double minAcceptanceProbability,
        double p10AcceptanceProbability,
        double medianAcceptanceProbability,
        double p90AcceptanceProbability,
        double maxAcceptanceProbability,
        double topVsBottomWillingnessRatio,
        double medianResponseDelayMinutes,
        double observableProxyLiftOverRandom
    ) {
        /**
         * Alias for {@link #observableProxyLiftOverRandom()}.
         */
        public double rankerVsRandomLift() {
            return observableProxyLiftOverRandom;
        }
    }

    /**
     * Scores a candidate donor using EXCLUSIVELY observable snapshot fields (distance and history stats).
     * Does NOT access latent traits (willingness, reliability, or Oracle).
     * Note: This is an observable ranking proxy for calibration sanity checking; the actual S5 learned
     * ranker (logistic regression on simulated history) is implemented in lifeline-eval.
     */
    public static double scoreObservableProxy(
        DonorSnapshot snapshot,
        String requestZone,
        ZoneMap zoneMap,
        double travelToleranceKm
    ) {
        double distKm = zoneMap.distanceKm(snapshot.homeZone(), requestZone);
        // Proximity decay from observable distance
        double proximityTerm = Math.pow(2.0, -distKm / travelToleranceKm);
        // Smoothed accept rate from observable history (Beta(1, 2) prior per P-23)
        double responsivenessTerm = (snapshot.historyStats().accepts() + 1.0) / (snapshot.historyStats().contacts() + 3.0);
        return 0.70 * proximityTerm + 0.30 * responsivenessTerm;
    }

    public static Metrics computeMetrics(World world, SimulationParameters params) {
        ResponseModel model = new M1BaselineLogisticModel(params);
        Instant now = world.referenceTime();

        // Sample emergency requests across zones
        List<BloodRequest> sampleRequests = new ArrayList<>();
        List<String> zones = new ArrayList<>(world.zoneMap().centroids().keySet());
        for (int i = 0; i < 20; i++) {
            sampleRequests.add(new BloodRequest(
                "CALIB-REQ-" + i,
                BloodGroup.O_POS,
                1,
                Urgency.CRITICAL,
                zones.get(i % zones.size()),
                now,
                now.plus(Duration.ofHours(2))
            ));
        }

        List<LatentDonor> donors = new ArrayList<>(world.latentDonors().values());
        List<Double> probList = new ArrayList<>();
        List<Double> delayList = new ArrayList<>();

        SplittableRandom calibRng = new SplittableRandom(12345L);

        // Evaluate local emergency request acceptance probabilities across active donors (P-14)
        for (LatentDonor d : donors) {
            if (!d.snapshot().availability()) {
                continue;
            }
            BloodRequest localEmergencyReq = new BloodRequest(
                "CALIB-LOCAL-" + d.id(),
                d.bloodGroup(),
                1,
                Urgency.CRITICAL,
                d.homeZone(),
                now,
                now.plus(Duration.ofHours(2))
            );
            double p = model.trueAcceptanceProbability(d, localEmergencyReq, 0.0, 0, 14.0);
            probList.add(p);

            Duration delay = model.drawResponseDelay(d, calibRng);
            delayList.add((double) delay.toMinutes());
        }

        Collections.sort(probList);
        Collections.sort(delayList);

        double sum = 0.0;
        for (double p : probList) sum += p;
        double meanP = probList.isEmpty() ? 0.0 : sum / probList.size();

        double minP = probList.isEmpty() ? 0.0 : probList.get(0);
        double p10 = quantile(probList, 0.10);
        double medianP = quantile(probList, 0.50);
        double p90 = quantile(probList, 0.90);
        double maxP = probList.isEmpty() ? 0.0 : probList.get(probList.size() - 1);

        double medianDelay = quantile(delayList, 0.50);

        // High vs Low willingness group ratio (P-15 / Wu et al. 2022: 1.96x)
        List<LatentDonor> sortedByWillingness = new ArrayList<>(donors);
        sortedByWillingness.sort(Comparator.comparingDouble(LatentDonor::willingness));

        int halfSize = sortedByWillingness.size() / 2;
        double bottomSum = 0.0;
        double topSum = 0.0;

        BloodRequest refReq = sampleRequests.get(0);
        for (int i = 0; i < halfSize; i++) {
            LatentDonor bottomDonor = sortedByWillingness.get(i);
            bottomSum += model.trueAcceptanceProbability(bottomDonor, refReq, 5.0, 0, 14.0);

            LatentDonor topDonor = sortedByWillingness.get(sortedByWillingness.size() - 1 - i);
            topSum += model.trueAcceptanceProbability(topDonor, refReq, 5.0, 0, 14.0);
        }

        double bottomMean = halfSize == 0 ? 0.01 : bottomSum / halfSize;
        double topMean = halfSize == 0 ? 0.01 : topSum / halfSize;
        double topVsBottomRatio = topMean / Math.max(1e-4, bottomMean);

        // First-contact ranker lift over random (Section B / Wu et al. 2022)
        // Average acceptance of top candidate vs random candidate
        double randomSum = 0.0;
        double topRankedSum = 0.0;
        int evalTrials = 200;

        for (int t = 0; t < evalTrials; t++) {
            BloodRequest testReq = sampleRequests.get(t % sampleRequests.size());
            List<LatentDonor> candidates = new ArrayList<>();
            for (LatentDonor d : donors) {
                if (candidates.size() >= 25) break;
                if (!d.snapshot().availability()) continue;
                double dist = world.zoneMap().distanceKm(d.homeZone(), testReq.zone());
                if (dist <= params.travelToleranceKm() * 1.5) {
                    candidates.add(d);
                }
            }

            if (candidates.isEmpty()) continue;

            // Random candidate among eligible pool
            LatentDonor randomCandidate = candidates.get(calibRng.nextInt(candidates.size()));
            double distRandom = world.zoneMap().distanceKm(randomCandidate.homeZone(), testReq.zone());
            randomSum += model.trueAcceptanceProbability(randomCandidate, testReq, distRandom, 0, 14.0);

            // Best candidate chosen strictly using observable snapshot fields (distance and history stats)
            LatentDonor bestCandidate = Collections.max(candidates, Comparator.comparingDouble(
                c -> scoreObservableProxy(c.snapshot(), testReq.zone(), world.zoneMap(), params.travelToleranceKm())
            ));
            double distBest = world.zoneMap().distanceKm(bestCandidate.homeZone(), testReq.zone());
            topRankedSum += model.trueAcceptanceProbability(bestCandidate, testReq, distBest, 0, 14.0);
        }

        double meanRandom = randomSum / evalTrials;
        double meanRanked = topRankedSum / evalTrials;
        double observableProxyLift = meanRanked / Math.max(1e-4, meanRandom);

        return new Metrics(
            meanP,
            minP,
            p10,
            medianP,
            p90,
            maxP,
            topVsBottomRatio,
            medianDelay,
            observableProxyLift
        );
    }

    private static double quantile(List<Double> sortedList, double q) {
        if (sortedList.isEmpty()) return 0.0;
        int idx = (int) Math.round(q * (sortedList.size() - 1));
        return sortedList.get(Math.max(0, Math.min(sortedList.size() - 1, idx)));
    }

    public static void printReport(Metrics m) {
        System.out.println("================================================================================");
        System.out.println("                   LIFELINE SIMULATION CALIBRATION REPORT                      ");
        System.out.println("                 Checks against docs/PARAMETERS.md Section B                    ");
        System.out.println("================================================================================");
        System.out.printf("1. Mean Acceptance Probability (P-14 target ~0.30)        : %.4f%n", m.meanAcceptanceProbability);
        System.out.printf("   - Min                                                   : %.4f%n", m.minAcceptanceProbability);
        System.out.printf("   - 10th Percentile                                       : %.4f%n", m.p10AcceptanceProbability);
        System.out.printf("   - Median                                                : %.4f%n", m.medianAcceptanceProbability);
        System.out.printf("   - 90th Percentile                                       : %.4f%n", m.p90AcceptanceProbability);
        System.out.printf("   - Max (P-16: no group near 100%%)                       : %.4f%n", m.maxAcceptanceProbability);
        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("2. Top vs Bottom Willingness Ratio (Wu 2022 ~1.96x, P-15) : %.2fx%n", m.topVsBottomWillingnessRatio);
        System.out.printf("3. Response Delay Median (P-17 target ~30 min)            : %.1f min%n", m.medianResponseDelayMinutes);
        System.out.printf("4. Observable-Proxy Lift over Random                     : %.2fx%n", m.observableProxyLiftOverRandom);
        System.out.println("   [NOTE: Measured using observable history stats and distance proxy,");
        System.out.println("    NOT the actual S5 learned ranker. S5 must be trained and measured in Phase 5.]");
        System.out.println("================================================================================");
    }

    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 42L;
        SimulationParameters params = SimulationParameters.loadDefault();
        WorldGenerator generator = new WorldGenerator(params);
        World world = generator.generate(seed);

        Metrics metrics = computeMetrics(world, params);
        printReport(metrics);
    }
}
