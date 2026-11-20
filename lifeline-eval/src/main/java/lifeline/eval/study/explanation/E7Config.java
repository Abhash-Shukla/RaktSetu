package lifeline.eval.study.explanation;

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
 * Configuration options for the E7 Explanation Faithfulness Study (Claim C5).
 */
public record E7Config(
    List<Long> seeds,
    double numericalTolerance,
    String outputDir,
    int topN,
    boolean includeTargetedCoverageCases
) {
    public static final double DEFAULT_TOLERANCE = 1.0e-12;
    public static final int DEFAULT_TOP_N = 10;
    public static final String DEFAULT_OUTPUT_DIR = "results/e7-final";

    public E7Config {
        Objects.requireNonNull(seeds, "seeds cannot be null");
        Objects.requireNonNull(outputDir, "outputDir cannot be null");
        if (numericalTolerance <= 0) {
            throw new IllegalArgumentException("numericalTolerance must be positive: " + numericalTolerance);
        }
        if (topN <= 0) {
            throw new IllegalArgumentException("topN must be positive: " + topN);
        }
    }

    public static E7Config defaultConfig() {
        return new E7Config(
            List.of(1L, 2L, 3L, 4L, 5L),
            DEFAULT_TOLERANCE,
            DEFAULT_OUTPUT_DIR,
            DEFAULT_TOP_N,
            true
        );
    }

    /**
     * Loads configuration from a YAML file.
     */
    @SuppressWarnings("unchecked")
    public static E7Config load(Path yamlPath) {
        if (!Files.exists(yamlPath)) {
            return defaultConfig();
        }

        try (InputStream in = Files.newInputStream(yamlPath)) {
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Map<String, Object> data = yaml.load(in);

            List<Long> seeds = data.containsKey("seeds")
                ? ((List<?>) data.get("seeds")).stream().map(s -> ((Number) s).longValue()).toList()
                : List.of(1L, 2L, 3L, 4L, 5L);

            double tolerance = data.containsKey("tolerance")
                ? ((Number) data.get("tolerance")).doubleValue()
                : DEFAULT_TOLERANCE;

            String outDir = data.containsKey("outputDir")
                ? (String) data.get("outputDir")
                : DEFAULT_OUTPUT_DIR;

            int topN = data.containsKey("topN")
                ? ((Number) data.get("topN")).intValue()
                : DEFAULT_TOP_N;

            boolean includeTargeted = !data.containsKey("includeTargetedCoverageCases")
                || Boolean.TRUE.equals(data.get("includeTargetedCoverageCases"));

            return new E7Config(seeds, tolerance, outDir, topN, includeTargeted);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load E7 config from " + yamlPath, e);
        }
    }
}

