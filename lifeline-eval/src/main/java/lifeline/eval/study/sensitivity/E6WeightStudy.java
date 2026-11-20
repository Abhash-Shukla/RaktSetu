package lifeline.eval.study.sensitivity;

import lifeline.core.domain.BloodGroup;
import lifeline.core.domain.BloodRequest;
import lifeline.eval.baseline.BaselineMatcherFactory;
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
 * Execution harness for Experiment E6 (Weight Sensitivity & Urgency Profile Comparison — Claim C2).
 * Operates in two strictly separated stages:
 * 1. Stage 1 (Tuning Phase, seeds 1000+): Evaluates K = 20 bounded uniform perturbations of default urgency weights.
 *    Identifies top-performing and lower-bound configurations and writes machine-readable tuning manifest.
 * 2. Stage 2 (Reporting Phase, seeds 1-30): Evaluates S6_DEFAULT against S6_PERTURBED_BEST, S6_PERTURBED_MIN,
 *    and S6_SINGLE_URGENCY independently across 30 reporting seeds and response models M1-M3.
 */
public final class E6WeightStudy {

    private static final Logger log = LoggerFactory.getLogger(E6WeightStudy.class);

    private final E6Config config;
    private final SimulationParameters simParams;
    private final Map<BloodGroup, Double> basePopulationFrequencies;
    private final AtomicInteger worldGenerationCount = new AtomicInteger(0);

    public E6WeightStudy(E6Config config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.simParams = SimulationParameters.loadDefault();
        this.basePopulationFrequencies = ExperimentRunner.loadPopulationFrequencies();
    }

