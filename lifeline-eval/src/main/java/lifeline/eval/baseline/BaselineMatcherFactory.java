package lifeline.eval.baseline;

import lifeline.core.domain.ZoneMap;
import lifeline.sim.engine.Matcher;
import lifeline.sim.oracle.Oracle;
import lifeline.sim.world.World;

import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Factory for creating systems S0 to S8.
 */
public final class BaselineMatcherFactory {

    private BaselineMatcherFactory() {
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
            case "S6", "S6_LIFELINE_LOCKED" -> new S6LifeLineLockedMatcher(zoneMap);
            case "S7", "S7_LIFELINE_EXTENSION" -> new S7LifeLineExtensionMatcher(zoneMap);
            case "S8", "S8_ORACLE" -> {
                Objects.requireNonNull(oracle, "oracle is required for S8");
                yield new S8OracleMatcher(oracle, zoneMap);
            }
            default -> throw new IllegalArgumentException("Unknown system ID: " + systemId);
        };
    }
}
