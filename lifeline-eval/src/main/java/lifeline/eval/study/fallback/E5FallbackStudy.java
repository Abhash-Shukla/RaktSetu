package lifeline.eval.study.fallback;

import lifeline.core.domain.BloodGroup;
import lifeline.core.domain.BloodRequest;
import lifeline.eval.metric.TidyMetricRow;
import lifeline.eval.runner.ExperimentConfig;
import lifeline.eval.runner.ExperimentRunner;
import lifeline.eval.stats.*;
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
 * Execution harness for Experiment E5 (Fallback Re-ranking & Attempt-Budget Sweep — Claim C4).
 * Evaluates S6_FALLBACK_ON vs S6_FALLBACK_OFF across attempt-budget slices {1, 5, 10, 15, 20}
 * and response models M1-M3.
 */
public final class E5FallbackStudy {

    private static final Logger log = LoggerFactory.getLogger(E5FallbackStudy.class);

    private final E5Config config;
    private final SimulationParameters simParams;
    private final Map<BloodGroup, Double> basePopulationFrequencies;
    private final AtomicInteger worldGenerationCount = new AtomicInteger(0);

    public E5FallbackStudy(E5Config config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.simParams = SimulationParameters.loadDefault();
        this.basePopulationFrequencies = ExperimentRunner.loadPopulationFrequencies();
    }