    public E6StudyResult run() {
        // Validate tuning and reporting seeds are strictly separated
        StatisticalAnalyzer.classifyAndValidateSeeds(new HashSet<>(config.tuningSeeds()));
        EvaluationDatasetType reportingDatasetType = StatisticalAnalyzer.classifyAndValidateSeeds(new HashSet<>(config.reportingSeeds()));
        if (reportingDatasetType != EvaluationDatasetType.FINAL_REPORTING) {
            throw new IllegalArgumentException("E6 reporting seeds must be classified as FINAL_REPORTING (seeds 1-30)");
        }

        Instant startTime = Instant.now();
        Path outDir = Path.of(config.outputDir());

        try {
            Files.createDirectories(outDir);

            // =========================================================================
            // 1. Generate K alternative configurations via master sampling seed
            // =========================================================================
            List<WeightConfiguration> sampledConfigs = E6WeightPerturber.generateConfigurations(
                config.masterSamplingSeed(),
                config.candidateCount()
            );

            // Write machine-readable weight configurations manifest
            Path weightManifestPath = outDir.resolve("weight_configurations.json");
            writeWeightManifestJson(weightManifestPath, sampledConfigs, config.masterSamplingSeed());

            // Register all custom configurations with BaselineMatcherFactory
            for (WeightConfiguration wc : sampledConfigs) {
                BaselineMatcherFactory.registerCustomProfile(wc.id(), wc.toUrgencyProfile());
            }

            // =========================================================================
            // 2. Stage 1: Tuning Phase (Tuning Seeds 1000+)
            // =========================================================================
            log.info("Starting E6 Stage 1 (Tuning Search): {} weight configurations across {} tuning seeds and {} models",
                sampledConfigs.size(), config.tuningSeeds().size(), config.responseModels().size());

            ExecutorService tuningExecutor = Executors.newFixedThreadPool(config.parallelism());
            WorldGenerator worldGen = new WorldGenerator(simParams);
            RequestGenerator reqGen = new RequestGenerator(simParams, basePopulationFrequencies);
            ExperimentRunner helperRunner = new ExperimentRunner(new ExperimentConfig(
                "E6",
                List.of("S6_DEFAULT"),
                config.responseModels(),
                config.tuningSeeds(),
                config.outputDir(),
                config.parallelism()
            ));

            Map<String, List<Double>> fulfillmentByConfig = new HashMap<>();
            Map<String, List<Double>> contactsByConfig = new HashMap<>();

            try {
                for (long seed : config.tuningSeeds()) {
                    World world = worldGen.generate(seed);
                    worldGenerationCount.incrementAndGet();
                    List<String> zones = new ArrayList<>(world.zoneMap().centroids().keySet());
                    List<BloodRequest> requests = reqGen.generateRequests(
                        zones,
                        world.referenceTime(),
                        new SplittableRandom(seed + 100L)
                    );

                    List<Callable<List<TidyMetricRow>>> tuningTasks = new ArrayList<>();
                    for (WeightConfiguration wc : sampledConfigs) {
                        for (String model : config.responseModels()) {
                            tuningTasks.add(() -> helperRunner.executeSingleSimulation(
                                "E6_TUNING",
                                wc.id(),
                                model,
                                seed,
                                world,
                                requests,
                                false,
                                25.0
                            ));
                        }
                    }

                    List<Future<List<TidyMetricRow>>> futures = tuningExecutor.invokeAll(tuningTasks);
                    for (Future<List<TidyMetricRow>> f : futures) {
                        List<TidyMetricRow> rows = f.get();
                        for (TidyMetricRow r : rows) {
                            if (r.metric().equals("fulfillment_within_deadline")) {
                                fulfillmentByConfig.computeIfAbsent(r.system(), k -> new ArrayList<>()).add(r.value());
                            } else if (r.metric().equals("contacts_per_fulfilled_request")) {
                                contactsByConfig.computeIfAbsent(r.system(), k -> new ArrayList<>()).add(r.value());
                            }
                        }
                    }
                }
            } finally {
                tuningExecutor.shutdown();
            }

            // Write Stage 1 Tuning Summary CSV
            Path tuningSummaryCsvPath = outDir.resolve("e6_tuning_summary.csv");
            WeightConfiguration bestTuned = null;
            WeightConfiguration worstTuned = null;
            double bestFulfill = -1.0;
            double worstFulfill = Double.MAX_VALUE;

            try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(tuningSummaryCsvPath, StandardCharsets.UTF_8))) {
                pw.println("config_id,description,mean_fulfillment,mean_contacts");
                for (WeightConfiguration wc : sampledConfigs) {
                    List<Double> fList = fulfillmentByConfig.getOrDefault(wc.id(), List.of(0.0));
                    List<Double> cList = contactsByConfig.getOrDefault(wc.id(), List.of(0.0));
                    double meanF = fList.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    double meanC = cList.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    pw.printf(Locale.ROOT, "%s,\"%s\",%.4f,%.2f%n", wc.id(), wc.description(), meanF, meanC);

                    // Track best and worst among alternative configurations (excluding baseline S6_DEFAULT)
                    if (!wc.id().equals("S6_DEFAULT")) {
                        if (meanF > bestFulfill) {
                            bestFulfill = meanF;
                            bestTuned = wc;
                        }
                        if (meanF < worstFulfill) {
                            worstFulfill = meanF;
                            worstTuned = wc;
                        }
                    }
                }
            }

            if (bestTuned == null) bestTuned = sampledConfigs.get(1);
            if (worstTuned == null) worstTuned = sampledConfigs.get(sampledConfigs.size() - 1);

            log.info("E6 Tuning complete: selected S6_PERTURBED_BEST={} (fulfillment={}), S6_PERTURBED_MIN={} (fulfillment={})",
                bestTuned.id(), String.format(Locale.ROOT, "%.4f", bestFulfill),
                worstTuned.id(), String.format(Locale.ROOT, "%.4f", worstFulfill));

            // Register selected best and worst profiles for reporting phase
            BaselineMatcherFactory.registerCustomProfile("S6_PERTURBED_BEST", bestTuned.toUrgencyProfile());
            BaselineMatcherFactory.registerCustomProfile("S6_PERTURBED_MIN", worstTuned.toUrgencyProfile());

            // =========================================================================
            // 3. Stage 2: Reporting Phase (Reporting Seeds 1-30)
            // =========================================================================
            List<String> reportingSystems = List.of("S6_DEFAULT", "S6_PERTURBED_BEST", "S6_PERTURBED_MIN", "S6_SINGLE_URGENCY");
            log.info("Starting E6 Stage 2 (Reporting Evaluation): {} systems across {} reporting seeds and {} models",
                reportingSystems.size(), config.reportingSeeds().size(), config.responseModels().size());

