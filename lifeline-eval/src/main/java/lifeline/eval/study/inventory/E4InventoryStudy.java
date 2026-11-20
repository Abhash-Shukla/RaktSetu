package lifeline.eval.study.inventory;

import lifeline.core.domain.BloodGroup;
import lifeline.core.domain.BloodRequest;
import lifeline.core.inventory.BankInventory;
import lifeline.eval.metric.TidyMetricRow;
import lifeline.eval.runner.ExperimentConfig;
import lifeline.eval.runner.ExperimentRunner;
import lifeline.eval.stats.*;
import lifeline.sim.inventory.InventoryScaler;
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
 * Execution harness for Experiment E4 (Inventory-Level Sweep & Tier-0 Evaluation — Claim C1).
 * Evaluates S6_TIER0_ON vs S6_TIER0_OFF across inventory levels {0%, 25%, 50%, 100%} and response models M1-M3.
 */
public final class E4InventoryStudy {

    private static final Logger log = LoggerFactory.getLogger(E4InventoryStudy.class);

    private final E4Config config;
    private final SimulationParameters simParams;
    private final Map<BloodGroup, Double> basePopulationFrequencies;
    private final AtomicInteger worldGenerationCount = new AtomicInteger(0);

    public E4InventoryStudy(E4Config config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.simParams = SimulationParameters.loadDefault();
        this.basePopulationFrequencies = ExperimentRunner.loadPopulationFrequencies();
    }