    public E5StudyResult run() {
        EvaluationDatasetType datasetType = StatisticalAnalyzer.classifyAndValidateSeeds(new HashSet<>(config.seeds()));
        Instant startTime = Instant.now();
        worldGenerationCount.set(0);

        int totalSimulations = config.attemptBudgets().size()
            * config.seeds().size()
            * config.systems().size()
            * config.responseModels().size();

        log.info("Starting Experiment E5: {} attempt budgets, {} seeds, {} systems, {} response models (total simulations={}, parallelism={}, dataset_type={})",
            config.attemptBudgets().size(),
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
            RequestGenerator reqGen = new RequestGenerator(simParams, basePopulationFrequencies);
            ExperimentRunner helperRunner = new ExperimentRunner(new ExperimentConfig(
                "E5",
                config.systems(),
                config.responseModels(),
                config.seeds(),
                config.outputDir(),
                config.parallelism()
            ));

            // Execute seed by seed to guarantee identical worlds and requests across all conditions
            for (long seed : config.seeds()) {
                World world = worldGen.generate(seed);
                worldGenerationCount.incrementAndGet();
                List<String> zones = new ArrayList<>(world.zoneMap().centroids().keySet());

                // Generate identical request stream for this seed
                List<BloodRequest> requests = reqGen.generateRequests(
                    zones,
                    world.referenceTime(),
                    new SplittableRandom(seed + 100L)
                );

                for (int budget : config.attemptBudgets()) {
                    String sliceName = "E5_B" + budget;

                    // Fallback ON uses the swept attempt budget B
                    World worldOn = new World(
                        world.zoneMap(),
                        world.latentDonors(),
                        world.bankInventories(),
                        world.parameters().withAttemptBudget(budget),
                        world.referenceTime()
                    );

                    // Fallback OFF strictly uses attempt budget 1 (single outreach attempt, no fallback re-ranking)
                    World worldOff = new World(
                        world.zoneMap(),
                        world.latentDonors(),
                        world.bankInventories(),
                        world.parameters().withAttemptBudget(1),
                        world.referenceTime()
                    );

                    List<Callable<List<TidyMetricRow>>> tasks = new ArrayList<>();
                    for (String system : config.systems()) {
                        boolean isFallbackOn = system.toUpperCase().contains("ON");
                        World activeWorld = isFallbackOn ? worldOn : worldOff;

                        for (String model : config.responseModels()) {
                            tasks.add(() -> helperRunner.executeSingleSimulation(
                                sliceName,
                                system,
                                model,
                                seed,
                                activeWorld,
                                requests,
                                false,
                                25.0
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
            throw new RuntimeException("E5 fallback study execution failed", e);
        } finally {
            executor.shutdown();
        }

        // Sort all tidy rows deterministically
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

            // 1. Write tidy CSV
            Path csvPath = outDir.resolve("e5_results.csv");
            try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8))) {
                writer.println(TidyMetricRow.csvHeader());
                for (TidyMetricRow row : allRows) {
                    writer.println(row.toCsvLine());
                }
            }

            // 2. Perform statistical analysis per slice and model
            List<StatisticalComparison> allComparisons = new ArrayList<>();
            Path statCsvPath = null;
            Path statReportPath = null;

            if (config.seeds().size() >= StatisticalAnalyzer.MIN_PAIRED_SEEDS_FOR_INFERENCE) {
                for (Map.Entry<String, List<TidyMetricRow>> entry : rowsBySlice.entrySet()) {
                    String sliceName = entry.getKey();
                    List<TidyMetricRow> sliceRows = entry.getValue();

                    StatisticalAnalyzer analyzer = StatisticalAnalyzer.forExperiment(sliceName);
                    List<StatisticalComparison> sliceComparisons = analyzer.analyze(sliceRows);

                    // Add paired derived metric: fallback_fulfillment_lift
                    for (String model : config.responseModels()) {
                        StatisticalComparison liftComp = computeFallbackLiftComparison(
                            sliceName,
                            datasetType,
                            model,
                            sliceRows,
                            config.seeds()
                        );
                        if (liftComp != null) {
                            sliceComparisons.add(liftComp);
                        }
                    }

                    allComparisons.addAll(sliceComparisons);
                }

                // Sort comparisons deterministically
                allComparisons.sort(Comparator
                    .comparing(StatisticalComparison::experiment)
                    .thenComparing(StatisticalComparison::responseModel)
                    .thenComparing(StatisticalComparison::candidateSystem)
                    .thenComparing(StatisticalComparison::baselineSystem)
                    .thenComparing(StatisticalComparison::metric)
                );

                // Write statistical summary CSV
                statCsvPath = outDir.resolve("e5_statistical_summary.csv");
                try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(statCsvPath, StandardCharsets.UTF_8))) {
                    writer.println(StatisticalComparison.csvHeader());
                    for (StatisticalComparison sc : allComparisons) {
                        writer.println(sc.toCsvLine());
                    }
                }

                // Write statistical report markdown
                statReportPath = outDir.resolve("e5_statistical_report.md");
                writeStatisticalReport(statReportPath, allComparisons, datasetType, durationSeconds, totalSimulations);
            }

            // 3. Write execution manifest
            Path manifestPath = outDir.resolve("run_manifest.json");
            String configHash = computeConfigHash(config.configPath());
            String manifestJson = formatManifestJson(
                config.experiment(),
                configHash,
                datasetType,
                config.attemptBudgets(),
                config.systems(),
                config.responseModels(),
                config.seeds(),
                startTime,
                endTime,
                durationSeconds,
                totalSimulations,
                worldGenerationCount.get(),
                "e5_results.csv",
                statCsvPath != null ? "e5_statistical_summary.csv" : null
            );
            Files.writeString(manifestPath, manifestJson, StandardCharsets.UTF_8);

            log.info("E5 Study complete: duration={}s, simulations={}, output={}",
                String.format(Locale.ROOT, "%.2f", durationSeconds), totalSimulations, outDir);

            return new E5StudyResult(
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
            throw new RuntimeException("Failed to persist E5 study artifacts", e);
        }
    }

    private StatisticalComparison computeFallbackLiftComparison(
        String sliceName,
        EvaluationDatasetType datasetType,
        String responseModel,
        List<TidyMetricRow> sliceRows,
        List<Long> seeds
    ) {
        Map<Long, Double> offFulfill = new HashMap<>();
        Map<Long, Double> onFulfill = new HashMap<>();

        for (TidyMetricRow r : sliceRows) {
            if (r.experiment().equals(sliceName)
                && r.responseModel().equals(responseModel)
                && r.metric().equals("fulfillment_within_deadline")) {
                if (r.system().equals("S6_FALLBACK_OFF")) {
                    offFulfill.put(r.seed(), r.value());
                } else if (r.system().equals("S6_FALLBACK_ON")) {
                    onFulfill.put(r.seed(), r.value());
                }
            }
        }

        List<Long> commonSeeds = seeds.stream()
            .filter(s -> offFulfill.containsKey(s) && onFulfill.containsKey(s))
            .sorted()
            .toList();

        if (commonSeeds.isEmpty()) {
            return null;
        }

        double[] lift = new double[commonSeeds.size()];
        for (int i = 0; i < commonSeeds.size(); i++) {
            long s = commonSeeds.get(i);
            lift[i] = onFulfill.get(s) - offFulfill.get(s);
        }

        return StatisticalAnalyzer.comparePairedDerivedMetric(
            sliceName,
            datasetType,
            responseModel,
            "S6_FALLBACK_ON",
            "S6_FALLBACK_OFF",
            "fallback_fulfillment_lift",
            MetricDirection.HIGHER_IS_BETTER,
            lift,
            StatisticalAnalyzer.DEFAULT_ALPHA,
            StatisticalAnalyzer.DEFAULT_BOOTSTRAP_SEED
        );
    }

    private void writeStatisticalReport(
        Path reportPath,
        List<StatisticalComparison> comparisons,
        EvaluationDatasetType datasetType,
        double durationSeconds,
        int totalSimulations
    ) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(reportPath, StandardCharsets.UTF_8))) {
            w.println("# LifeLine Evaluation :: Experiment E5 Statistical Report");
            w.println();
            w.println("**Claim C4**: Fallback re-ranking improves fulfillment on decline / no-response.");
            w.println();
            w.printf(Locale.ROOT, "- **Dataset Type**: %s%n", datasetType);
            w.printf(Locale.ROOT, "- **Total Simulations**: %d%n", totalSimulations);
            w.printf(Locale.ROOT, "- **Duration**: %.2f seconds%n", durationSeconds);
            w.printf(Locale.ROOT, "- **Attempt Budgets Evaluated**: %s%n", config.attemptBudgets());
            w.printf(Locale.ROOT, "- **Systems**: %s%n", config.systems());
            w.printf(Locale.ROOT, "- **Response Models**: %s%n", config.responseModels());
            w.println();

            w.println("## Statistical Comparisons Summary");
            w.println();
            w.println("| Slice | Model | Metric | Candidate Mean | Baseline Mean | HL Shift | Cliff's d | Holm p | Decision |");
            w.println("|---|---|---|---|---|---|---|---|---|");

            for (StatisticalComparison c : comparisons) {
                w.printf(Locale.ROOT, "| %s | %s | %s | %.4f | %.4f | %+.4f | %+.3f | %.6f | **%s** |%n",
                    c.experiment(),
                    c.responseModel(),
                    c.metric(),
                    c.candidateMean(),
                    c.baselineMean(),
                    c.hodgesLehmannShift(),
                    c.cliffsDelta(),
                    c.holmPValue(),
                    c.decision()
                );
            }
        }
    }

    private String computeConfigHash(Path configPath) {
        if (configPath == null || !Files.exists(configPath)) {
            return "unknown";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readAllBytes(configPath));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return "error";
        }
    }

    private String formatManifestJson(
        String experiment,
        String configHash,
        EvaluationDatasetType datasetType,
        List<Integer> attemptBudgets,
        List<String> systems,
        List<String> responseModels,
        List<Long> seeds,
        Instant startTime,
        Instant endTime,
        double durationSeconds,
        int totalSimulations,
        int worldGenerations,
        String csvFile,
        String statSummaryFile
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append(String.format("  \"experiment\": \"%s\",\n", experiment));
        sb.append(String.format("  \"dataset_type\": \"%s\",\n", datasetType));
        sb.append("  \"git_commit\": \"92ce970e486d73242bb56006e3b8b50a9914d497\",\n");
        sb.append(String.format("  \"config_hash\": \"%s\",\n", configHash));

        sb.append("  \"attempt_budgets\": [");
        for (int i = 0; i < attemptBudgets.size(); i++) {
            sb.append(attemptBudgets.get(i));
            if (i < attemptBudgets.size() - 1) sb.append(", ");
        }
        sb.append("],\n");

        sb.append("  \"systems\": [");
        for (int i = 0; i < systems.size(); i++) {
            sb.append(String.format("\"%s\"", systems.get(i)));
            if (i < systems.size() - 1) sb.append(", ");
        }
        sb.append("],\n");

        sb.append("  \"response_models\": [");
        for (int i = 0; i < responseModels.size(); i++) {
            sb.append(String.format("\"%s\"", responseModels.get(i)));
            if (i < responseModels.size() - 1) sb.append(", ");
        }
        sb.append("],\n");

        sb.append(String.format("  \"seed_count\": %d,\n", seeds.size()));
        sb.append("  \"seeds\": [");
        for (int i = 0; i < seeds.size(); i++) {
            sb.append(seeds.get(i));
            if (i < seeds.size() - 1) sb.append(", ");
        }
        sb.append("],\n");

        sb.append(String.format("  \"start_time\": \"%s\",\n", startTime));
        sb.append(String.format("  \"end_time\": \"%s\",\n", endTime));
        sb.append(String.format(Locale.ROOT, "  \"duration_seconds\": %.3f,\n", durationSeconds));
        sb.append(String.format("  \"total_runs\": %d,\n", totalSimulations));
        sb.append(String.format("  \"world_generations\": %d,\n", worldGenerations));
        sb.append(String.format("  \"csv_file\": \"%s\",\n", csvFile));
        sb.append(String.format("  \"statistical_summary_file\": %s\n", statSummaryFile != null ? "\"" + statSummaryFile + "\"" : "null"));
        sb.append("}\n");
        return sb.toString();
    }
}
