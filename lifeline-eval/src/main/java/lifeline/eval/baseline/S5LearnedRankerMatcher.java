package lifeline.eval.baseline;

import lifeline.core.domain.BloodRequest;
import lifeline.core.domain.DonorSnapshot;
import lifeline.core.domain.ZoneMap;
import lifeline.sim.engine.Matcher;
import lifeline.sim.world.World;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * System S5: Learned Ranker.
 * Logistic regression trained on observable features from simulated past outreach events,
 * partitioned with an explicit time-based train/validation split (earlier 12 months train, later 6 months test).
 * <p>
 * Uses exclusively observable fields:
 * - Proximity decay
 * - Observable smoothed acceptance history rate
 * - Days since last donation
 * - Log-scaled past contact volume
 * - Request urgency level
 */
public final class S5LearnedRankerMatcher implements Matcher {

    private final ZoneMap zoneMap;
    private final double[] weights; // [bias, w_dist, w_hist, w_recency, w_contacts, w_urgency]

    public S5LearnedRankerMatcher(ZoneMap zoneMap, double[] weights) {
        this.zoneMap = Objects.requireNonNull(zoneMap, "zoneMap cannot be null");
        this.weights = Objects.requireNonNull(weights, "weights cannot be null").clone();
    }

    @Override
    public String name() {
        return "S5_LEARNED_RANKER";
    }

    public double[] weights() {
        return weights.clone();
    }

    @Override
    public Optional<String> selectNextCandidate(
        BloodRequest request,
        List<DonorSnapshot> eligiblePool,
        Set<String> alreadyContacted
    ) {
        return selectNextCandidate(request, eligiblePool, alreadyContacted, request.createdAt());
    }

    @Override
    public Optional<String> selectNextCandidate(
        BloodRequest request,
        List<DonorSnapshot> eligiblePool,
        Set<String> alreadyContacted,
        Instant currentTime
    ) {
        LocalDate currentDate = currentTime.atZone(ZoneOffset.UTC).toLocalDate();

        return eligiblePool.stream()
            .filter(d -> !alreadyContacted.contains(d.id()))
            .max(Comparator
                .comparingDouble((DonorSnapshot d) -> predictScore(d, request, currentDate))
                .thenComparing(DonorSnapshot::id, Comparator.reverseOrder())
            )
            .map(DonorSnapshot::id);
    }

    /**
     * Computes the predicted probability/score using the trained logistic regression weights.
     */
    public double predictScore(DonorSnapshot donor, BloodRequest request, LocalDate currentDate) {
        double[] x = extractFeatures(donor, request, zoneMap, currentDate);
        double logit = 0.0;
        for (int i = 0; i < weights.length; i++) {
            logit += weights[i] * x[i];
        }
        return 1.0 / (1.0 + Math.exp(-Math.max(-20.0, Math.min(20.0, logit))));
    }

    /**
     * Extracts observable normalized feature vector [1.0 (bias), dist, hist, recency, contacts, urgency].
     */
    public static double[] extractFeatures(
        DonorSnapshot donor,
        BloodRequest request,
        ZoneMap zoneMap,
        LocalDate currentDate
    ) {
        // Feature 0: Bias
        double x0 = 1.0;

        // Feature 1: Observable Distance Decay
        double distKm = zoneMap.distanceKm(donor.homeZone(), request.zone());
        double x1 = Math.pow(2.0, -distKm / 10.0);

        // Feature 2: Observable Smoothed Acceptance Rate
        double x2 = (donor.historyStats().accepts() + 1.0) / (donor.historyStats().contacts() + 3.0);

        // Feature 3: Recency of Last Donation
        double x3;
        if (donor.lastDonationDate() == null) {
            x3 = 1.0;
        } else {
            long days = ChronoUnit.DAYS.between(donor.lastDonationDate(), currentDate);
            x3 = Math.min(1.0, Math.max(0.0, days / 365.0));
        }

        // Feature 4: Observable Contacts Volume (log scaled)
        double x4 = Math.min(1.0, Math.log1p(donor.historyStats().contacts()) / Math.log(15.0));

        // Feature 5: Urgency level
        double x5 = switch (request.urgency()) {
            case CRITICAL -> 1.0;
            case URGENT -> 0.5;
            case ROUTINE -> 0.0;
        };

        return new double[]{x0, x1, x2, x3, x4, x5};
    }

