package lifeline.eval.runner;

import lifeline.core.domain.BloodGroup;
import lifeline.core.domain.BloodRequest;
import lifeline.eval.baseline.BaselineMatcherFactory;
import lifeline.eval.metric.EvaluationMetrics;
import lifeline.eval.metric.MetricCalculator;
import lifeline.eval.metric.TidyMetricRow;
import lifeline.eval.stats.EvaluationDatasetType;
import lifeline.eval.stats.StatisticalAnalyzer;
import lifeline.eval.stats.StatisticalComparison;
import lifeline.sim.engine.Matcher;
import lifeline.sim.engine.SimulationResult;
import lifeline.sim.engine.SimulationRun;
import lifeline.sim.model.SimulationParameters;
import lifeline.sim.oracle.Oracle;
import lifeline.sim.oracle.SimulatedOracle;
import lifeline.sim.request.RequestGenerator;
import lifeline.sim.response.M1BaselineLogisticModel;
import lifeline.sim.response.M2DynamicFatigueModel;
import lifeline.sim.response.M3MisspecifiedModel;
import lifeline.sim.response.ResponseModel;
import lifeline.sim.world.World;
import lifeline.sim.world.WorldGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Config-driven parallel experiment runner executing evaluation protocols.
 * <p>
 * Optimized per Priority 2: Generates World and request sets once per seed,
 * reusing the immutable synthetic world across all systems and response models.
 * Writes a tidy CSV per experiment, emits statistical comparison summaries,
 * and records a run manifest JSON.
 */
public final class ExperimentRunner {

    private static final Logger log = LoggerFactory.getLogger(ExperimentRunner.class);

    private final ExperimentConfig config;
    private final Path configPath;
    private final SimulationParameters simParams;
    private final Map<BloodGroup, Double> populationFrequencies;
    private final AtomicInteger worldGenerationCount = new AtomicInteger(0);

