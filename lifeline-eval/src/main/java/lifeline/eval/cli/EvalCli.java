package lifeline.eval.cli;

import lifeline.eval.metric.TidyMetricRow;
import lifeline.eval.runner.ExperimentConfig;
import lifeline.eval.runner.ExperimentRunResult;
import lifeline.eval.runner.ExperimentRunner;
import lifeline.eval.stats.EvaluationDatasetType;
import lifeline.eval.stats.StatisticalAnalyzer;
import lifeline.eval.stats.StatisticalComparison;
import lifeline.eval.study.explanation.E7Config;
import lifeline.eval.study.explanation.E7ExplanationStudy;
import lifeline.eval.study.explanation.E7StudyResult;
import lifeline.eval.study.fallback.E5Config;
import lifeline.eval.study.fallback.E5FallbackStudy;
import lifeline.eval.study.fallback.E5StudyResult;
import lifeline.eval.study.inventory.E4Config;
import lifeline.eval.study.inventory.E4InventoryStudy;
import lifeline.eval.study.inventory.E4StudyResult;
import lifeline.eval.study.sensitivity.E6Config;
import lifeline.eval.study.sensitivity.E6StudyResult;
import lifeline.eval.study.sensitivity.E6WeightStudy;
import lifeline.eval.study.scalability.E8ScalabilityStudy;
import lifeline.eval.study.scalability.ScalabilityBenchmarkResult;
import lifeline.eval.study.scalability.ScalabilityConfig;
import lifeline.eval.study.scarcity.E3Config;
import lifeline.eval.study.scarcity.E3ScarcityStudy;
import lifeline.eval.study.scarcity.E3StudyResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Command-line interface for running evaluation experiments and statistical analysis.
 * <p>
 * Usage:
 * <pre>
 *   java -jar lifeline-eval.jar run E1 --config configs/e1.yaml
 *   java -jar lifeline-eval.jar analyze results/e1_results.csv --output-dir results
 * </pre>
 */
public final class EvalCli {

    private EvalCli() {
    }

    public static void main(String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("--help") || args[0].equalsIgnoreCase("-h")) {
            printHelp();
            return;
        }

