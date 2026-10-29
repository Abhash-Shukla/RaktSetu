package lifeline.sim.world;

import lifeline.core.domain.*;
import lifeline.core.inventory.BankInventory;
import lifeline.core.state.DonorState;
import lifeline.sim.model.LatentDonor;
import lifeline.sim.model.SimulationParameters;
import lifeline.sim.response.M1BaselineLogisticModel;
import lifeline.sim.response.ResponseModel;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.random.RandomGenerator;

/**
 * Procedural synthetic world generator.
 * <p>
 * Under AGENTS.md Section 1 and docs/PARAMETERS.md:
 * <ul>
 *   <li>Synthetic data only; zero real patient/donor data.</li>
 *   <li>All randomness via an injected RandomGenerator (SplittableRandom) seeded from config.</li>
 *   <li>Samples blood groups from population.yaml distribution.</li>
 *   <li>Simulates 18 months of past outreach to derive HistoryStats from events only.</li>
 * </ul>
 */
public final class WorldGenerator {

    private final SimulationParameters params;
    private final Map<BloodGroup, Double> populationFrequencies;

    public WorldGenerator(SimulationParameters params) {
        this.params = Objects.requireNonNull(params, "params cannot be null");
        this.populationFrequencies = loadPopulationFrequencies();
    }

    public WorldGenerator() {
        this(SimulationParameters.loadDefault());
    }

