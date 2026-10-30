package lifeline.eval.runner;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Immutable configuration for an evaluation experiment runner loaded from YAML.
 */
public record ExperimentConfig(
    String experiment,
    List<String> systems,
    List<String> responseModels,
    List<Long> seeds,
    String outputDir,
    int parallelism
) {
    public ExperimentConfig {
        Objects.requireNonNull(experiment, "experiment cannot be null");
        Objects.requireNonNull(systems, "systems cannot be null");
        Objects.requireNonNull(responseModels, "responseModels cannot be null");
        Objects.requireNonNull(seeds, "seeds cannot be null");
        outputDir = outputDir != null ? outputDir : "results";
        parallelism = parallelism > 0 ? parallelism : Runtime.getRuntime().availableProcessors();
    }

    @SuppressWarnings("unchecked")
    public static ExperimentConfig load(Path yamlPath) {
        try (InputStream in = Files.newInputStream(yamlPath)) {
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Map<String, Object> root = yaml.load(in);

            String exp = (String) root.getOrDefault("experiment", "E1");
            List<String> sys = (List<String>) root.getOrDefault("systems", List.of("S0", "S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8"));
            List<String> models = (List<String>) root.getOrDefault("responseModels", List.of("M1", "M2", "M3"));

            List<?> rawSeeds = (List<?>) root.getOrDefault("seeds", defaultReportingSeeds());
            List<Long> seeds = new ArrayList<>();
            for (Object s : rawSeeds) {
                if (s instanceof Number n) {
                    seeds.add(n.longValue());
                } else {
                    seeds.add(Long.parseLong(s.toString()));
                }
            }

            String outDir = (String) root.getOrDefault("outputDir", "results");
            int par = root.containsKey("parallelism")
                ? ((Number) root.get("parallelism")).intValue()
                : Math.max(1, Runtime.getRuntime().availableProcessors());

            return new ExperimentConfig(exp, sys, models, seeds, outDir, par);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to load experiment configuration from " + yamlPath, e);
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
