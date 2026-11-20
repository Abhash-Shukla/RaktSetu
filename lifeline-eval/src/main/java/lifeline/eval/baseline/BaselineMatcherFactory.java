package lifeline.eval.baseline;

import lifeline.core.config.UrgencyProfile;
import lifeline.core.domain.PopulationFrequencyTable;
import lifeline.core.domain.ZoneMap;
import lifeline.sim.engine.Matcher;
import lifeline.sim.oracle.Oracle;
import lifeline.sim.world.World;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.random.RandomGenerator;

/**
 * Factory for creating systems S0 to S8, study variants, and custom weight profiles.
 */
public final class BaselineMatcherFactory {

    private static final Map<String, UrgencyProfile> CUSTOM_PROFILES = new ConcurrentHashMap<>();

    private BaselineMatcherFactory() {
    }

    public static void registerCustomProfile(String systemId, UrgencyProfile profile) {
        Objects.requireNonNull(systemId, "systemId cannot be null");
        Objects.requireNonNull(profile, "profile cannot be null");
        CUSTOM_PROFILES.put(systemId.toUpperCase(), profile);
    }

    public static Matcher create(
        String systemId,
        World world,
        Oracle oracle,
        RandomGenerator rng,
        long seed
    ) {
        Objects.requireNonNull(systemId, "systemId cannot be null");
        Objects.requireNonNull(world, "world cannot be null");
        ZoneMap zoneMap = world.zoneMap();

        return switch (systemId.toUpperCase()) {
            case "S0", "S0_FCFS" -> new S0FcfsMatcher();
            case "S1", "S1_RANDOM" -> new S1RandomMatcher(rng);
            case "S2", "S2_NEAREST" -> new S2NearestDonorMatcher(zoneMap);
            case "S3", "S3_RULE_BASED" -> new S3RuleBasedMatcher(zoneMap);
            case "S4", "S4_HISTORY_ONLY" -> new S4ResponseHistoryMatcher();
            case "S5", "S5_LEARNED_RANKER" -> S5LearnedRankerMatcher.train(world, zoneMap, seed);
            case "S6", "S6_LIFELINE_LOCKED", "S6_DEFAULT", "S6_TIER0_ON", "S6_TIER0_OFF", "S6_FALLBACK_ON", "S6_FALLBACK_OFF" -> new S6LifeLineLockedMatcher(zoneMap);
            case "S7", "S7_LIFELINE_EXTENSION" -> new S7LifeLineExtensionMatcher(zoneMap);
            case "S8", "S8_ORACLE" -> {
                Objects.requireNonNull(oracle, "oracle is required for S8");
                yield new S8OracleMatcher(oracle, zoneMap);
            }
            case "S6_NO_PROXIMITY" -> E2AblationMatcher.createNoProximity(zoneMap);
            case "S6_NO_RARITY" -> E2AblationMatcher.createNoRarity(zoneMap);
            case "S6_NO_FRESHNESS" -> E2AblationMatcher.createNoFreshness(zoneMap);
            case "S6_SINGLE_URGENCY" -> E2AblationMatcher.createSingleUrgency(zoneMap);
            case "S7_NO_RESPONSIVENESS" -> E2AblationMatcher.createS7NoResponsiveness(zoneMap);
            default -> {
                UrgencyProfile custom = CUSTOM_PROFILES.get(systemId.toUpperCase());
                if (custom != null) {
                    yield new S6LifeLineLockedMatcher(zoneMap, custom, Duration.ofHours(24), PopulationFrequencyTable.defaultIndianPopulation());
                }
                throw new IllegalArgumentException("Unknown system ID: " + systemId);
            }
        };
    }
}