            ExecutorService reportingExecutor = Executors.newFixedThreadPool(config.parallelism());
            List<TidyMetricRow> reportingRows = new ArrayList<>();

            try {
                for (long seed : config.reportingSeeds()) {
                    World world = worldGen.generate(seed);
                    worldGenerationCount.incrementAndGet();
                    List<String> zones = new ArrayList<>(world.zoneMap().centroids().keySet());
                    List<BloodRequest> requests = reqGen.generateRequests(
                        zones,
                        world.referenceTime(),
                        new SplittableRandom(seed + 100L)
                    );

                    List<Callable<List<TidyMetricRow>>> reportingTasks = new ArrayList<>();
                    for (String system : reportingSystems) {
                        for (String model : config.responseModels()) {
                            reportingTasks.add(() -> helperRunner.executeSingleSimulation(
                                "E6",
                                system,
                                model,
                                seed,
                                world,
                                requests,
                                false,
                                25.0
                            ));
                        }
                    }

                    List<Future<List<TidyMetricRow>>> futures = reportingExecutor.invokeAll(reportingTasks);
                    for (Future<List<TidyMetricRow>> f : futures) {
                        reportingRows.addAll(f.get());
                    }
                }
            } finally {
                reportingExecutor.shutdown();
            }

            // Sort reporting rows deterministically
            reportingRows.sort(Comparator
                .comparing(TidyMetricRow::experiment)
                .thenComparing(TidyMetricRow::system)
                .thenComparing(TidyMetricRow::responseModel)
                .thenComparingLong(TidyMetricRow::seed)
                .thenComparing(TidyMetricRow::metric)
            );

