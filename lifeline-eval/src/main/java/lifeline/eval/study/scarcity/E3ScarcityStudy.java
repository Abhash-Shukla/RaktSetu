package lifeline.eval.study.scarcity;

import lifeline.core.domain.BloodGroup;
import lifeline.core.domain.BloodRequest;
import lifeline.core.domain.ZoneMap;
import lifeline.eval.metric.TidyMetricRow;
import lifeline.eval.runner.ExperimentConfig;
import lifeline.eval.runner.ExperimentRunner;
import lifeline.eval.stats.EvaluationDatasetType;
import lifeline.eval.stats.StatisticalAnalyzer;
import lifeline.eval.stats.StatisticalComparison;
import lifeline.sim.engine.SimulationRun;
import lifeline.sim.model.SimulationParameters;
import lifeline.sim.request.RequestGenerator;
import lifeline.sim.world.World;
import lifeline.sim.world.WorldGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Execution harness for Experiment E3 (Scarcity Sweep and Rarity Weighting Evaluation — Claim C3).
 * Evaluates S6 vs S6_NO_RARITY across scarcity multipliers {1.0x, 1.5x, 2.0x, 3.0x} and response models M1-M3.
 */
public final class E3ScarcityStudy {

    private static final Logger log = LoggerFactory.getLogger(E3ScarcityStudy.class);

    private final E3Config config;
    private final SimulationParameters simParams;
    private final Map<BloodGroup, Double> basePopulationFrequencies;
    private final AtomicInteger worldGenerationCount = new AtomicInteger(0);

    public E3ScarcityStudy(E3Config config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.simParams = SimulationParameters.loadDefault();
        this.basePopulationFrequencies = ExperimentRunner.loadPopulationFrequencies();
    }

