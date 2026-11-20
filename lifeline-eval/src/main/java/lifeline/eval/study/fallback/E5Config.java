package lifeline.eval.study.fallback;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Configuration for Experiment E5 (Fallback Re-ranking & Attempt-Budget Sweep — Claim C4).
 */
public record E5Config(
    String experiment,
    List<Integer> attemptBudgets,
    List<String> systems,
    List<String> responseModels,
    List<Long> seeds,
    String outputDir,
    int parallelism,
    Path configPath
) {
    public static final List<Integer> DEFAULT_ATTEMPT_BUDGETS = List.of(1, 5, 10, 15, 20);
    public static final List<String> DEFAULT_SYSTEMS = List.of("S6_FALLBACK_ON", "S6_FALLBACK_OFF");
    public static final List<String> DEFAULT_RESPONSE_MODELS = List.of("M1", "M2", "M3");
    public static final String DEFAULT_OUTPUT_DIR = "results/e5-final";

    public E5Config {
        Objects.requireNonNull(experiment, "experiment cannot be null");
        Objects.requireNonNull(attemptBudgets, "attemptBudgets cannot be null");
        Objects.requireNonNull(systems, "systems cannot be null");
        Objects.requireNonNull(responseModels, "responseModels cannot be null");
        Objects.requireNonNull(seeds, "seeds cannot be null");
        outputDir = outputDir != null ? outputDir : DEFAULT_OUTPUT_DIR;
        parallelism = parallelism > 0 ? parallelism : Math.max(1, Runtime.getRuntime().availableProcessors());

        if (attemptBudgets.isEmpty()) {
            throw new IllegalArgumentException("attemptBudgets cannot be empty");
        }
        for (int budget : attemptBudgets) {
            if (budget < 1) {
                throw new IllegalArgumentException("Attempt budget must be >= 1: " + budget);
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

        attemptBudgets = Collections.unmodifiableList(new ArrayList<>(attemptBudgets));
        systems = Collections.unmodifiableList(new ArrayList<>(systems));
        responseModels = Collections.unmodifiableList(new ArrayList<>(responseModels));
        seeds = Collections.unmodifiableList(new ArrayList<>(seeds));
    }

    public static E5Config load(Path configPath) throws IOException {
        try (InputStream in = Files.newInputStream(configPath)) {
            return load(in, configPath);
        }
    }

    public static E5Config load(InputStream in) {
        return load(in, null);
    }

    @SuppressWarnings("unchecked")
    public static E5Config load(InputStream in, Path configPath) {
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Map<String, Object> root = yaml.load(in);

        String experiment = (String) root.getOrDefault("experiment", "E5");

        List<Number> rawBudgets = (List<Number>) root.getOrDefault("attempt_budgets",
            root.getOrDefault("attemptBudgets", DEFAULT_ATTEMPT_BUDGETS));
        List<Integer> budgets = rawBudgets.stream().map(Number::intValue).toList();

        List<String> systems = (List<String>) root.getOrDefault("systems", DEFAULT_SYSTEMS);
        List<String> responseModels = (List<String>) root.getOrDefault("response_models", DEFAULT_RESPONSE_MODELS);

        List<Number> rawSeeds = (List<Number>) root.get("seeds");
        if (rawSeeds == null || rawSeeds.isEmpty()) {
            throw new IllegalArgumentException("Missing required 'seeds' in E5 configuration");
        }
        List<Long> seeds = rawSeeds.stream().map(Number::longValue).toList();

        String outputDir = (String) root.getOrDefault("output_dir",
            root.getOrDefault("outputDir", DEFAULT_OUTPUT_DIR));

        int parallelism = ((Number) root.getOrDefault("parallelism",
            Runtime.getRuntime().availableProcessors())).intValue();

        return new E5Config(
            experiment,
            budgets,
            systems,
            responseModels,
            seeds,
            outputDir,
            parallelism,
            configPath
        );
    }
}
