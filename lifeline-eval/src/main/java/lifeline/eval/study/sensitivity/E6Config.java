package lifeline.eval.study.sensitivity;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Configuration for Experiment E6 (Weight Sensitivity & Urgency Profile Comparison — Claim C2).
 */
public record E6Config(
    String experiment,
    int candidateCount,
    long masterSamplingSeed,
    List<String> responseModels,
    List<Long> tuningSeeds,
    List<Long> reportingSeeds,
    String outputDir,
    int parallelism,
    Path configPath
) {
    public static final int DEFAULT_CANDIDATE_COUNT = 20;
    public static final long DEFAULT_MASTER_SEED = 20261117L;
    public static final List<String> DEFAULT_RESPONSE_MODELS = List.of("M1", "M2", "M3");
    public static final String DEFAULT_OUTPUT_DIR = "results/e6-final";

    public E6Config {
        Objects.requireNonNull(experiment, "experiment cannot be null");
        Objects.requireNonNull(responseModels, "responseModels cannot be null");
        Objects.requireNonNull(tuningSeeds, "tuningSeeds cannot be null");
        Objects.requireNonNull(reportingSeeds, "reportingSeeds cannot be null");
        outputDir = outputDir != null ? outputDir : DEFAULT_OUTPUT_DIR;
        parallelism = parallelism > 0 ? parallelism : Math.max(1, Runtime.getRuntime().availableProcessors());

        if (candidateCount < 1) {
            throw new IllegalArgumentException("candidateCount must be >= 1: " + candidateCount);
        }
        if (responseModels.isEmpty()) {
            throw new IllegalArgumentException("responseModels cannot be empty");
        }
        if (tuningSeeds.isEmpty()) {
            throw new IllegalArgumentException("tuningSeeds cannot be empty");
        }
        if (reportingSeeds.isEmpty()) {
            throw new IllegalArgumentException("reportingSeeds cannot be empty");
        }

        responseModels = Collections.unmodifiableList(new ArrayList<>(responseModels));
        tuningSeeds = Collections.unmodifiableList(new ArrayList<>(tuningSeeds));
        reportingSeeds = Collections.unmodifiableList(new ArrayList<>(reportingSeeds));
    }

    public static E6Config load(Path configPath) throws IOException {
        try (InputStream in = Files.newInputStream(configPath)) {
            return load(in, configPath);
        }
    }

    public static E6Config load(InputStream in) {
        return load(in, null);
    }

    @SuppressWarnings("unchecked")
    public static E6Config load(InputStream in, Path configPath) {
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Map<String, Object> root = yaml.load(in);

        String experiment = (String) root.getOrDefault("experiment", "E6");
        int candidateCount = ((Number) root.getOrDefault("candidate_count",
            root.getOrDefault("candidateCount", DEFAULT_CANDIDATE_COUNT))).intValue();
        long masterSamplingSeed = ((Number) root.getOrDefault("master_sampling_seed",
            root.getOrDefault("masterSamplingSeed", DEFAULT_MASTER_SEED))).longValue();

        List<String> responseModels = (List<String>) root.getOrDefault("response_models", DEFAULT_RESPONSE_MODELS);

        List<Number> rawTuning = (List<Number>) root.get("tuning_seeds");
        if (rawTuning == null || rawTuning.isEmpty()) {
            throw new IllegalArgumentException("Missing required 'tuning_seeds' in E6 configuration");
        }
        List<Long> tuningSeeds = rawTuning.stream().map(Number::longValue).toList();

        List<Number> rawReporting = (List<Number>) root.get("reporting_seeds");
        if (rawReporting == null || rawReporting.isEmpty()) {
            throw new IllegalArgumentException("Missing required 'reporting_seeds' in E6 configuration");
        }
        List<Long> reportingSeeds = rawReporting.stream().map(Number::longValue).toList();

        String outputDir = (String) root.getOrDefault("output_dir",
            root.getOrDefault("outputDir", DEFAULT_OUTPUT_DIR));

        int parallelism = ((Number) root.getOrDefault("parallelism",
            Runtime.getRuntime().availableProcessors())).intValue();

        return new E6Config(
            experiment,
            candidateCount,
            masterSamplingSeed,
            responseModels,
            tuningSeeds,
            reportingSeeds,
            outputDir,
            parallelism,
            configPath
        );
    }
}