    /**
     * Trains a logistic regression model on simulated history using an explicit time-based split.
     * Events before the split cutoff (earlier 12 months) form the training set.
     *
     * @param world   generated simulation world containing donors with observable history
     * @param zoneMap zone distance map
     * @param seed    training seed
     * @return trained S5 Learned Ranker
     */
    public static S5LearnedRankerMatcher train(World world, ZoneMap zoneMap, long seed) {
        SplittableRandom rng = new SplittableRandom(seed);
        List<DonorSnapshot> donors = world.latentDonors().values().stream()
            .map(ld -> ld.snapshot())
            .toList();

        Instant refTime = world.referenceTime();
        // Time split: cut off 6 months before reference time
        Instant splitCutoff = refTime.minus(java.time.Duration.ofDays(180));
        LocalDate refDate = refTime.atZone(ZoneOffset.UTC).toLocalDate();

        List<double[]> xList = new ArrayList<>();
        List<Double> yList = new ArrayList<>();

        List<String> zones = new ArrayList<>(zoneMap.centroids().keySet());

        // Construct training set from donors whose last contact was strictly before the split cutoff
        for (DonorSnapshot donor : donors) {
            if (donor.historyStats().contacts() == 0) {
                continue;
            }

            Instant lastContact = donor.historyStats().lastContactAt();
            // Time-split filtering: only train on past events before the cutoff
            if (lastContact != null && lastContact.isAfter(splitCutoff)) {
                continue;
            }

            // Sample simulated query request
            String targetZone = rng.nextDouble() < 0.60
                ? donor.homeZone()
                : zones.get(rng.nextInt(zones.size()));

            BloodRequest sampleReq = new BloodRequest(
                "TRAIN-REQ-" + donor.id(),
                donor.bloodGroup(),
                1,
                lifeline.core.domain.Urgency.URGENT,
                targetZone,
                refTime.minus(java.time.Duration.ofDays(200)),
                refTime.minus(java.time.Duration.ofDays(200)).plus(java.time.Duration.ofHours(6))
            );

            double[] x = extractFeatures(donor, sampleReq, zoneMap, refDate);

            // Target derived from observable history outcome
            double acceptFraction = (double) donor.historyStats().accepts() / donor.historyStats().contacts();
            double y = acceptFraction >= 0.30 ? 1.0 : 0.0;

            xList.add(x);
            yList.add(y);
        }

        // Fallback default weights if training examples are insufficient
        if (xList.size() < 20) {
            return new S5LearnedRankerMatcher(zoneMap, new double[]{-0.5, 1.5, 1.8, 0.4, 0.2, 0.5});
        }

        // L2 Regularized Logistic Regression Gradient Descent
        int numFeatures = 6;
        double[] w = new double[numFeatures];
        // Initialize weights
        w[0] = -0.5; // Bias
        w[1] = 1.0;  // Distance
        w[2] = 1.5;  // History
        w[3] = 0.3;  // Recency
        w[4] = 0.1;  // Contacts
        w[5] = 0.4;  // Urgency

        double learningRate = 0.05;
        double lambdaL2 = 0.01;
        int epochs = 150;
        int n = xList.size();

        for (int epoch = 0; epoch < epochs; epoch++) {
            double[] grad = new double[numFeatures];

            for (int i = 0; i < n; i++) {
                double[] xi = xList.get(i);
                double yi = yList.get(i);

                double logit = 0.0;
                for (int j = 0; j < numFeatures; j++) {
                    logit += w[j] * xi[j];
                }
                double pi = 1.0 / (1.0 + Math.exp(-Math.max(-20.0, Math.min(20.0, logit))));
                double error = pi - yi;

                for (int j = 0; j < numFeatures; j++) {
                    grad[j] += error * xi[j];
                }
            }

            // Weight update with L2 regularization (do not regularize bias)
            for (int j = 0; j < numFeatures; j++) {
                double reg = (j == 0) ? 0.0 : lambdaL2 * w[j];
                w[j] -= learningRate * ((grad[j] / n) + reg);
            }
        }

        return new S5LearnedRankerMatcher(zoneMap, w);
    }
}
