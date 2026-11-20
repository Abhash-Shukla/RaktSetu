package lifeline.eval.study.scalability;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration options for the E8 Engine Scalability and Latency Benchmark (Claim C7).
 */
public record ScalabilityConfig(
    List<Integer> donorPoolSizes,
    List<Integer> concurrencyLevels,
    int warmupIterations,
    int measuredIterations,
    Map<Integer, Integer> operationsPerConcurrency,
    int topN,
    long seed,
    String outputDir
) {
    public static final List<Integer> REQUIRED_POOL_SIZES = List.of(1000, 5000, 20000, 50000);
    public static final List<Integer> REQUIRED_CONCURRENCY_LEVELS = List.of(1, 10, 50, 100);
    public static final int DEFAULT_WARMUP = 20;
    public static final int DEFAULT_MEASURED = 100;
    public static final int DEFAULT_TOP_N = 10;
    public static final long DEFAULT_SEED = 1001L;
    public static final String DEFAULT_OUTPUT_DIR = "results/e8-final";
    public static final Map<Integer, Integer> DEFAULT_SUSTAINED_OPERATIONS = Map.of(
        1, 100,
        10, 100,
        50, 500,
        100, 1000
    );

    public ScalabilityConfig(
        List<Integer> donorPoolSizes,
        List<Integer> concurrencyLevels,
        int warmupIterations,
        int measuredIterations,
        int topN,
        long seed,
        String outputDir
    ) {
        this(donorPoolSizes, concurrencyLevels, warmupIterations, measuredIterations, Map.of(), topN, seed, outputDir);
    }

    public ScalabilityConfig {
        Objects.requireNonNull(donorPoolSizes, "donorPoolSizes cannot be null");
        Objects.requireNonNull(concurrencyLevels, "concurrencyLevels cannot be null");
        Objects.requireNonNull(outputDir, "outputDir cannot be null");
        operationsPerConcurrency = operationsPerConcurrency != null
            ? Map.copyOf(operationsPerConcurrency)
            : Map.of();
        if (donorPoolSizes.isEmpty()) {
            throw new IllegalArgumentException("donorPoolSizes cannot be empty");
        }
        if (concurrencyLevels.isEmpty()) {
            throw new IllegalArgumentException("concurrencyLevels cannot be empty");
        }
        if (warmupIterations < 0) {
            throw new IllegalArgumentException("warmupIterations must be non-negative: " + warmupIterations);
        }
        if (measuredIterations <= 0) {
            throw new IllegalArgumentException("measuredIterations must be positive: " + measuredIterations);
        }
        if (topN <= 0) {
            throw new IllegalArgumentException("topN must be positive: " + topN);
        }
    }

    /**
     * Resolves the measured operation count for a given concurrency level.
     * Uses the per-concurrency mapped operation count if specified; otherwise defaults to measuredIterations.
     */
    public int operationsForConcurrency(int concurrency) {
        if (operationsPerConcurrency.containsKey(concurrency)) {
            return operationsPerConcurrency.get(concurrency);
        }
        return measuredIterations;
    }

    public static ScalabilityConfig defaultConfig() {
        return new ScalabilityConfig(
            REQUIRED_POOL_SIZES,
            REQUIRED_CONCURRENCY_LEVELS,
            DEFAULT_WARMUP,
            DEFAULT_MEASURED,
            Map.of(),
            DEFAULT_TOP_N,
            DEFAULT_SEED,
            DEFAULT_OUTPUT_DIR
        );
    }

    public static ScalabilityConfig sustainedConfig() {
        return new ScalabilityConfig(
            REQUIRED_POOL_SIZES,
            REQUIRED_CONCURRENCY_LEVELS,
            DEFAULT_WARMUP,
            DEFAULT_MEASURED,
            DEFAULT_SUSTAINED_OPERATIONS,
            DEFAULT_TOP_N,
            DEFAULT_SEED,
            "results/e8-sustained"
        );
    }

    public static ScalabilityConfig smokeConfig() {
        return new ScalabilityConfig(
            List.of(1000, 5000),
            List.of(1, 10),
            5,
            10,
            DEFAULT_TOP_N,
            DEFAULT_SEED,
            "results/e8-smoke"
        );
    }

    /**
     * Loads scalability configuration from YAML.
     */
    @SuppressWarnings("unchecked")
    public static ScalabilityConfig load(Path yamlPath) {
        if (!Files.exists(yamlPath)) {
            return defaultConfig();
        }

        try (InputStream in = Files.newInputStream(yamlPath)) {
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Map<String, Object> data = yaml.load(in);

            List<Integer> poolSizes = data.containsKey("donorPoolSizes")
                ? ((List<?>) data.get("donorPoolSizes")).stream().map(s -> ((Number) s).intValue()).toList()
                : REQUIRED_POOL_SIZES;

            List<Integer> concurrency = data.containsKey("concurrencyLevels")
                ? ((List<?>) data.get("concurrencyLevels")).stream().map(s -> ((Number) s).intValue()).toList()
                : REQUIRED_CONCURRENCY_LEVELS;

            int warmup = data.containsKey("warmupIterations")
                ? ((Number) data.get("warmupIterations")).intValue()
                : DEFAULT_WARMUP;

            int measured = data.containsKey("measuredIterations")
                ? ((Number) data.get("measuredIterations")).intValue()
                : DEFAULT_MEASURED;

            Map<Integer, Integer> opsPerConcurrency = new java.util.HashMap<>();
            if (data.containsKey("operationsPerConcurrency")) {
                Map<?, ?> map = (Map<?, ?>) data.get("operationsPerConcurrency");
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    int conc = ((Number) entry.getKey()).intValue();
                    int ops = ((Number) entry.getValue()).intValue();
                    opsPerConcurrency.put(conc, ops);
                }
            }

            int topN = data.containsKey("topN")
                ? ((Number) data.get("topN")).intValue()
                : DEFAULT_TOP_N;

            long seed = data.containsKey("seed")
                ? ((Number) data.get("seed")).longValue()
                : DEFAULT_SEED;

            String outDir = data.containsKey("outputDir")
                ? (String) data.get("outputDir")
                : DEFAULT_OUTPUT_DIR;

            return new ScalabilityConfig(poolSizes, concurrency, warmup, measured, opsPerConcurrency, topN, seed, outDir);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load ScalabilityConfig from " + yamlPath, e);
        }
    }
}