    public ExperimentRunner(ExperimentConfig config, Path configPath) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.configPath = configPath;
        this.simParams = SimulationParameters.loadDefault();
        this.populationFrequencies = loadPopulationFrequencies();
    }

    public ExperimentRunner(ExperimentConfig config) {
        this(config, null);
    }

    public int worldGenerationCount() {
        return worldGenerationCount.get();
    }

    /**
     * Executes the experiment and writes results to disk.
     *
     * @return summary of execution
     */
    public ExperimentRunResult execute() {
        EvaluationDatasetType datasetType = StatisticalAnalyzer.classifyAndValidateSeeds(new HashSet<>(config.seeds()));

        Instant startTime = Instant.now();
        worldGenerationCount.set(0);

        int totalSimulations = config.seeds().size() * config.systems().size() * config.responseModels().size();
        log.info("Starting experiment {} across {} systems, {} response models, {} seeds (total runs={}, parallelism={}, dataset_type={})",
            config.experiment(), config.systems().size(), config.responseModels().size(), config.seeds().size(), totalSimulations, config.parallelism(), datasetType
        );

        ExecutorService executor = Executors.newFixedThreadPool(config.parallelism());
        List<TidyMetricRow> allRows = new ArrayList<>();

        try {
            WorldGenerator worldGen = new WorldGenerator(simParams);
            RequestGenerator reqGen = new RequestGenerator(simParams, populationFrequencies);

            // Execute seed by seed: generate world and requests ONCE per seed
            for (long seed : config.seeds()) {
                World world = worldGen.generate(seed);
                worldGenerationCount.incrementAndGet();

                List<String> zones = new ArrayList<>(world.zoneMap().centroids().keySet());
                List<BloodRequest> requests = reqGen.generateRequests(
                    zones,
                    world.referenceTime(),
                    new SplittableRandom(seed + 100L)
                );

                List<Callable<List<TidyMetricRow>>> seedTasks = new ArrayList<>();
                for (String system : config.systems()) {
                    for (String modelName : config.responseModels()) {
                        seedTasks.add(() -> executeSingleSimulation(
                            config.experiment(),
                            system,
                            modelName,
                            seed,
                            world,
                            requests
                        ));
                    }
                }

                List<Future<List<TidyMetricRow>>> futures = executor.invokeAll(seedTasks);
                for (Future<List<TidyMetricRow>> future : futures) {
                    allRows.addAll(future.get());
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Experiment execution encountered an error", e);
        } finally {
            executor.shutdown();
        }

        // Sort rows deterministically: (experiment, system, response_model, seed, metric)
        allRows.sort(Comparator
            .comparing(TidyMetricRow::experiment)
            .thenComparing(TidyMetricRow::system)
            .thenComparing(TidyMetricRow::responseModel)
            .thenComparingLong(TidyMetricRow::seed)
            .thenComparing(TidyMetricRow::metric)
        );

        Instant endTime = Instant.now();
        double durationSeconds = java.time.Duration.between(startTime, endTime).toMillis() / 1000.0;

        // Ensure output directory exists
        Path outDir = Path.of(config.outputDir());
        try {
            Files.createDirectories(outDir);

            // 1. Write tidy CSV
            String csvFilename = config.experiment().toLowerCase() + "_results.csv";
            Path csvPath = outDir.resolve(csvFilename);
            try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8))) {
                writer.println("experiment,system,response_model,seed,metric,value");
                for (TidyMetricRow row : allRows) {
                    writer.println(row.toCsvLine());
                }
            }

            // 2. Perform statistical analysis and write summaries
            Path statCsvPath = null;
            Path statReportPath = null;
            String statCsvFilename = null;

            if (config.seeds().size() < StatisticalAnalyzer.MIN_PAIRED_SEEDS_FOR_INFERENCE) {
                log.info("Experiment {} executed with {} seed(s). Inferential statistical analysis skipped (minimum {} required for paired testing). Ordinary simulation metrics written to {}.",
                    config.experiment(), config.seeds().size(), StatisticalAnalyzer.MIN_PAIRED_SEEDS_FOR_INFERENCE, csvFilename);
            } else {
                StatisticalAnalyzer analyzer = new StatisticalAnalyzer();
                List<StatisticalComparison> comparisons = analyzer.analyze(allRows);

                if (!comparisons.isEmpty()) {
                    statCsvFilename = config.experiment().toLowerCase() + "_statistical_summary.csv";
                    statCsvPath = outDir.resolve(statCsvFilename);
                    analyzer.writeCsv(comparisons, statCsvPath);

                    String reportFilename = config.experiment().toLowerCase() + "_statistical_report.md";
                    statReportPath = outDir.resolve(reportFilename);
                    String mdReport = analyzer.generateMarkdownReport(comparisons);
                    Files.writeString(statReportPath, mdReport, StandardCharsets.UTF_8);
                }
            }

            // 3. Write manifest JSON
            String manifestFilename = "run_manifest.json";
            Path manifestPath = outDir.resolve(manifestFilename);
            String gitCommit = resolveGitCommit();
            String configHash = computeConfigHash();

            String manifestJson = buildManifestJson(
                gitCommit,
                configHash,
                config.experiment(),
                datasetType,
                config.systems(),
                config.responseModels(),
                config.seeds(),
                startTime,
                endTime,
                durationSeconds,
                totalSimulations,
                worldGenerationCount.get(),
                csvFilename,
                statCsvFilename
            );
            Files.writeString(manifestPath, manifestJson, StandardCharsets.UTF_8);

            log.info("Experiment {} complete (duration={}s, world_gens={}). Wrote {} rows to {} and manifest to {}",
                config.experiment(), String.format(Locale.ROOT, "%.2f", durationSeconds), worldGenerationCount.get(), allRows.size(), csvPath, manifestPath
            );

            return new ExperimentRunResult(
                config.experiment(),
                csvPath,
                manifestPath,
                statCsvPath,
                statReportPath,
                allRows.size(),
                totalSimulations,
                worldGenerationCount.get(),
                durationSeconds,
                allRows
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to persist experiment artifacts", e);
        }
    }

    /**
     * Executes a single simulation run using a pre-generated immutable World and request set.
     */
    public List<TidyMetricRow> executeSingleSimulation(
        String experiment,
        String system,
        String modelName,
        long seed,
        World world,
        List<BloodRequest> requests
    ) {
        ResponseModel responseModel = createResponseModel(modelName, simParams);
        Oracle oracle = new SimulatedOracle(world.latentDonors(), responseModel);
        Matcher matcher = BaselineMatcherFactory.create(
            system,
            world,
            oracle,
            new SplittableRandom(seed + 200L),
            seed
        );

        SimulationRun runner = new SimulationRun();
        SimulationResult result = runner.run(world, matcher, responseModel, requests, seed);

        EvaluationMetrics metrics = MetricCalculator.calculate(result, requests, world, oracle);
        return metrics.toTidyRows(experiment, system, modelName, seed);
    }

    /**
     * Executes an isolated single simulation evaluation run generating its own world.
     * Maintained for standalone invocations.
     */
    public List<TidyMetricRow> executeSingleRun(
        String experiment,
        String system,
        String modelName,
        long seed
    ) {
        WorldGenerator worldGen = new WorldGenerator(simParams);
        World world = worldGen.generate(seed);

        RequestGenerator reqGen = new RequestGenerator(simParams, populationFrequencies);
        List<String> zones = new ArrayList<>(world.zoneMap().centroids().keySet());
        List<BloodRequest> requests = reqGen.generateRequests(
            zones,
            world.referenceTime(),
            new SplittableRandom(seed + 100L)
        );

        return executeSingleSimulation(experiment, system, modelName, seed, world, requests);
    }

    public static ResponseModel createResponseModel(String modelName, SimulationParameters params) {
        return switch (modelName.toUpperCase()) {
            case "M1" -> new M1BaselineLogisticModel(params);
            case "M2" -> new M2DynamicFatigueModel(params);
            case "M3" -> new M3MisspecifiedModel(params);
            default -> throw new IllegalArgumentException("Unknown response model: " + modelName);
        };
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

    private String computeConfigHash() {
        if (configPath == null || !Files.exists(configPath)) {
            return "sha256-default";
        }
        try {
            byte[] bytes = Files.readAllBytes(configPath);
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

    private static String buildManifestJson(
        String gitCommit,
        String configHash,
        String experiment,
        EvaluationDatasetType datasetType,
        List<String> systems,
        List<String> responseModels,
        List<Long> seeds,
        Instant start,
        Instant end,
        double durationSeconds,
        int totalRuns,
        int worldGenerations,
        String csvFile,
        String statSummaryFile
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append(String.format("  \"git_commit\": \"%s\",\n", gitCommit));
        sb.append(String.format("  \"config_hash\": \"%s\",\n", configHash));
        sb.append(String.format("  \"experiment\": \"%s\",\n", experiment));
        sb.append(String.format("  \"dataset_type\": \"%s\",\n", datasetType.name()));
        sb.append(String.format("  \"systems\": %s,\n", toJsonArray(systems)));
        sb.append(String.format("  \"response_models\": %s,\n", toJsonArray(responseModels)));
        sb.append(String.format("  \"seeds\": %s,\n", seeds.toString()));
        sb.append(String.format("  \"timestamp_start\": \"%s\",\n", start));
        sb.append(String.format("  \"timestamp_end\": \"%s\",\n", end));
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

    @SuppressWarnings("unchecked")
    private static Map<BloodGroup, Double> loadPopulationFrequencies() {
        try (InputStream in = ExperimentRunner.class.getResourceAsStream("/sim/population.yaml")) {
            if (in == null) {
                throw new IllegalStateException("Resource /sim/population.yaml not found");
            }
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Map<String, Object> root = yaml.load(in);
            Map<String, Object> freqs = (Map<String, Object>) root.get("frequencies");

            Map<BloodGroup, Double> map = new EnumMap<>(BloodGroup.class);
            for (Map.Entry<String, Object> entry : freqs.entrySet()) {
                BloodGroup bg = parseBloodGroup(entry.getKey());
                double freq = ((Number) entry.getValue()).doubleValue();
                map.put(bg, freq);
            }
            return map;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load /sim/population.yaml", e);
        }
    }

    private static BloodGroup parseBloodGroup(String name) {
        return switch (name.trim()) {
            case "O+" -> BloodGroup.O_POS;
            case "O-" -> BloodGroup.O_NEG;
            case "A+" -> BloodGroup.A_POS;
            case "A-" -> BloodGroup.A_NEG;
            case "B+" -> BloodGroup.B_POS;
            case "B-" -> BloodGroup.B_NEG;
            case "AB+" -> BloodGroup.AB_POS;
            case "AB-" -> BloodGroup.AB_NEG;
            default -> throw new IllegalArgumentException("Unknown blood group: " + name);
        };
    }
}