    public E3StudyResult run() {
        EvaluationDatasetType datasetType = StatisticalAnalyzer.classifyAndValidateSeeds(new HashSet<>(config.seeds()));
        Instant startTime = Instant.now();
        worldGenerationCount.set(0);

        int totalSimulations = config.scarcityMultipliers().size()
            * config.seeds().size()
            * config.systems().size()
            * config.responseModels().size();

        log.info("Starting Experiment E3: {} multipliers, {} seeds, {} systems, {} response models (total simulations={}, parallelism={}, dataset_type={})",
            config.scarcityMultipliers().size(),
            config.seeds().size(),
            config.systems().size(),
            config.responseModels().size(),
            totalSimulations,
            config.parallelism(),
            datasetType
        );

        ExecutorService executor = Executors.newFixedThreadPool(config.parallelism());
        List<TidyMetricRow> allRows = new ArrayList<>();
        Map<String, List<TidyMetricRow>> rowsBySlice = new LinkedHashMap<>();

        try {
            WorldGenerator worldGen = new WorldGenerator(simParams);
            ExperimentRunner helperRunner = new ExperimentRunner(new ExperimentConfig(
                "E3",
                config.systems(),
                config.responseModels(),
                config.seeds(),
                config.outputDir(),
                config.parallelism()
            ));

            // Execute seed by seed to minimize memory and maximize cache locality
            for (long seed : config.seeds()) {
                World world = worldGen.generate(seed);
                worldGenerationCount.incrementAndGet();
                List<String> zones = new ArrayList<>(world.zoneMap().centroids().keySet());

                for (double alpha : config.scarcityMultipliers()) {
                    String sliceName = String.format(Locale.ROOT, "E3_%.1fX", alpha);
                    Map<BloodGroup, Double> scaledFrequencies = ScarcityRequestMixScaler.scale(basePopulationFrequencies, alpha);
                    RequestGenerator reqGen = new RequestGenerator(simParams, scaledFrequencies);

                    // Seed request generation deterministically per seed and multiplier
                    long reqSeed = seed + 100L + (long) (alpha * 1000.0);
                    List<BloodRequest> requests = reqGen.generateRequests(
                        zones,
                        world.referenceTime(),
                        new SplittableRandom(reqSeed)
                    );

                    List<Callable<List<TidyMetricRow>>> tasks = new ArrayList<>();
                    for (String system : config.systems()) {
                        for (String model : config.responseModels()) {
                            tasks.add(() -> helperRunner.executeSingleSimulation(
                                sliceName,
                                system,
                                model,
                                seed,
                                world,
                                requests
                            ));
                        }
                    }

                    List<Future<List<TidyMetricRow>>> futures = executor.invokeAll(tasks);
                    for (Future<List<TidyMetricRow>> f : futures) {
                        List<TidyMetricRow> rows = f.get();
                        allRows.addAll(rows);
                        rowsBySlice.computeIfAbsent(sliceName, k -> new ArrayList<>()).addAll(rows);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("E3 scarcity sweep execution failed", e);
        } finally {
            executor.shutdown();
        }

        // Sort all rows deterministically
        allRows.sort(Comparator
            .comparing(TidyMetricRow::experiment)
            .thenComparing(TidyMetricRow::system)
            .thenComparing(TidyMetricRow::responseModel)
            .thenComparingLong(TidyMetricRow::seed)
            .thenComparing(TidyMetricRow::metric)
        );

        Instant endTime = Instant.now();
        double durationSeconds = java.time.Duration.between(startTime, endTime).toMillis() / 1000.0;

        Path outDir = Path.of(config.outputDir());
        try {
            Files.createDirectories(outDir);

            // 1. Write tidy CSV: e3_results.csv
            Path csvPath = outDir.resolve("e3_results.csv");
            try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8))) {
                writer.println("experiment,system,response_model,seed,metric,value");
                for (TidyMetricRow row : allRows) {
                    writer.println(row.toCsvLine());
                }
            }

            // 2. Statistical analysis per slice
            Path statCsvPath = null;
            Path statReportPath = null;
            List<StatisticalComparison> allComparisons = new ArrayList<>();

            if (config.seeds().size() >= StatisticalAnalyzer.MIN_PAIRED_SEEDS_FOR_INFERENCE) {
                StringBuilder mdBuilder = new StringBuilder();
                mdBuilder.append("# LifeLine Evaluation :: Experiment E3 (Scarcity Sweep) Statistical Report\n\n");
                mdBuilder.append("Evaluation of Rarity Weighting (Claim C3) across Scarcity Multipliers: ")
                    .append(config.scarcityMultipliers()).append("\n\n");
                mdBuilder.append("- Systems: S6 (Rarity ON) vs S6_NO_RARITY (Rarity OFF)\n");
                mdBuilder.append("- Significance test: Paired Wilcoxon signed-rank across seeds, Holm correction, Cliff's delta\n\n");

                for (Map.Entry<String, List<TidyMetricRow>> entry : rowsBySlice.entrySet()) {
                    String slice = entry.getKey();
                    List<TidyMetricRow> sliceRows = entry.getValue();

                    StatisticalAnalyzer analyzer = StatisticalAnalyzer.forExperiment(slice);
                    List<StatisticalComparison> sliceComparisons = analyzer.analyze(sliceRows);
                    allComparisons.addAll(sliceComparisons);

                    mdBuilder.append("## Slice ").append(slice).append("\n\n");
                    mdBuilder.append(analyzer.generateMarkdownReport(sliceComparisons)).append("\n\n---\n\n");
                }

                if (!allComparisons.isEmpty()) {
                    statCsvPath = outDir.resolve("e3_statistical_summary.csv");
                    try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(statCsvPath, StandardCharsets.UTF_8))) {
                        writer.println(StatisticalComparison.csvHeader());
                        for (StatisticalComparison sc : allComparisons) {
                            writer.println(sc.toCsvLine());
                        }
                    }

                    statReportPath = outDir.resolve("e3_statistical_report.md");
                    Files.writeString(statReportPath, mdBuilder.toString(), StandardCharsets.UTF_8);
                }
            } else {
                log.info("E3 executed with {} seed(s). Inferential analysis skipped (minimum {} required).",
                    config.seeds().size(), StatisticalAnalyzer.MIN_PAIRED_SEEDS_FOR_INFERENCE);
            }

