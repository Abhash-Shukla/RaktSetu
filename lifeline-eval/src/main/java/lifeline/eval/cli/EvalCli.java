package lifeline.eval.cli;

import lifeline.eval.metric.TidyMetricRow;
import lifeline.eval.runner.ExperimentConfig;
import lifeline.eval.runner.ExperimentRunResult;
import lifeline.eval.runner.ExperimentRunner;
import lifeline.eval.stats.EvaluationDatasetType;
import lifeline.eval.stats.StatisticalAnalyzer;
import lifeline.eval.stats.StatisticalComparison;

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

    private static void printHelp() {
        System.out.println("LifeLine Evaluation CLI");
        System.out.println("Usage:");
        System.out.println("  java -jar lifeline-eval.jar run <EXPERIMENT> [options]");
        System.out.println("  java -jar lifeline-eval.jar analyze <csv-file> [options]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  run <EXPERIMENT>      Run an evaluation experiment (e.g. E1)");
        System.out.println("  analyze <CSV_PATH>    Run statistical comparisons on an existing results CSV");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --config <path>       Path to YAML experiment configuration file");
        System.out.println("  --seeds <s1,s2,...>   Comma-separated list of random seeds to execute");
        System.out.println("  --output-dir <path>   Directory where CSV, manifest, and reports will be written");
        System.out.println("  --help, -h            Show this help dialog");
    }
}