        String command = args[0].toLowerCase();
        switch (command) {
            case "run" -> handleRun(args);
            case "analyze" -> handleAnalyze(args);
            case "export-demo-data" -> handleExportDemoData(args);
            default -> {
                System.err.println("Unknown command: " + command);
                printHelp();
                System.exit(1);
            }
        }
    }

    private static void handleRun(String[] args) {
        if (args.length < 2) {
            System.err.println("Error: Missing experiment identifier (e.g. E1)");
            printHelp();
            System.exit(1);
        }

        String experiment = args[1].toUpperCase();
        Path configPath = null;
        List<Long> explicitSeeds = null;
        String outputDir = null;

        for (int i = 2; i < args.length; i++) {
            if ("--config".equals(args[i]) && i + 1 < args.length) {
                configPath = Path.of(args[++i]);
            } else if ("--seeds".equals(args[i]) && i + 1 < args.length) {
                String[] parts = args[++i].split(",");
                explicitSeeds = new ArrayList<>();
                for (String p : parts) {
                    explicitSeeds.add(Long.parseLong(p.trim()));
                }
            } else if ("--output-dir".equals(args[i]) && i + 1 < args.length) {
                outputDir = args[++i];
            }
        }

        if ("E3".equals(experiment)) {
            handleRunE3(configPath, explicitSeeds, outputDir);
            return;
        }

        if ("E4".equals(experiment)) {
            handleRunE4(configPath, explicitSeeds, outputDir);
            return;
        }

        if ("E5".equals(experiment)) {
            handleRunE5(configPath, explicitSeeds, outputDir);
            return;
        }

        if ("E6".equals(experiment)) {
            handleRunE6(configPath, outputDir);
            return;
        }

        if ("E7".equals(experiment)) {
            handleRunE7(configPath, explicitSeeds, outputDir);
            return;
        }

        if ("E8".equals(experiment)) {
            handleRunE8(configPath, outputDir);
            return;
        }

        // Fallback default config path if not provided
        if (configPath == null) {
            Path defaultPath = Path.of("configs", experiment.toLowerCase() + ".yaml");
            if (Files.exists(defaultPath)) {
                configPath = defaultPath;
            }
        }

        ExperimentConfig config;
        if (configPath != null && Files.exists(configPath)) {
            config = ExperimentConfig.load(configPath);
            if (explicitSeeds != null) {
                config = new ExperimentConfig(
                    config.experiment(),
                    config.systems(),
                    config.responseModels(),
                    explicitSeeds,
                    outputDir != null ? outputDir : config.outputDir(),
                    config.parallelism()
                );
            }
        } else {
            config = new ExperimentConfig(
                experiment,
                List.of("S0", "S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8"),
                List.of("M1", "M2", "M3"),
                explicitSeeds != null ? explicitSeeds : ExperimentConfig.defaultReportingSeeds(),
                outputDir != null ? outputDir : "results",
                Runtime.getRuntime().availableProcessors()
            );
        }

        EvaluationDatasetType datasetType = StatisticalAnalyzer.classifyAndValidateSeeds(new HashSet<>(config.seeds()));

        System.out.println("================================================================================");
        System.out.printf("  LifeLine Evaluation Runner :: %s%n", config.experiment());
        System.out.printf("  Dataset Status       : %s%n", datasetType.description());
        System.out.printf("  Systems (%d)         : %s%n", config.systems().size(), config.systems());
        System.out.printf("  Response Models (%d) : %s%n", config.responseModels().size(), config.responseModels());
        System.out.printf("  Seeds (%d)           : %s%n", config.seeds().size(), config.seeds());
        System.out.printf("  Output Directory    : %s%n", config.outputDir());
        System.out.println("================================================================================");

        ExperimentRunner runner = new ExperimentRunner(config, configPath);
        ExperimentRunResult result = runner.execute();

        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("Execution completed in %.2f seconds (world generations: %d).%n",
            result.durationSeconds(), result.worldGenerations()
        );
        System.out.printf("Artifacts written:%n");
        System.out.printf("  - Tidy CSV   : %s (%d metric records)%n", result.csvPath(), result.totalMetricRows());
        System.out.printf("  - Manifest   : %s%n", result.manifestPath());
        if (result.statisticalSummaryPath() != null) {
            System.out.printf("  - Stat CSV   : %s%n", result.statisticalSummaryPath());
        }
        if (result.statisticalReportPath() != null) {
            System.out.printf("  - Stat Report: %s%n", result.statisticalReportPath());
        }
        if (result.statisticalSummaryPath() == null) {
            System.out.printf("  Note: Inferential statistical analysis skipped (seeds=%d < minimum %d required for paired testing).%n",
                config.seeds().size(), StatisticalAnalyzer.MIN_PAIRED_SEEDS_FOR_INFERENCE);
        }
        if (datasetType == EvaluationDatasetType.EXPLORATORY_TUNING) {
            System.out.printf("  Note: Results generated from tuning seeds (exploratory only, not final research findings).%n");
        } else if (datasetType == EvaluationDatasetType.INCOMPLETE_REPORTING) {
            System.out.printf("  Note: Incomplete reporting seed set (%d/30 seeds). Final research conclusions require all 30 reporting seeds.%n",
                config.seeds().size());
        }
        System.out.println("================================================================================");
    }


    private static void handleRunE3(Path configPath, List<Long> explicitSeeds, String outputDir) {
        if (configPath == null) {
            Path defaultPath = Path.of("configs", "e3.yaml");
            if (Files.exists(defaultPath)) {
                configPath = defaultPath;
            }
        }

        E3Config config;
        if (configPath != null && Files.exists(configPath)) {
            config = E3Config.load(configPath);
            if (explicitSeeds != null || outputDir != null) {
                config = new E3Config(
                    config.scarcityMultipliers(),
                    config.systems(),
                    config.responseModels(),
                    explicitSeeds != null ? explicitSeeds : config.seeds(),
                    outputDir != null ? outputDir : config.outputDir(),
                    config.parallelism(),
                    configPath
                );
            }
        } else {
            config = new E3Config(
                E3Config.DEFAULT_SCARCITY_MULTIPLIERS,
                E3Config.DEFAULT_SYSTEMS,
                E3Config.DEFAULT_RESPONSE_MODELS,
                explicitSeeds != null ? explicitSeeds : E3Config.defaultReportingSeeds(),
                outputDir != null ? outputDir : E3Config.DEFAULT_OUTPUT_DIR,
                Math.max(1, Runtime.getRuntime().availableProcessors()),
                configPath
            );
        }

        EvaluationDatasetType datasetType = StatisticalAnalyzer.classifyAndValidateSeeds(new HashSet<>(config.seeds()));

        System.out.println("================================================================================");
        System.out.println("LifeLine Evaluation :: Experiment E3 — Scarcity Sweep Study");
        System.out.println("================================================================================");
        System.out.printf("Dataset Type          : %s%n", datasetType);
        System.out.printf("Scarcity Multipliers  : %s%n", config.scarcityMultipliers());
        System.out.printf("Systems Evaluated     : %s%n", config.systems());
        System.out.printf("Response Models       : %s%n", config.responseModels());
        System.out.printf("Seeds (%d total)      : %s%n", config.seeds().size(), config.seeds());
        System.out.printf("Output Directory      : %s%n", config.outputDir());
        System.out.printf("Parallelism           : %d threads%n", config.parallelism());
        System.out.println("--------------------------------------------------------------------------------");

        E3ScarcityStudy study = new E3ScarcityStudy(config);
        E3StudyResult result = study.run();

        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("E3 execution completed in %.2fs (%d simulations, %d world generations)%n",
            result.durationSeconds(), result.totalSimulations(), result.worldGenerations());
        System.out.printf("Artifacts written:%n");
        System.out.printf("  - Tidy CSV   : %s (%d metric records)%n", result.csvPath(), result.totalRows());
        System.out.printf("  - Manifest   : %s%n", result.manifestPath());
        if (result.statCsvPath() != null) {
            System.out.printf("  - Stat CSV   : %s%n", result.statCsvPath());
        }
        if (result.statReportPath() != null) {
            System.out.printf("  - Stat Report: %s%n", result.statReportPath());
        }
        System.out.println("================================================================================");
    }

    private static void handleRunE4(Path configPath, List<Long> explicitSeeds, String outputDir) {
        if (configPath == null) {
            Path defaultPath = Path.of("configs", "e4.yaml");
            if (Files.exists(defaultPath)) {
                configPath = defaultPath;
            }
        }

        E4Config config;
        if (configPath != null && Files.exists(configPath)) {
            try {
                config = E4Config.load(configPath);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load E4 config from " + configPath, e);
            }
            if (explicitSeeds != null || outputDir != null) {
                config = new E4Config(
                    config.experiment(),
                    config.inventoryLevels(),
                    config.systems(),
                    config.responseModels(),
                    explicitSeeds != null ? explicitSeeds : config.seeds(),
                    outputDir != null ? outputDir : config.outputDir(),
                    config.parallelism(),
                    config.tier0RadiusKm(),
                    configPath
                );
            }
        } else {
            config = new E4Config(
                "E4",
                E4Config.DEFAULT_INVENTORY_LEVELS,
                E4Config.DEFAULT_SYSTEMS,
                E4Config.DEFAULT_RESPONSE_MODELS,
                explicitSeeds != null ? explicitSeeds : List.of(1L),
                outputDir != null ? outputDir : E4Config.DEFAULT_OUTPUT_DIR,
                Math.max(1, Runtime.getRuntime().availableProcessors()),
                E4Config.DEFAULT_TIER0_RADIUS_KM,
                configPath
            );
        }

        EvaluationDatasetType datasetType = StatisticalAnalyzer.classifyAndValidateSeeds(new HashSet<>(config.seeds()));

        System.out.println("================================================================================");
        System.out.println("LifeLine Evaluation :: Experiment E4 — Inventory-Level Sweep Study (Claim C1)");
        System.out.println("================================================================================");
        System.out.printf("Dataset Type          : %s%n", datasetType);
        System.out.printf("Inventory Levels      : %s%n", config.inventoryLevels());
        System.out.printf("Systems Evaluated     : %s%n", config.systems());
        System.out.printf("Response Models       : %s%n", config.responseModels());
        System.out.printf("Seeds (%d total)      : %s%n", config.seeds().size(), config.seeds());
        System.out.printf("Tier-0 Search Radius  : %.1f km%n", config.tier0RadiusKm());
        System.out.printf("Output Directory      : %s%n", config.outputDir());
        System.out.printf("Parallelism           : %d threads%n", config.parallelism());
        System.out.println("--------------------------------------------------------------------------------");

        E4InventoryStudy study = new E4InventoryStudy(config);
        E4StudyResult result = study.run();

        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("E4 execution completed in %.2fs (%d simulations, %d world generations)%n",
            result.durationSeconds(), result.totalSimulations(), result.worldGenerations());
        System.out.printf("Artifacts written:%n");
        System.out.printf("  - Tidy CSV   : %s (%d metric records)%n", result.csvPath(), result.totalRows());
        System.out.printf("  - Manifest   : %s%n", result.manifestPath());
        if (result.statCsvPath() != null) {
            System.out.printf("  - Stat CSV   : %s%n", result.statCsvPath());
        }
        if (result.statReportPath() != null) {
            System.out.printf("  - Stat Report: %s%n", result.statReportPath());
        }
        System.out.println("================================================================================");
    }

    private static void handleRunE5(Path configPath, List<Long> explicitSeeds, String outputDir) {
        if (configPath == null) {
            Path defaultPath = Path.of("configs", "e5.yaml");
            if (Files.exists(defaultPath)) {
                configPath = defaultPath;
            }
        }

        E5Config config;
        if (configPath != null && Files.exists(configPath)) {
            try {
                config = E5Config.load(configPath);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load E5 config from " + configPath, e);
            }
            if (explicitSeeds != null || outputDir != null) {
                config = new E5Config(
                    config.experiment(),
                    config.attemptBudgets(),
                    config.systems(),
                    config.responseModels(),
                    explicitSeeds != null ? explicitSeeds : config.seeds(),
                    outputDir != null ? outputDir : config.outputDir(),
                    config.parallelism(),
                    configPath
                );
            }
        } else {
            config = new E5Config(
                "E5",
                E5Config.DEFAULT_ATTEMPT_BUDGETS,
                E5Config.DEFAULT_SYSTEMS,
                E5Config.DEFAULT_RESPONSE_MODELS,
                explicitSeeds != null ? explicitSeeds : List.of(1L),
                outputDir != null ? outputDir : E5Config.DEFAULT_OUTPUT_DIR,
                Math.max(1, Runtime.getRuntime().availableProcessors()),
                configPath
            );
        }

        EvaluationDatasetType datasetType = StatisticalAnalyzer.classifyAndValidateSeeds(new HashSet<>(config.seeds()));

        System.out.println("================================================================================");
        System.out.println("LifeLine Evaluation :: Experiment E5 — Fallback Re-ranking Study (Claim C4)");
        System.out.println("================================================================================");
        System.out.printf("Dataset Type          : %s%n", datasetType);
        System.out.printf("Attempt Budgets       : %s%n", config.attemptBudgets());
        System.out.printf("Systems Evaluated     : %s%n", config.systems());
        System.out.printf("Response Models       : %s%n", config.responseModels());
        System.out.printf("Seeds (%d total)      : %s%n", config.seeds().size(), config.seeds());
        System.out.printf("Output Directory      : %s%n", config.outputDir());
        System.out.printf("Parallelism           : %d threads%n", config.parallelism());
        System.out.println("--------------------------------------------------------------------------------");

        E5FallbackStudy study = new E5FallbackStudy(config);
        E5StudyResult result = study.run();

        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("E5 execution completed in %.2fs (%d simulations, %d world generations)%n",
            result.durationSeconds(), result.totalSimulations(), result.worldGenerations());
        System.out.printf("Artifacts written:%n");
        System.out.printf("  - Tidy CSV   : %s (%d metric records)%n", result.csvPath(), result.totalRows());
        System.out.printf("  - Manifest   : %s%n", result.manifestPath());
        if (result.statCsvPath() != null) {
            System.out.printf("  - Stat CSV   : %s%n", result.statCsvPath());
        }
        if (result.statReportPath() != null) {
            System.out.printf("  - Stat Report: %s%n", result.statReportPath());
        }
        System.out.println("================================================================================");
    }

    private static void handleRunE6(Path configPath, String outputDir) {
        if (configPath == null) {
            Path defaultPath = Path.of("configs", "e6.yaml");
            if (Files.exists(defaultPath)) {
                configPath = defaultPath;
            }
        }

        E6Config config;
        if (configPath != null && Files.exists(configPath)) {
            try {
                config = E6Config.load(configPath);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load E6 config from " + configPath, e);
            }
            if (outputDir != null) {
                config = new E6Config(
                    config.experiment(),
                    config.candidateCount(),
                    config.masterSamplingSeed(),
                    config.responseModels(),
                    config.tuningSeeds(),
                    config.reportingSeeds(),
                    outputDir,
                    config.parallelism(),
                    configPath
                );
            }
        } else {
            throw new IllegalArgumentException("E6 configuration file required: configs/e6.yaml");
        }

        System.out.println("================================================================================");
        System.out.println("LifeLine Evaluation :: Experiment E6 — Weight Sensitivity Study (Claim C2)");
        System.out.println("================================================================================");
        System.out.printf("Candidate Count       : %d sampled alternative profiles%n", config.candidateCount());
        System.out.printf("Master Sampling Seed  : %d%n", config.masterSamplingSeed());
        System.out.printf("Response Models       : %s%n", config.responseModels());
        System.out.printf("Tuning Seeds (%d)     : %s%n", config.tuningSeeds().size(), config.tuningSeeds());
        System.out.printf("Reporting Seeds (%d)  : %s%n", config.reportingSeeds().size(), config.reportingSeeds());
        System.out.printf("Output Directory      : %s%n", config.outputDir());
        System.out.printf("Parallelism           : %d threads%n", config.parallelism());
        System.out.println("--------------------------------------------------------------------------------");

        E6WeightStudy study = new E6WeightStudy(config);
        E6StudyResult result = study.run();

        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("E6 execution completed in %.2fs (%d simulations)%n",
            result.durationSeconds(), result.totalSimulations());
        System.out.printf("Artifacts written:%n");
        System.out.printf("  - Tuning CSV : %s%n", result.tuningSummaryCsvPath());
        System.out.printf("  - Tidy CSV   : %s (%d metric records)%n", result.reportingCsvPath(), result.totalReportingRows());
        System.out.printf("  - Manifest   : %s%n", result.manifestPath());
        if (result.statCsvPath() != null) {
            System.out.printf("  - Stat CSV   : %s%n", result.statCsvPath());
        }
        if (result.statReportPath() != null) {
            System.out.printf("  - Stat Report: %s%n", result.statReportPath());
        }
        System.out.println("================================================================================");
    }

    private static void handleRunE7(Path configPath, List<Long> explicitSeeds, String outputDir) {
        if (configPath == null) {
            Path defaultPath = Path.of("configs", "e7.yaml");
            if (Files.exists(defaultPath)) {
                configPath = defaultPath;
            }
        }

        E7Config config;
        if (configPath != null && Files.exists(configPath)) {
            config = E7Config.load(configPath);
            if (explicitSeeds != null || outputDir != null) {
                config = new E7Config(
                    explicitSeeds != null ? explicitSeeds : config.seeds(),
                    config.numericalTolerance(),
                    outputDir != null ? outputDir : config.outputDir(),
                    config.topN(),
                    config.includeTargetedCoverageCases()
                );
            }
        } else {
            config = new E7Config(
                explicitSeeds != null ? explicitSeeds : List.of(1L, 2L, 3L, 4L, 5L),
                E7Config.DEFAULT_TOLERANCE,
                outputDir != null ? outputDir : E7Config.DEFAULT_OUTPUT_DIR,
                E7Config.DEFAULT_TOP_N,
                true
            );
        }

        System.out.println("================================================================================");
        System.out.println("  LifeLine Evaluation Runner :: E7 Explanation Faithfulness Study (Claim C5)");
        System.out.printf("  Seeds (%d)           : %s%n", config.seeds().size(), config.seeds());
        System.out.printf("  Numerical Tolerance  : %.2e%n", config.numericalTolerance());
        System.out.printf("  Output Directory     : %s%n", config.outputDir());
        System.out.println("================================================================================");

        E7ExplanationStudy study = new E7ExplanationStudy();
        E7StudyResult result = study.run(config);
        Path outPath = Path.of(config.outputDir());
        study.exportResults(result, outPath);

        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("E7 Study Completed across %d seeds.%n", config.seeds().size());
        System.out.printf("  - Additivity Violations        : %d%n", result.additivityResult().violationCount());
        System.out.printf("  - Counterfactual Top-1 Flips   : %d / %d (%.2f%%)%n",
            result.counterfactualResult().top1FlipNumerator(),
            result.counterfactualResult().top1FlipDenominator(),
            result.counterfactualResult().top1FlipRate() * 100.0);
        System.out.printf("  - Reason Code Coverage         : %d / %d (%.1f%%)%n",
            result.coverageResult().coveredCodes().size(),
            result.coverageResult().totalCatalogCodes(),
            result.coverageResult().coverageRate() * 100.0);
        System.out.printf("Artifacts written to %s:%n", outPath.toAbsolutePath());
        System.out.printf("  - Additivity CSV     : %s%n", outPath.resolve("e7_additivity.csv"));
        System.out.printf("  - Counterfactual CSV : %s%n", outPath.resolve("e7_counterfactual.csv"));
        System.out.printf("  - Reason Codes CSV   : %s%n", outPath.resolve("e7_reason_codes.csv"));
        System.out.printf("  - Summary JSON       : %s%n", outPath.resolve("e7_summary.json"));
        System.out.printf("  - Sample Explanation : %s%n", outPath.resolve("sample_explanation.md"));
        System.out.println("================================================================================");
    }

    private static void handleRunE8(Path configPath, String outputDir) {
        if (configPath == null) {
            Path defaultPath = Path.of("configs", "e8.yaml");
            if (Files.exists(defaultPath)) {
                configPath = defaultPath;
            }
        }

        ScalabilityConfig config;
        if (configPath != null && Files.exists(configPath)) {
            config = ScalabilityConfig.load(configPath);
            if (outputDir != null) {
                config = new ScalabilityConfig(
                    config.donorPoolSizes(),
                    config.concurrencyLevels(),
                    config.warmupIterations(),
                    config.measuredIterations(),
                    config.operationsPerConcurrency(),
                    config.topN(),
                    config.seed(),
                    outputDir
                );
            }
        } else {
            config = outputDir != null
                ? new ScalabilityConfig(
                    ScalabilityConfig.REQUIRED_POOL_SIZES,
                    ScalabilityConfig.REQUIRED_CONCURRENCY_LEVELS,
                    ScalabilityConfig.DEFAULT_WARMUP,
                    ScalabilityConfig.DEFAULT_MEASURED,
                    ScalabilityConfig.DEFAULT_TOP_N,
                    ScalabilityConfig.DEFAULT_SEED,
                    outputDir
                )
                : ScalabilityConfig.defaultConfig();
        }

        System.out.println("================================================================================");
        System.out.println("  LifeLine Evaluation Runner :: E8 Engine Scalability Benchmark (Claim C7)");
        System.out.printf("  Pool Sizes           : %s%n", config.donorPoolSizes());
        System.out.printf("  Concurrency Levels   : %s%n", config.concurrencyLevels());
        if (!config.operationsPerConcurrency().isEmpty()) {
            System.out.printf("  Operations per Level : %s (warmup=%d)%n", config.operationsPerConcurrency(), config.warmupIterations());
        } else {
            System.out.printf("  Iterations per cell  : %d (warmup=%d)%n", config.measuredIterations(), config.warmupIterations());
        }
        System.out.printf("  Output Directory     : %s%n", config.outputDir());
        System.out.println("================================================================================");

        E8ScalabilityStudy study = new E8ScalabilityStudy();
        ScalabilityBenchmarkResult result = study.run(config);
        Path outPath = Path.of(config.outputDir());
        study.exportResults(result, outPath);

        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("E8 Benchmark Completed across %d grid cells.%n", result.cells().size());
        System.out.printf("Artifacts written to %s:%n", outPath.toAbsolutePath());
        System.out.printf("  - Scalability CSV    : %s%n", outPath.resolve("e8_scalability.csv"));
        System.out.printf("  - Scalability JSON   : %s%n", outPath.resolve("e8_scalability.json"));
        System.out.printf("  - Markdown Report    : %s%n", outPath.resolve("e8_benchmark_report.md"));
        System.out.println("================================================================================");
    }

    private static void handleAnalyze(String[] args) {
        if (args.length < 2) {
            System.err.println("Error: Missing CSV path to analyze");
            printHelp();
            System.exit(1);
        }

        Path csvPath = Path.of(args[1]);
        if (!Files.exists(csvPath)) {
            System.err.println("Error: CSV file not found: " + csvPath);
            System.exit(1);
        }

        String outputDir = null;
        for (int i = 2; i < args.length; i++) {
            if ("--output-dir".equals(args[i]) && i + 1 < args.length) {
                outputDir = args[++i];
            }
        }

        Path outDir = outputDir != null ? Path.of(outputDir) : csvPath.getParent();
        if (outDir == null) {
            outDir = Path.of(".");
        }

        try {
            List<TidyMetricRow> rows = StatisticalAnalyzer.loadTidyCsv(csvPath);
            Set<Long> seedSet = new HashSet<>();
            for (TidyMetricRow r : rows) {
                seedSet.add(r.seed());
            }
            EvaluationDatasetType datasetType = StatisticalAnalyzer.classifyAndValidateSeeds(seedSet);

            System.out.println("================================================================================");
            System.out.printf("  LifeLine Statistical Analyzer%n");
            System.out.printf("  Input CSV        : %s%n", csvPath);
            System.out.printf("  Dataset Status   : %s%n", datasetType.description());
            System.out.printf("  Output Directory : %s%n", outDir);
            System.out.println("================================================================================");

            StatisticalAnalyzer analyzer = new StatisticalAnalyzer();
            List<StatisticalComparison> comparisons = analyzer.analyze(rows);

            boolean hasInsufficient = comparisons.stream()
                .anyMatch(sc -> sc.decision() == lifeline.eval.stats.ComparisonDecision.INSUFFICIENT_DATA);
            if (hasInsufficient) {
                System.out.printf("Warning: Dataset contains comparisons with fewer than %d paired seeds.%n" +
                    "         Inferential tests skipped and marked as INSUFFICIENT_DATA.%n",
                    StatisticalAnalyzer.MIN_PAIRED_SEEDS_FOR_INFERENCE);
            }

            Files.createDirectories(outDir);
            Path statCsv = outDir.resolve("statistical_summary.csv");
            Path statReport = outDir.resolve("statistical_report.md");

            analyzer.writeCsv(comparisons, statCsv);
            Files.writeString(statReport, analyzer.generateMarkdownReport(comparisons));

            System.out.printf("Generated %d statistical comparisons:%n", comparisons.size());
            System.out.printf("  - Summary CSV    : %s%n", statCsv);
            System.out.printf("  - Markdown Report: %s%n", statReport);
            if (datasetType == EvaluationDatasetType.EXPLORATORY_TUNING) {
                System.out.printf("  Note: Results generated from tuning seeds (exploratory only, not final research findings).%n");
            } else if (datasetType == EvaluationDatasetType.INCOMPLETE_REPORTING) {
                System.out.printf("  Note: Incomplete reporting seed set (%d/30 seeds). Final research conclusions require all 30 reporting seeds.%n",
                    seedSet.size());
            }
            System.out.println("================================================================================");
        } catch (Exception e) {
            System.err.println("Statistical analysis failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void handleExportDemoData(String[] args) {
        Path outputPath = Path.of("data/demo_seed.json");
        long seed = 42L;
        int donorLimit = 200;

        for (int i = 1; i < args.length; i++) {
            if ("--output".equals(args[i]) && i + 1 < args.length) {
                outputPath = Path.of(args[++i]);
            } else if ("--seed".equals(args[i]) && i + 1 < args.length) {
                seed = Long.parseLong(args[++i].trim());
            } else if ("--donors".equals(args[i]) && i + 1 < args.length) {
                donorLimit = Integer.parseInt(args[++i].trim());
            }
        }

        try {
            System.out.println("================================================================================");
            System.out.println("LifeLine Evaluation CLI — Export Synthetic Demo Seed Data");
            System.out.println("================================================================================");
            System.out.printf("Generating synthetic world (seed=%d, donorLimit=%d)...%n", seed, donorLimit);
            lifeline.eval.demo.DemoDataExporter.exportDemoData(seed, donorLimit, outputPath);
            System.out.printf("Successfully exported demo seed data to: %s%n", outputPath.toAbsolutePath());
            System.out.println("================================================================================");
        } catch (Exception e) {
            System.err.println("Demo data export failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printHelp() {
        System.out.println("LifeLine Evaluation CLI");
        System.out.println("Usage:");
        System.out.println("  java -jar lifeline-eval.jar run <EXPERIMENT> [options]");
        System.out.println("  java -jar lifeline-eval.jar analyze <csv-file> [options]");
        System.out.println("  java -jar lifeline-eval.jar export-demo-data [options]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  run <EXPERIMENT>      Run an evaluation experiment (e.g. E1, E2, E3, E7, E8)");
        System.out.println("  analyze <CSV_PATH>    Run statistical comparisons on an existing results CSV");
        System.out.println("  export-demo-data      Export synthetic observable donors and banks to JSON seed file");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --config <path>       Path to YAML experiment configuration file");
        System.out.println("  --seeds <s1,s2,...>   Comma-separated list of random seeds to execute");
        System.out.println("  --output-dir <path>   Directory where CSV, manifest, and reports will be written");
        System.out.println("  --output <path>       Destination path for exported demo seed JSON (default: data/demo_seed.json)");
        System.out.println("  --seed <long>         Random seed for synthetic demo world (default: 42)");
        System.out.println("  --donors <int>        Maximum number of observable donors to export (default: 200)");
        System.out.println("  --help, -h            Show this help dialog");
    }
}
