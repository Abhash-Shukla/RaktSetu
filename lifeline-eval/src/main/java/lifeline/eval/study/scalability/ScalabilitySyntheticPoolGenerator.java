package lifeline.eval.study.scalability;

import lifeline.core.config.UrgencyProfile;
import lifeline.core.domain.*;
import lifeline.core.factor.Context;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Deterministic synthetic generator creating candidate donor pools, blood requests,
 * and topology contexts for scalability benchmarking.
 * <p>
 * Under AGENTS.md Section 4:
 * All randomness is seeded from configuration; same seed yields identical donor pool and requests.
 */
public final class ScalabilitySyntheticPoolGenerator {

    private final long seed;
    private final ZoneMap standardZoneMap;
    private final PopulationFrequencyTable frequencyTable;

    public ScalabilitySyntheticPoolGenerator(long seed) {
        this.seed = seed;
        this.standardZoneMap = createStandardZoneMap();
        this.frequencyTable = PopulationFrequencyTable.defaultIndianPopulation();
    }

    public ZoneMap zoneMap() {
        return standardZoneMap;
    }

    /**
     * Generates an eligible pool of candidate donors of the specified size.
     */
    public List<DonorSnapshot> generatePool(int poolSize) {
        SplittableRandom rng = new SplittableRandom(seed ^ (poolSize * 31L));
        List<DonorSnapshot> donors = new ArrayList<>(poolSize);
        List<String> zoneIds = new ArrayList<>(standardZoneMap.centroids().keySet());

        Instant now = Instant.parse("2026-11-06T12:00:00Z");
        LocalDate refDate = LocalDate.of(2026, 11, 6);
        BloodGroup[] bloodGroups = BloodGroup.values();

        for (int i = 0; i < poolSize; i++) {
            String donorId = String.format("D-BENCH-%06d", i + 1);

            // Blood group according to population frequency distribution
            BloodGroup bg = sampleBloodGroup(rng.nextDouble());

            // Sex: 94% Male, 6% Female per PARAMETERS.md P-27
            Sex sex = (rng.nextDouble() < 0.94) ? Sex.MALE : Sex.FEMALE;

            // Zone distribution
            String homeZone = zoneIds.get(rng.nextInt(zoneIds.size()));

            // Last donation outside statutory cooldown window (180 days ago) so all donors are eligible
            LocalDate lastDonation = refDate.minusDays(180 + rng.nextInt(180));

            // Current location freshness (30% fresh current location, 70% empty per P-13)
            Optional<CurrentLocation> loc = Optional.empty();
            if (rng.nextDouble() < 0.30) {
                String curZone = zoneIds.get(rng.nextInt(zoneIds.size()));
                Instant confirmedAt = now.minus(Duration.ofMinutes(rng.nextInt(60 * 12))); // within 12h
                loc = Optional.of(new CurrentLocation(curZone, confirmedAt));
            }

            // History stats (average 4 contacts)
            int contacts = rng.nextInt(8);
            int accepts = contacts > 0 ? rng.nextInt(contacts + 1) : 0;
            HistoryStats stats = new HistoryStats(contacts, accepts, 0, 0, now.minus(Duration.ofDays(30)));

            donors.add(new DonorSnapshot(
                donorId,
                bg,
                sex,
                homeZone,
                lastDonation,
                true,   // isAvailable
                false,  // isPaused
                loc,
                stats
            ));
        }

        return Collections.unmodifiableList(donors);
    }

    /**
     * Generates a deterministic sequence of blood requests.
     */
    public List<BloodRequest> generateRequests(int count) {
        SplittableRandom rng = new SplittableRandom(seed ^ 0xDEADBEEFL);
        List<BloodRequest> requests = new ArrayList<>(count);
        List<String> zoneIds = new ArrayList<>(standardZoneMap.centroids().keySet());
        Instant now = Instant.parse("2026-11-06T12:00:00Z");

        for (int i = 0; i < count; i++) {
            String reqId = String.format("REQ-BENCH-%04d", i + 1);
            BloodGroup bg = sampleBloodGroup(rng.nextDouble());

            // Urgency: 20% CRITICAL, 40% URGENT, 40% ROUTINE (PARAMETERS.md P-06)
            double rU = rng.nextDouble();
            Urgency urgency;
            Duration deadline;
            if (rU < 0.20) {
                urgency = Urgency.CRITICAL;
                deadline = Duration.ofHours(2);
            } else if (rU < 0.60) {
                urgency = Urgency.URGENT;
                deadline = Duration.ofHours(6);
            } else {
                urgency = Urgency.ROUTINE;
                deadline = Duration.ofHours(24);
            }

            String zone = zoneIds.get(rng.nextInt(zoneIds.size()));
            requests.add(new BloodRequest(
                reqId,
                bg,
                1 + rng.nextInt(2),
                urgency,
                zone,
                now,
                now.plus(deadline)
            ));
        }

        return Collections.unmodifiableList(requests);
    }

    /**
     * Builds ranking context for a given request.
     */
    public Context createContext(BloodRequest req) {
        return new Context(
            standardZoneMap,
            Duration.ofHours(24),
            req.createdAt(),
            frequencyTable,
            10.0,
            1.0,
            2.0,
            UrgencyProfile.lockedSpec().weightsFor(req.urgency()),
            Set.of("PROXIMITY", "RARITY")
        );
    }

    private BloodGroup sampleBloodGroup(double p) {
        // CDF based on population frequencies
        if (p < 0.325313) return BloodGroup.O_POS;
        if (p < 0.646296) return BloodGroup.B_POS;
        if (p < 0.864301) return BloodGroup.A_POS;
        if (p < 0.941299) return BloodGroup.AB_POS;
        if (p < 0.961586) return BloodGroup.O_NEG;
        if (p < 0.981603) return BloodGroup.B_NEG;
        if (p < 0.995198) return BloodGroup.A_NEG;
        return BloodGroup.AB_NEG;
    }

    private static ZoneMap createStandardZoneMap() {
        Map<String, GeoCoordinate> zones = new LinkedHashMap<>();
        // 40 synthetic zones spread across typical metropolitan area (~30 km radius)
        double baseLat = 19.0760;
        double baseLon = 72.8777;

        for (int i = 1; i <= 40; i++) {
            double angle = (2 * Math.PI * i) / 40.0;
            double radius = 2.0 + (i % 6) * 4.0; // 2 to 22 km
            double lat = baseLat + (radius / 111.0) * Math.cos(angle);
            double lon = baseLon + (radius / (111.0 * Math.cos(Math.toRadians(baseLat)))) * Math.sin(angle);
            zones.put("Z" + i, new GeoCoordinate(lat, lon));
        }

        return new ZoneMap(zones, 1.3);
    }
}