            // 3. Write manifest JSON
            Path manifestPath = outDir.resolve("run_manifest.json");
            String gitCommit = resolveGitCommit();
            String configHash = computeConfigHash(config.configPath());

            String manifestJson = buildManifestJson(
                gitCommit,
                configHash,
                datasetType,
                config.scarcityMultipliers(),
                config.systems(),
                config.responseModels(),
                config.seeds(),
                startTime,
                endTime,
                durationSeconds,
                totalSimulations,
                worldGenerationCount.get(),
                "e3_results.csv",
                statCsvPath != null ? "e3_statistical_summary.csv" : null
            );
            Files.writeString(manifestPath, manifestJson, StandardCharsets.UTF_8);

            log.info("E3 Study complete: duration={}s, simulations={}, output={}",
                String.format(Locale.ROOT, "%.2f", durationSeconds), totalSimulations, outDir);

            return new E3StudyResult(
                csvPath,
                manifestPath,
                statCsvPath,
                statReportPath,
                allRows.size(),
                totalSimulations,
                worldGenerationCount.get(),
                durationSeconds,
                allRows,
                allComparisons
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to persist E3 study artifacts", e);
        }
    }

    private String resolveGitCommit() {
        try {
            Process process = new ProcessBuilder("git", "rev-parse", "HEAD").start();
            try (Scanner s = new Scanner(process.getInputStream(), StandardCharsets.UTF_8)) {
                if (s.hasNext()) {
                    return s.next().trim();
                }
            }
        } catch (Exception ignored) {
        }
        return "unknown";
    }

    private String computeConfigHash(Path path) {
        if (path == null || !Files.exists(path)) {
            return "sha256-default";
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "sha256-error";
        }
    }

    private String buildManifestJson(
        String gitCommit,
        String configHash,
        EvaluationDatasetType datasetType,
        List<Double> multipliers,
        List<String> systems,
        List<String> responseModels,
        List<Long> seeds,
        Instant startTime,
        Instant endTime,
        double durationSeconds,
        int totalRuns,
        int worldGenerations,
        String csvFile,
        String statSummaryFile
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"experiment\": \"E3\",\n");
        sb.append(String.format("  \"dataset_type\": \"%s\",\n", datasetType));
        sb.append(String.format("  \"git_commit\": \"%s\",\n", gitCommit));
        sb.append(String.format("  \"config_hash\": \"%s\",\n", configHash));
        sb.append("  \"scarcity_multipliers\": ").append(multipliers).append(",\n");
        sb.append("  \"systems\": ").append(toJsonArray(systems)).append(",\n");
        sb.append("  \"response_models\": ").append(toJsonArray(responseModels)).append(",\n");
        sb.append("  \"seed_count\": ").append(seeds.size()).append(",\n");
        sb.append("  \"seeds\": ").append(seeds).append(",\n");
        sb.append(String.format("  \"start_time\": \"%s\",\n", startTime));
        sb.append(String.format("  \"end_time\": \"%s\",\n", endTime));
        sb.append(String.format("  \"duration_seconds\": %.3f,\n", durationSeconds));
        sb.append(String.format("  \"total_runs\": %d,\n", totalRuns));
        sb.append(String.format("  \"world_generations\": %d,\n", worldGenerations));
        sb.append(String.format("  \"csv_file\": \"%s\"", csvFile));
        if (statSummaryFile != null) {
            sb.append(",\n").append(String.format("  \"statistical_summary_file\": \"%s\"", statSummaryFile));
        } else {
            sb.append(",\n").append("  \"statistical_summary_file\": null");
        }
        sb.append("\n}\n");
        return sb.toString();
    }

    private static String toJsonArray(List<String> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append("\"").append(list.get(i)).append("\"");
            if (i < list.size() - 1) sb.append(", ");
        }
        sb.append("]");
        return sb.toString();
    }
}

