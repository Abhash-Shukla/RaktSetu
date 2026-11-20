package lifeline.eval.study.inventory;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Configuration for Experiment E4 (Inventory-Level Sweep & Tier-0 Evaluation — Claim C1).
 */
public record E4Config(
    String experiment,
    List<Double> inventoryLevels,
    List<String> systems,
    List<String> responseModels,
    List<Long> seeds,
    String outputDir,
    int parallelism,
    double tier0RadiusKm,
    Path configPath
) {
    public static final double DEFAULT_TIER0_RADIUS_KM = 25.0;
    public static final List<Double> DEFAULT_INVENTORY_LEVELS = List.of(0.0, 0.25, 0.50, 1.00);
    public static final List<String> DEFAULT_SYSTEMS = List.of("S6_TIER0_ON", "S6_TIER0_OFF");
    public static final List<String> DEFAULT_RESPONSE_MODELS = List.of("M1", "M2", "M3");
    public static final String DEFAULT_OUTPUT_DIR = "results/e4-final";

    public E4Config {
        Objects.requireNonNull(experiment, "experiment cannot be null");
        Objects.requireNonNull(inventoryLevels, "inventoryLevels cannot be null");
        Objects.requireNonNull(systems, "systems cannot be null");
        Objects.requireNonNull(responseModels, "responseModels cannot be null");
        Objects.requireNonNull(seeds, "seeds cannot be null");
        outputDir = outputDir != null ? outputDir : DEFAULT_OUTPUT_DIR;
        parallelism = parallelism > 0 ? parallelism : Math.max(1, Runtime.getRuntime().availableProcessors());
        tier0RadiusKm = tier0RadiusKm > 0.0 ? tier0RadiusKm : DEFAULT_TIER0_RADIUS_KM;

        if (inventoryLevels.isEmpty()) {
            throw new IllegalArgumentException("inventoryLevels cannot be empty");
        }
        for (double level : inventoryLevels) {
            if (level < 0.0) {
                throw new IllegalArgumentException("Inventory level cannot be negative: " + level);
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

        inventoryLevels = Collections.unmodifiableList(new ArrayList<>(inventoryLevels));
        systems = Collections.unmodifiableList(new ArrayList<>(systems));
        responseModels = Collections.unmodifiableList(new ArrayList<>(responseModels));
        seeds = Collections.unmodifiableList(new ArrayList<>(seeds));
    }

    public static E4Config load(Path configPath) throws IOException {
        try (InputStream in = Files.newInputStream(configPath)) {
            return load(in, configPath);
        }
    }

    public static E4Config load(InputStream in) {
        return load(in, null);
    }

    @SuppressWarnings("unchecked")
    public static E4Config load(InputStream in, Path configPath) {
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Map<String, Object> root = yaml.load(in);

        String experiment = (String) root.getOrDefault("experiment", "E4");

        List<Number> rawLevels = (List<Number>) root.getOrDefault("inventory_levels",
            root.getOrDefault("inventoryLevels", DEFAULT_INVENTORY_LEVELS));
        List<Double> levels = rawLevels.stream().map(Number::doubleValue).toList();

        List<String> systems = (List<String>) root.getOrDefault("systems", DEFAULT_SYSTEMS);
        List<String> responseModels = (List<String>) root.getOrDefault("response_models", DEFAULT_RESPONSE_MODELS);

        List<Number> rawSeeds = (List<Number>) root.get("seeds");
        if (rawSeeds == null || rawSeeds.isEmpty()) {
            throw new IllegalArgumentException("Missing required 'seeds' in E4 configuration");
        }
        List<Long> seeds = rawSeeds.stream().map(Number::longValue).toList();

        String outputDir = (String) root.getOrDefault("output_dir",
            root.getOrDefault("outputDir", DEFAULT_OUTPUT_DIR));

        int parallelism = ((Number) root.getOrDefault("parallelism",
            Runtime.getRuntime().availableProcessors())).intValue();

        double tier0RadiusKm = ((Number) root.getOrDefault("tier0_radius_km",
            root.getOrDefault("tier0RadiusKm", DEFAULT_TIER0_RADIUS_KM))).doubleValue();

        return new E4Config(
            experiment,
            levels,
            systems,
            responseModels,
            seeds,
            outputDir,
            parallelism,
            tier0RadiusKm,
            configPath
        );
    }
}