            // Write e6_results.csv
            Path reportingCsvPath = outDir.resolve("e6_results.csv");
            try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(reportingCsvPath, StandardCharsets.UTF_8))) {
                pw.println(TidyMetricRow.csvHeader());
                for (TidyMetricRow r : reportingRows) {
                    pw.println(r.toCsvLine());
                }
            }

            // Perform statistical analysis for S6_DEFAULT vs baselines across reporting seeds
            StatisticalAnalyzer analyzer = StatisticalAnalyzer.forExperiment("E6");
            List<StatisticalComparison> comparisons = analyzer.analyze(reportingRows);

            // Write e6_statistical_summary.csv
            Path statCsvPath = outDir.resolve("e6_statistical_summary.csv");
            try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(statCsvPath, StandardCharsets.UTF_8))) {
                pw.println(StatisticalComparison.csvHeader());
                for (StatisticalComparison sc : comparisons) {
                    pw.println(sc.toCsvLine());
                }
            }

            // Write e6_statistical_report.md
            Instant endTime = Instant.now();
            double durationSeconds = java.time.Duration.between(startTime, endTime).toMillis() / 1000.0;
            Path statReportPath = outDir.resolve("e6_statistical_report.md");
            writeStatisticalReport(statReportPath, comparisons, durationSeconds, reportingRows.size(), bestTuned, worstTuned);

            // Write run_manifest.json
            Path manifestPath = outDir.resolve("run_manifest.json");
            String configHash = computeConfigHash(config.configPath());
            int totalSims = (sampledConfigs.size() * config.tuningSeeds().size() * config.responseModels().size())
                + (reportingSystems.size() * config.reportingSeeds().size() * config.responseModels().size());

            String manifestJson = formatManifestJson(
                config.experiment(),
                configHash,
                reportingDatasetType,
                config.masterSamplingSeed(),
                config.candidateCount(),
                reportingSystems,
                config.responseModels(),
                config.reportingSeeds(),
                bestTuned.id(),
                worstTuned.id(),
                startTime,
                endTime,
                durationSeconds,
                totalSims,
                worldGenerationCount.get(),
                "e6_results.csv",
                "e6_statistical_summary.csv"
            );
            Files.writeString(manifestPath, manifestJson, StandardCharsets.UTF_8);

            log.info("E6 Study complete: duration={}s, simulations={}, output={}",
                String.format(Locale.ROOT, "%.2f", durationSeconds), totalSims, outDir);

            return new E6StudyResult(
                reportingCsvPath,
                tuningSummaryCsvPath,
                manifestPath,
                statCsvPath,
                statReportPath,
                reportingRows.size(),
                totalSims,
                durationSeconds,
                reportingRows,
                comparisons
            );
        } catch (Exception e) {
            throw new RuntimeException("E6 weight sensitivity study execution failed", e);
        }
    }

    private void writeWeightManifestJson(Path path, List<WeightConfiguration> configs, long masterSeed) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append(String.format("  \"experiment\": \"E6\",\n"));
        sb.append(String.format("  \"master_sampling_seed\": %d,\n", masterSeed));
        sb.append(String.format("  \"perturbation_bound\": %.2f,\n", E6WeightPerturber.PERTURBATION_BOUND));
        sb.append(String.format("  \"candidate_count\": %d,\n", configs.size() - 1));
        sb.append("  \"configurations\": [\n");
        for (int i = 0; i < configs.size(); i++) {
            sb.append(configs.get(i).toJson());
            if (i < configs.size() - 1) sb.append(",\n");
            else sb.append("\n");
        }
        sb.append("  ]\n");
        sb.append("}\n");
        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }

    private void writeStatisticalReport(
        Path reportPath,
        List<StatisticalComparison> comparisons,
        double durationSeconds,
        int totalRows,
        WeightConfiguration bestTuned,
        WeightConfiguration worstTuned
    ) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(reportPath, StandardCharsets.UTF_8))) {
            w.println("# LifeLine Evaluation :: Experiment E6 Statistical Report");
            w.println();
            w.println("**Claim C2**: Weight sensitivity and urgency-profile evaluation.");
            w.println();
            w.println("### Tuning Phase Selection Summary");
            w.printf("- **Tuned Top Configuration**: `%s` (%s)%n", bestTuned.id(), bestTuned.description());
            w.printf("- **Sensitivity Lower Bound Configuration**: `%s` (%s)%n", worstTuned.id(), worstTuned.description());
            w.printf("- **Duration**: %.2f seconds%n", durationSeconds);
            w.printf("- **Reporting Rows**: %d%n", totalRows);
            w.println();

            w.println("### Reporting Phase Comparisons Summary (Seeds 1-30)");
            w.println();
            w.println("| Slice | Model | Baseline | Metric | Candidate Mean | Baseline Mean | HL Shift | Cliff's d | Holm p | Decision |");
            w.println("|---|---|---|---|---|---|---|---|---|---|");

            for (StatisticalComparison c : comparisons) {
                w.printf(Locale.ROOT, "| %s | %s | %s | %s | %.4f | %.4f | %+.4f | %+.3f | %.6f | **%s** |%n",
                    c.experiment(),
                    c.responseModel(),
                    c.baselineSystem(),
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
        long masterSamplingSeed,
        int candidateCount,
        List<String> systems,
        List<String> responseModels,
        List<Long> reportingSeeds,
        String bestTunedId,
        String worstTunedId,
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
        sb.append(String.format("  \"master_sampling_seed\": %d,\n", masterSamplingSeed));
        sb.append(String.format("  \"candidate_count\": %d,\n", candidateCount));
        sb.append(String.format("  \"best_tuned_configuration\": \"%s\",\n", bestTunedId));
        sb.append(String.format("  \"worst_tuned_configuration\": \"%s\",\n", worstTunedId));

        sb.append("  \"reporting_systems\": [");
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

        sb.append(String.format("  \"reporting_seed_count\": %d,\n", reportingSeeds.size()));
        sb.append(String.format("  \"start_time\": \"%s\",\n", startTime));
        sb.append(String.format("  \"end_time\": \"%s\",\n", endTime));
        sb.append(String.format(Locale.ROOT, "  \"duration_seconds\": %.3f,\n", durationSeconds));
        sb.append(String.format("  \"total_simulations\": %d,\n", totalSimulations));
        sb.append(String.format("  \"world_generations\": %d,\n", worldGenerations));
        sb.append(String.format("  \"reporting_csv_file\": \"%s\",\n", csvFile));
        sb.append(String.format("  \"statistical_summary_file\": %s\n", statSummaryFile != null ? "\"" + statSummaryFile + "\"" : "null"));
        sb.append("}\n");
        return sb.toString();
    }
}