    /**
     * Generates a fully populated synthetic world using the provided seed.
     *
     * @param seed random seed (reporting seeds 1-30, tuning seeds 1000+)
     * @return generated synthetic world
     */
    public World generate(long seed) {
        RandomGenerator rng = new SplittableRandom(seed);
        Instant referenceTime = Instant.parse("2026-10-15T12:00:00Z");
        LocalDate referenceDate = LocalDate.ofInstant(referenceTime, ZoneOffset.UTC);

        // 1. Generate Zones and Centroids (P-04)
        int zoneCount = params.zoneCount();
        Map<String, GeoCoordinate> centroids = new LinkedHashMap<>();
        double[] zoneWeights = new double[zoneCount];
        double totalZoneWeight = 0.0;

        // Spread zones across synthetic metropolitan area (e.g. 19.0 to 19.3 N, 72.8 to 73.1 E)
        for (int i = 0; i < zoneCount; i++) {
            String zoneId = String.format("Z%02d", i + 1);
            double lat = 19.00 + 0.30 * (i / 6.0) / 7.0 + 0.02 * (rng.nextDouble() - 0.5);
            double lon = 72.80 + 0.30 * (i % 6.0) / 6.0 + 0.02 * (rng.nextDouble() - 0.5);
            centroids.put(zoneId, new GeoCoordinate(lat, lon));

            // Uneven density weights from lognormal(0, sigma) per P-04
            double lognormalDraw = Math.exp(params.densityLognormalSigma() * rng.nextGaussian());
            zoneWeights[i] = lognormalDraw;
            totalZoneWeight += lognormalDraw;
        }

        ZoneMap zoneMap = new ZoneMap(centroids, params.roadFactor());
        List<String> zoneList = new ArrayList<>(centroids.keySet());

        // 2. Generate Base Donors and Latent Traits (P-10..P-18, P-27)
        int donorCount = params.donorPoolSize();
        List<LatentDonor> latentList = new ArrayList<>(donorCount);

        for (int i = 0; i < donorCount; i++) {
            String donorId = String.format("DONOR-%05d", i + 1);

            // Blood group from population.yaml
            BloodGroup group = sampleBloodGroup(rng);

            // Sex split: 85% male / 15% female (P-27)
            Sex sex = rng.nextDouble() < params.maleShare() ? Sex.MALE : Sex.FEMALE;

            // Home zone from uneven density distribution
            String homeZone = sampleCategorical(zoneList, zoneWeights, totalZoneWeight, rng);

            // Days since last donation: Uniform(0, 365) (P-11)
            int daysAgo = rng.nextInt(params.maxDonationAgeDays() + 1);
            LocalDate lastDonationDate = referenceDate.minusDays(daysAgo);

            // Availability: 70% true (P-12)
            boolean availability = rng.nextDouble() < params.availabilityShare();

            // Current location: 30% share (P-13)
            Optional<CurrentLocation> currentLocation = Optional.empty();
            if (rng.nextDouble() < params.currentLocationShare()) {
                boolean isFresh = rng.nextDouble() < 0.85;
                Instant confirmedAt = isFresh
                    ? referenceTime.minus(Duration.ofMinutes(rng.nextInt(1, params.freshnessWindowHours() * 60)))
                    : referenceTime.minus(Duration.ofHours(rng.nextInt(params.freshnessWindowHours() + 1, 96)));
                String locZone = rng.nextDouble() < 0.70 ? homeZone : zoneList.get(rng.nextInt(zoneList.size()));
                currentLocation = Optional.of(new CurrentLocation(locZone, confirmedAt));
            }

            // Latent traits (P-14..P-18)
            // Willingness ~ Beta(alpha=1.2, beta=2.8) -> mean 0.30, kappa 4.0
            double alpha = params.meanWillingness() * params.heterogeneityKappa();
            double beta = (1.0 - params.meanWillingness()) * params.heterogeneityKappa();
            double willingness = RandomDistributionUtil.sampleBeta(alpha, beta, rng);

            // Reliability in [0.75, 1.0] (P-16)
            double reliability = 0.75 + 0.25 * rng.nextDouble();

            // Delay scale around median 30 min (P-17)
            double delayScale = params.delayMedianMinutes() * Math.exp(0.3 * rng.nextGaussian());

            // Travel tolerance around median 10 km (P-18)
            double travelTolerance = params.travelToleranceKm() * Math.exp(0.3 * rng.nextGaussian());

            // Fatigue sensitivity (P-19)
            double fatigueSensitivity = params.m2FatigueFactor();

            // Hidden zone affinity (+/- 0.5 logit, P-22)
            double hiddenZoneAffinity = (rng.nextDouble() - 0.5) * 2.0 * params.m3HiddenZoneAffinityLogit();

            DonorSnapshot snapshot = new DonorSnapshot(
                donorId,
                group,
                sex,
                homeZone,
                lastDonationDate,
                availability ? DonorState.AVAILABLE : DonorState.DECLINED,
                currentLocation,
                HistoryStats.empty()
            );

            latentList.add(new LatentDonor(
                snapshot,
                willingness,
                reliability,
                delayScale,
                travelTolerance,
                fatigueSensitivity,
                hiddenZoneAffinity
            ));
        }

        // 3. Simulate History and derive observable HistoryStats strictly from events (Requirement 4, P-23)
        Map<String, LatentDonor> finalLatentDonors = simulatePastEventsAndDeriveStats(
            latentList, zoneMap, referenceTime, rng
        );

        // 4. Generate Simulated Bank Inventories (Requirement 2, P-09)
        List<BankInventory> bankInventories = new ArrayList<>();
        for (int i = 0; i < zoneCount; i++) {
            String zoneId = zoneList.get(i);
            String bankId = String.format("BANK-%02d", i + 1);
            String bankName = "Blood Bank " + zoneId;

            Map<BloodGroup, Integer> stock = new EnumMap<>(BloodGroup.class);
            for (BloodGroup bg : BloodGroup.values()) {
                // Stock units proportional to population frequency (P-09 sweep baseline)
                double expectedUnits = 10.0 * populationFrequencies.getOrDefault(bg, 0.1);
                int units = Math.max(0, (int) Math.round(expectedUnits + rng.nextGaussian() * 1.5));
                stock.put(bg, units);
            }

            bankInventories.add(new BankInventory(
                bankId,
                bankName,
                zoneId,
                stock,
                String.format("+91-22-%04d-%04d", 2000 + i, 5000 + i)
            ));
        }

        return new World(zoneMap, finalLatentDonors, bankInventories, params, referenceTime);
    }

