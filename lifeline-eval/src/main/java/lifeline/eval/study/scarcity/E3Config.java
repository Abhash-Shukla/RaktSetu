package lifeline.eval.study.scarcity;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable configuration for the E3 Scarcity Sweep Study (Claim C3).
 * Loaded from YAML configs/e3.yaml or configs/e3-smoke.yaml.
 */
public record E3Config(
    List<Double> scarcityMultipliers,
    List<String> systems,
    List<String> responseModels,
    List<Long> seeds,
    String outputDir,
    int parallelism,
    Path configPath
) {
    public static final List<Double> DEFAULT_SCARCITY_MULTIPLIERS = List.of(1.0, 1.5, 2.0, 3.0);
    public static final List<String> DEFAULT_SYSTEMS = List.of("S6", "S6_NO_RARITY");
    public static final List<String> DEFAULT_RESPONSE_MODELS = List.of("M1", "M2", "M3");
    public static final String DEFAULT_OUTPUT_DIR = "results/e3-final";

    public E3Config {
        Objects.requireNonNull(scarcityMultipliers, "scarcityMultipliers cannot be null");
        Objects.requireNonNull(systems, "systems cannot be null");
        Objects.requireNonNull(responseModels, "responseModels cannot be null");
        Objects.requireNonNull(seeds, "seeds cannot be null");
        outputDir = outputDir != null ? outputDir : DEFAULT_OUTPUT_DIR;
        parallelism = parallelism > 0 ? parallelism : Math.max(1, Runtime.getRuntime().availableProcessors());

        if (scarcityMultipliers.isEmpty()) {
            throw new IllegalArgumentException("scarcityMultipliers cannot be empty");
        }
        for (Double m : scarcityMultipliers) {
            if (m == null || Double.isNaN(m) || Double.isInfinite(m) || m <= 0.0) {
                throw new IllegalArgumentException("Invalid scarcity multiplier: " + m);
            }
        }

        if (systems.isEmpty()) {
            throw new IllegalArgumentException("systems cannot be empty");
        }
        if (responseModels.isEmpty()) {
            throw new IllegalArgumentException("responseModels cannot be empty");
        }
        if (seeds.isEmpty()) {
            throw new IllegalArgumentException("seeds cannot be empty");
        }

        scarcityMultipliers = Collections.unmodifiableList(new ArrayList<>(scarcityMultipliers));
        systems = Collections.unmodifiableList(new ArrayList<>(systems));
        responseModels = Collections.unmodifiableList(new ArrayList<>(responseModels));
        seeds = Collections.unmodifiableList(new ArrayList<>(seeds));
    }

    public E3Config(
        List<Double> scarcityMultipliers,
        List<String> systems,
        List<String> responseModels,
        List<Long> seeds,
        String outputDir,
        int parallelism
    ) {
        this(scarcityMultipliers, systems, responseModels, seeds, outputDir, parallelism, null);
    }

    @SuppressWarnings("unchecked")
    public static E3Config load(Path yamlPath) {
        Objects.requireNonNull(yamlPath, "yamlPath cannot be null");
        if (!Files.exists(yamlPath)) {
            throw new IllegalArgumentException("E3 configuration file not found: " + yamlPath);
        }

        try (InputStream in = Files.newInputStream(yamlPath)) {
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Map<String, Object> root = yaml.load(in);
            if (root == null) {
                throw new IllegalArgumentException("Empty YAML configuration at: " + yamlPath);
            }

            List<Double> multipliers = DEFAULT_SCARCITY_MULTIPLIERS;
            if (root.containsKey("scarcityMultipliers")) {
                List<?> rawMult = (List<?>) root.get("scarcityMultipliers");
                List<Double> list = new ArrayList<>();
                for (Object o : rawMult) {
                    if (o instanceof Number n) {
                        list.add(n.doubleValue());
                    } else {
                        list.add(Double.parseDouble(o.toString()));
                    }
                }
                multipliers = list;
            }

            List<String> systems = root.containsKey("systems")
                ? ((List<?>) root.get("systems")).stream().map(Object::toString).toList()
                : DEFAULT_SYSTEMS;

            List<String> models = root.containsKey("responseModels")
                ? ((List<?>) root.get("responseModels")).stream().map(Object::toString).toList()
                : DEFAULT_RESPONSE_MODELS;

            List<?> rawSeeds = (List<?>) root.getOrDefault("seeds", defaultReportingSeeds());
            List<Long> seeds = new ArrayList<>();
            for (Object s : rawSeeds) {
                if (s instanceof Number n) {
                    seeds.add(n.longValue());
                } else {
                    seeds.add(Long.parseLong(s.toString()));
                }
            }

            String outDir = (String) root.getOrDefault("outputDir", DEFAULT_OUTPUT_DIR);
            int par = root.containsKey("parallelism")
                ? ((Number) root.get("parallelism")).intValue()
                : Math.max(1, Runtime.getRuntime().availableProcessors());

            return new E3Config(multipliers, systems, models, seeds, outDir, par, yamlPath);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to load E3 configuration from " + yamlPath, e);
        }
    }

    public static List<Long> defaultReportingSeeds() {
        List<Long> seeds = new ArrayList<>();
        for (long s = 1; s <= 30; s++) {
            seeds.add(s);
        }
        return seeds;
    }
}

