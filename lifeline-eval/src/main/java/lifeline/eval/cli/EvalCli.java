package lifeline.eval.cli;

import lifeline.eval.runner.ExperimentConfig;
import lifeline.eval.runner.ExperimentRunResult;
import lifeline.eval.runner.ExperimentRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Command-line interface for running evaluation experiments.
 * <p>
 * Usage:
 * <pre>
 *   java -jar lifeline-eval.jar run E1 --config configs/e1.yaml
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

        String command = args[0];
        if (!"run".equalsIgnoreCase(command)) {
            System.err.println("Unknown command: " + command);
            printHelp();
            System.exit(1);
        }

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
            // Default configuration for experiment
            config = new ExperimentConfig(
                experiment,
                List.of("S0", "S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8"),
                List.of("M1", "M2", "M3"),
                explicitSeeds != null ? explicitSeeds : ExperimentConfig.defaultReportingSeeds(),
                outputDir != null ? outputDir : "results",
                Runtime.getRuntime().availableProcessors()
            );
        }

        System.out.println("================================================================================");
        System.out.printf("  LifeLine Evaluation Runner :: %s%n", config.experiment());
        System.out.printf("  Systems (%d)         : %s%n", config.systems().size(), config.systems());
        System.out.printf("  Response Models (%d) : %s%n", config.responseModels().size(), config.responseModels());
        System.out.printf("  Seeds (%d)           : %s%n", config.seeds().size(), config.seeds());
        System.out.printf("  Output Directory    : %s%n", config.outputDir());
        System.out.println("================================================================================");

        ExperimentRunner runner = new ExperimentRunner(config, configPath);
        ExperimentRunResult result = runner.execute();

        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("Execution completed in %.2f seconds.%n", result.durationSeconds());
        System.out.printf("Artifacts written:%n");
        System.out.printf("  - Tidy CSV : %s (%d metric records)%n", result.csvPath(), result.totalMetricRows());
        System.out.printf("  - Manifest : %s%n", result.manifestPath());
        System.out.println("================================================================================");
    }

    private static void printHelp() {
        System.out.println("LifeLine Evaluation CLI");
        System.out.println("Usage: java -jar lifeline-eval.jar run <EXPERIMENT> [options]");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  run <EXPERIMENT>      Experiment identifier (e.g. E1, E2, E3)");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --config <path>       Path to YAML experiment configuration file");
        System.out.println("  --seeds <s1,s2,...>   Comma-separated list of random seeds to execute");
        System.out.println("  --output-dir <path>   Directory where CSV and manifest artifacts will be written");
        System.out.println("  --help, -h            Show this help dialog");
    }
}
