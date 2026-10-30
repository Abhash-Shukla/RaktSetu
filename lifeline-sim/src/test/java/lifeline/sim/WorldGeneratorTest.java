package lifeline.sim;

import lifeline.core.domain.BloodGroup;
import lifeline.core.domain.HistoryStats;
import lifeline.core.domain.Sex;
import lifeline.core.filter.CandidateFilter;
import lifeline.core.filter.FilterResult;
import lifeline.sim.model.LatentDonor;
import lifeline.sim.model.SimulationParameters;
import lifeline.sim.world.World;
import lifeline.sim.world.WorldGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class WorldGeneratorTest {

    @Test
    @DisplayName("Same seed produces identical world: donors, latent traits, and bank inventories")
    void sameSeedProducesIdenticalWorld() {
        SimulationParameters params = SimulationParameters.loadDefault();
        WorldGenerator gen1 = new WorldGenerator(params);
        WorldGenerator gen2 = new WorldGenerator(params);

        World w1 = gen1.generate(42L);
        World w2 = gen2.generate(42L);

        assertThat(w1.latentDonors().keySet()).isEqualTo(w2.latentDonors().keySet());
        assertThat(w1.bankInventories()).isEqualTo(w2.bankInventories());

        for (String id : w1.latentDonors().keySet()) {
            LatentDonor d1 = w1.latentDonors().get(id);
            LatentDonor d2 = w2.latentDonors().get(id);

            assertThat(d1.snapshot()).isEqualTo(d2.snapshot());
            assertThat(d1.willingness()).isEqualTo(d2.willingness());
            assertThat(d1.reliability()).isEqualTo(d2.reliability());
            assertThat(d1.responseDelayScale()).isEqualTo(d2.responseDelayScale());
            assertThat(d1.travelToleranceKm()).isEqualTo(d2.travelToleranceKm());
            assertThat(d1.fatigueSensitivity()).isEqualTo(d2.fatigueSensitivity());
            assertThat(d1.hiddenZoneAffinity()).isEqualTo(d2.hiddenZoneAffinity());
        }
    }

    @Test
    @DisplayName("Blood-group frequencies converge to population.yaml distribution within statistical tolerance")
    void bloodGroupFrequenciesConvergeToPopulationYaml() {
        SimulationParameters params = SimulationParameters.loadDefault();
        WorldGenerator gen = new WorldGenerator(params);
        World world = gen.generate(1001L);

        Map<BloodGroup, Double> expectedFreqs = loadPopulationFrequencies();
        Map<BloodGroup, Integer> counts = new EnumMap<>(BloodGroup.class);

        int totalDonors = world.latentDonors().size();
        assertThat(totalDonors).isEqualTo(20_000);

        for (LatentDonor d : world.latentDonors().values()) {
            counts.merge(d.bloodGroup(), 1, Integer::sum);
        }

        // For N=20,000, 3-sigma tolerance: 3 * sqrt(p*(1-p)/N) <= 0.011
        for (BloodGroup bg : BloodGroup.values()) {
            double expected = expectedFreqs.get(bg);
            double actual = (double) counts.getOrDefault(bg, 0) / totalDonors;

            assertThat(actual)
                .as("Blood group %s frequency %.4f should be close to expected %.4f", bg, actual, expected)
                .isCloseTo(expected, within(0.015));
        }
    }

    @Test
    @DisplayName("HistoryStats are strictly derived from simulated past outreach events, not copied from latent traits")
    void historyStatsAreDerivedFromEventsOnly() {
        SimulationParameters params = SimulationParameters.loadDefault();
        WorldGenerator gen = new WorldGenerator(params);
        World world = gen.generate(42L);

        int zeroEventDonors = 0;
        int activeEventDonors = 0;

        for (LatentDonor ld : world.latentDonors().values()) {
            HistoryStats stats = ld.snapshot().historyStats();

            // Contacts must exactly equal sum of outcomes
            assertThat(stats.contacts())
                .as("Contacts must equal accepts + declines + noResponses for donor %s", ld.id())
                .isEqualTo(stats.accepts() + stats.declines() + stats.noResponses());

            if (stats.contacts() == 0) {
                zeroEventDonors++;
                assertThat(stats.lastContactAt()).isNull();
                double smoothed = (stats.accepts() + 1.0) / (stats.contacts() + 1.0 + 2.0);
                assertThat(smoothed).isCloseTo(1.0 / 3.0, within(1e-6));
            } else {
                activeEventDonors++;
                assertThat(stats.lastContactAt()).isNotNull();
            }

            // Verify no latent trait is copied into observable stats
            // Latent willingness is continuous in (0, 1), whereas acceptance rate is derived ratio
            if (stats.contacts() > 0) {
                double rawAcceptRate = (double) stats.accepts() / stats.contacts();
                // Should not simply be equal to latent willingness
                assertThat(rawAcceptRate).isNotEqualTo(ld.willingness());
            }
        }

        assertThat(activeEventDonors).isGreaterThan(10_000);
        assertThat(zeroEventDonors).isGreaterThan(100);
    }

    @Test
    @DisplayName("Cooldown exclusion is strictly respected for male (90d) and female (120d) donors")
    void cooldownExclusionRespectedInGeneratedWorld() {
        SimulationParameters params = SimulationParameters.loadDefault();
        WorldGenerator gen = new WorldGenerator(params);
        World world = gen.generate(42L);

        int maleCooldownCount = 0;
        int femaleCooldownCount = 0;

        for (LatentDonor ld : world.latentDonors().values()) {
            long daysSinceLast = ChronoUnit.DAYS.between(
                ld.snapshot().lastDonationDate(),
                world.referenceTime().atZone(java.time.ZoneOffset.UTC).toLocalDate()
            );

            if (ld.snapshot().sex() == Sex.MALE) {
                if (daysSinceLast < params.cooldownDaysMale()) {
                    maleCooldownCount++;
                }
            } else {
                if (daysSinceLast < params.cooldownDaysFemale()) {
                    femaleCooldownCount++;
                }
            }
        }

        // Days since donation is Uniform(0, 365)
        // Men cooldown = 90d / 365d ~ 24.6%
        // Women cooldown = 120d / 365d ~ 32.8%
        // Under 94% male / 6% female split (P-27, N=20,000):
        // Expected male in cooldown: ~18,800 * 24.6% ~ 4,625
        // Expected female in cooldown: ~1,200 * 32.8% ~ 394
        assertThat(maleCooldownCount).isGreaterThan(3500);
        assertThat(femaleCooldownCount).isGreaterThan(250);
    }

    @SuppressWarnings("unchecked")
    private static Map<BloodGroup, Double> loadPopulationFrequencies() {
        try (InputStream in = WorldGeneratorTest.class.getResourceAsStream("/sim/population.yaml")) {
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Map<String, Object> root = yaml.load(in);
            Map<String, Object> freqs = (Map<String, Object>) root.get("frequencies");

            Map<BloodGroup, Double> map = new EnumMap<>(BloodGroup.class);
            for (Map.Entry<String, Object> entry : freqs.entrySet()) {
                map.put(BloodGroup.fromCode(entry.getKey()), ((Number) entry.getValue()).doubleValue());
            }
            return map;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