    public E4StudyResult run() {
        EvaluationDatasetType datasetType = StatisticalAnalyzer.classifyAndValidateSeeds(new HashSet<>(config.seeds()));
        Instant startTime = Instant.now();
        worldGenerationCount.set(0);

        int totalSimulations = config.inventoryLevels().size()
            * config.seeds().size()
            * config.systems().size()
            * config.responseModels().size();

        log.info("Starting Experiment E4: {} inventory levels, {} seeds, {} systems, {} response models (total simulations={}, parallelism={}, dataset_type={})",
            config.inventoryLevels().size(),
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
                "E4",
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

                for (double beta : config.inventoryLevels()) {
                    String sliceName = String.format(Locale.ROOT, "E4_%dPCT", (int) Math.round(beta * 100));

                    // Scale bank inventory deterministically for this inventory level
                    List<BankInventory> scaledBanks = InventoryScaler.scale(basePopulationFrequencies, beta, zones);
                    World worldWithInventory = new World(
                        world.zoneMap(),
                        world.latentDonors(),
                        scaledBanks,
                        world.parameters(),
                        world.referenceTime()
                    );

                    List<Callable<List<TidyMetricRow>>> tasks = new ArrayList<>();
                    for (String system : config.systems()) {
                        boolean tier0Enabled = system.toUpperCase().contains("ON");
                        for (String model : config.responseModels()) {
                            tasks.add(() -> helperRunner.executeSingleSimulation(
                                sliceName,
                                system,
                                model,
                                seed,
                                worldWithInventory,
                                requests,
                                tier0Enabled,
                                config.tier0RadiusKm()
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
            throw new RuntimeException("E4 inventory study execution failed", e);
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
            Path csvPath = outDir.resolve("e4_results.csv");
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

                    // Add paired derived metric: donor_contacts_avoided
                    for (String model : config.responseModels()) {
                        StatisticalComparison avoidedComp = computeDonorContactsAvoidedComparison(
                            sliceName,
                            datasetType,
                            model,
                            sliceRows,
                            config.seeds()
                        );
                        if (avoidedComp != null) {
                            sliceComparisons.add(avoidedComp);
                        }
                    }

                    allComparisons.addAll(sliceComparisons);
                }

                // Sort comparisons deterministically
                allComparisons.sort(Comparator
                    .comparing(StatisticalComparison::experiment)
                    .thenComparing(StatisticalComparison::responseModel)
                    .thenComparing(StatisticalComparison::metric)
                );

                statCsvPath = outDir.resolve("e4_statistical_summary.csv");
                try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(statCsvPath, StandardCharsets.UTF_8))) {
                    writer.println(StatisticalComparison.csvHeader());
                    for (StatisticalComparison comp : allComparisons) {
                        writer.println(comp.toCsvLine());
                    }
                }

                statReportPath = outDir.resolve("e4_statistical_report.md");
                String reportMarkdown = buildStatisticalReportMarkdown(allComparisons, config.inventoryLevels());
                Files.writeString(statReportPath, reportMarkdown, StandardCharsets.UTF_8);
            } else {
                log.warn("E4 executed with {} seed(s). Inferential statistical analysis skipped (minimum {} required).",
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
                config.inventoryLevels(),
                config.systems(),
                config.responseModels(),
                config.seeds(),
                startTime,
                endTime,
                durationSeconds,
                totalSimulations,
                worldGenerationCount.get(),
                "e4_results.csv",
                statCsvPath != null ? "e4_statistical_summary.csv" : null
            );
            Files.writeString(manifestPath, manifestJson, StandardCharsets.UTF_8);

            log.info("E4 Study complete: duration={}s, simulations={}, output={}",
                String.format(Locale.ROOT, "%.2f", durationSeconds), totalSimulations, outDir);

            return new E4StudyResult(
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
            throw new RuntimeException("Failed to persist E4 study artifacts", e);
        }
    }

    private StatisticalComparison computeDonorContactsAvoidedComparison(
        String sliceName,
        EvaluationDatasetType datasetType,
        String responseModel,
        List<TidyMetricRow> sliceRows,
        List<Long> seeds
    ) {
        Map<Long, Double> offContacts = new HashMap<>();
        Map<Long, Double> onContacts = new HashMap<>();

        for (TidyMetricRow r : sliceRows) {
            if (r.experiment().equals(sliceName)
                && r.responseModel().equals(responseModel)
                && r.metric().equals("total_notifications_sent")) {
                if (r.system().equals("S6_TIER0_OFF")) {
                    offContacts.put(r.seed(), r.value());
                } else if (r.system().equals("S6_TIER0_ON")) {
                    onContacts.put(r.seed(), r.value());
                }
            }
        }

        List<Long> commonSeeds = seeds.stream()
            .filter(s -> offContacts.containsKey(s) && onContacts.containsKey(s))
            .sorted()
            .toList();

        if (commonSeeds.isEmpty()) {
            return null;
        }

        double[] avoided = new double[commonSeeds.size()];
        for (int i = 0; i < commonSeeds.size(); i++) {
            long s = commonSeeds.get(i);
            avoided[i] = offContacts.get(s) - onContacts.get(s);
        }

        return StatisticalAnalyzer.comparePairedDerivedMetric(
            sliceName,
            datasetType,
            responseModel,
            "S6_TIER0_ON",
            "S6_TIER0_OFF",
            "donor_contacts_avoided",
            MetricDirection.HIGHER_IS_BETTER,
            avoided,
            StatisticalAnalyzer.DEFAULT_ALPHA,
            StatisticalAnalyzer.DEFAULT_BOOTSTRAP_SEED
        );
    }

    private String buildStatisticalReportMarkdown(List<StatisticalComparison> comparisons, List<Double> levels) {
        StringBuilder sb = new StringBuilder();
        sb.append("# LifeLine Evaluation :: Experiment E4 (Inventory-Level Sweep) Statistical Report\n\n");
        sb.append("Evaluation of Tier-0 Inventory Resolution (Claim C1) across Inventory Levels: ").append(levels).append("\n\n");
        sb.append("- Systems: S6_TIER0_ON vs S6_TIER0_OFF\n");
        sb.append("- Significance test: Paired Wilcoxon signed-rank across reporting seeds, Holm correction, Cliff's delta\n\n");

        Map<String, List<StatisticalComparison>> bySlice = new LinkedHashMap<>();
        for (StatisticalComparison c : comparisons) {
            bySlice.computeIfAbsent(c.experiment(), k -> new ArrayList<>()).add(c);
        }

        for (Map.Entry<String, List<StatisticalComparison>> sliceEntry : bySlice.entrySet()) {
            String slice = sliceEntry.getKey();
            sb.append("## Slice ").append(slice).append("\n\n");

            Map<String, List<StatisticalComparison>> byModel = new LinkedHashMap<>();
            for (StatisticalComparison c : sliceEntry.getValue()) {
                byModel.computeIfAbsent(c.responseModel(), k -> new ArrayList<>()).add(c);
            }

            for (Map.Entry<String, List<StatisticalComparison>> modelEntry : byModel.entrySet()) {
                String model = modelEntry.getKey();
                sb.append("### Response Model: ").append(model).append("\n\n");
                sb.append("| Metric | S6_TIER0_ON Mean [95% CI] | S6_TIER0_OFF Mean [95% CI] | HL Shift | Cliff's Delta | Raw p | Holm p | W / L / T | Decision |\n");
                sb.append("|---|---|---|---|---|---|---|---|---|\n");

                for (StatisticalComparison c : modelEntry.getValue()) {
                    sb.append(String.format(Locale.ROOT,
                        "| `%s` | %.4f [%.4f, %.4f] | %.4f [%.4f, %.4f] | %+.4f | %+.3f (%s) | %.4f | %.4f | %d / %d / %d | **%s** |\n",
                        c.metric(),
                        c.candidateMean(), c.candidateMeanCiLower(), c.candidateMeanCiUpper(),
                        c.baselineMean(), c.baselineMeanCiLower(), c.baselineMeanCiUpper(),
                        c.hodgesLehmannShift(),
                        c.cliffsDelta(), c.cliffsMagnitude(),
                        c.rawPValue(), c.holmPValue(),
                        c.wins(), c.losses(), c.ties(),
                        c.decision()
                    ));
                }
                sb.append("\n");
            }
        }

        return sb.toString();
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
        List<Double> levels,
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
        sb.append("  \"experiment\": \"E4\",\n");
        sb.append(String.format("  \"dataset_type\": \"%s\",\n", datasetType));
        sb.append(String.format("  \"git_commit\": \"%s\",\n", gitCommit));
        sb.append(String.format("  \"config_hash\": \"%s\",\n", configHash));
        sb.append("  \"inventory_levels\": ").append(levels).append(",\n");
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