    /**
     * Simulates 18 months of past outreach events and derives HistoryStats from events only.
     */
    private Map<String, LatentDonor> simulatePastEventsAndDeriveStats(
        List<LatentDonor> donors,
        ZoneMap zoneMap,
        Instant referenceTime,
        RandomGenerator rng
    ) {
        int totalDonors = donors.size();
        int totalEvents = (int) Math.round(totalDonors * params.meanContactsPerDonor());

        record HistoryCounter(int contacts, int accepts, int declines, int noResponses, Instant lastContactAt) {}
        Map<String, HistoryCounter> counters = new HashMap<>();

        ResponseModel simModel = new M1BaselineLogisticModel(params);
        long horizonSeconds = params.historyMonths() * 30L * 24L * 3600L;

        // Simulate events over past 18 months
        for (int e = 0; e < totalEvents; e++) {
            int donorIdx = rng.nextInt(totalDonors);
            LatentDonor donor = donors.get(donorIdx);

            long eventOffset = (long) (rng.nextDouble() * horizonSeconds);
            Instant eventTime = referenceTime.minusSeconds(eventOffset);

            // Mock historical request
            BloodRequest histReq = new BloodRequest(
                "HIST-" + e,
                donor.bloodGroup(),
                1,
                Urgency.URGENT,
                donor.homeZone(),
                eventTime,
                eventTime.plus(Duration.ofHours(6))
            );

            double p = simModel.trueAcceptanceProbability(donor, histReq, 0.0, 0, 14.0);
            Duration delay = simModel.drawResponseDelay(donor, rng);

            // Expiry threshold: 45 min for URGENT (P-25)
            boolean expired = delay.toMinutes() > params.expiryMinutesUrgent();

            HistoryCounter current = counters.getOrDefault(
                donor.id(),
                new HistoryCounter(0, 0, 0, 0, null)
            );

            int contacts = current.contacts + 1;
            int accepts = current.accepts;
            int declines = current.declines;
            int noResponses = current.noResponses;
            Instant lastContact = current.lastContactAt == null || eventTime.isAfter(current.lastContactAt)
                ? eventTime
                : current.lastContactAt;

            if (expired) {
                noResponses++;
            } else if (rng.nextDouble() < p) {
                accepts++;
            } else {
                declines++;
            }

            counters.put(donor.id(), new HistoryCounter(contacts, accepts, declines, noResponses, lastContact));
        }

        // Attach derived HistoryStats to each donor snapshot
        Map<String, LatentDonor> resultMap = new LinkedHashMap<>();
        for (LatentDonor donor : donors) {
            HistoryCounter hc = counters.get(donor.id());
            HistoryStats stats = hc != null
                ? new HistoryStats(hc.contacts, hc.accepts, hc.declines, hc.noResponses, hc.lastContactAt)
                : HistoryStats.empty();

            DonorSnapshot newSnapshot = new DonorSnapshot(
                donor.snapshot().id(),
                donor.snapshot().bloodGroup(),
                donor.snapshot().sex(),
                donor.snapshot().homeZone(),
                donor.snapshot().lastDonationDate(),
                donor.snapshot().state(),
                donor.snapshot().currentLocation(),
                stats
            );

            resultMap.put(donor.id(), donor.withSnapshot(newSnapshot));
        }

        return resultMap;
    }

    private BloodGroup sampleBloodGroup(RandomGenerator rng) {
        double r = rng.nextDouble();
        double cumulative = 0.0;
        for (Map.Entry<BloodGroup, Double> entry : populationFrequencies.entrySet()) {
            cumulative += entry.getValue();
            if (r <= cumulative) {
                return entry.getKey();
            }
        }
        return BloodGroup.O_POS;
    }

    private static String sampleCategorical(List<String> items, double[] weights, double totalWeight, RandomGenerator rng) {
        double r = rng.nextDouble() * totalWeight;
        double cumulative = 0.0;
        for (int i = 0; i < items.size(); i++) {
            cumulative += weights[i];
            if (r <= cumulative) {
                return items.get(i);
            }
        }
        return items.get(items.size() - 1);
    }

    @SuppressWarnings("unchecked")
    private static Map<BloodGroup, Double> loadPopulationFrequencies() {
        try (InputStream in = WorldGenerator.class.getResourceAsStream("/sim/population.yaml")) {
            if (in == null) {
                throw new IllegalStateException("population.yaml not found at /sim/population.yaml");
            }
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Map<String, Object> root = yaml.load(in);
            Map<String, Object> freqs = (Map<String, Object>) root.get("frequencies");

            Map<BloodGroup, Double> map = new EnumMap<>(BloodGroup.class);
            for (Map.Entry<String, Object> entry : freqs.entrySet()) {
                BloodGroup bg = BloodGroup.fromCode(entry.getKey());
                double val = ((Number) entry.getValue()).doubleValue();
                map.put(bg, val);
            }
            return Collections.unmodifiableMap(map);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load /sim/population.yaml", e);
        }
    }
}
