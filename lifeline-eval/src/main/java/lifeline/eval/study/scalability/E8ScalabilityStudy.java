package lifeline.eval.study.scalability;

import lifeline.core.domain.BloodRequest;
import lifeline.core.domain.DonorSnapshot;
import lifeline.core.factor.Context;
import lifeline.core.rank.Ranker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/**
 * Dedicated reproducible benchmark harness for Experiment E8: Engine Scalability and Latency (Claim C7).
 * <p>
 * Requirements:
 * 1. Benchmarks actual Ranker implementation.
 * 2. Uses deterministic synthetic inputs.
 * 3. Separates data generation and warm-up from measured ranking operations.
 * 4. Measures Mean, Median (p50), p95, and p99 latencies.
 * 5. Records JVM details, methodology, and limitations.
 * 6. Exports CSV, JSON, and Markdown reports.
 */
public final class E8ScalabilityStudy {

    private static final Logger log = LoggerFactory.getLogger(E8ScalabilityStudy.class);

    private final Ranker ranker;

    public E8ScalabilityStudy() {
        this.ranker = new Ranker();
    }

    /**
     * Executes the E8 benchmark across the configured pool sizes and concurrency levels.
     */
    public ScalabilityBenchmarkResult run(ScalabilityConfig config) {
        log.info("Starting E8 Scalability Benchmark: poolSizes={}, concurrencyLevels={}, warmup={}, measured={}, topN={}",
            config.donorPoolSizes(), config.concurrencyLevels(), config.warmupIterations(),
            config.measuredIterations(), config.topN()
        );

        ScalabilityEnvironment environment = ScalabilityEnvironment.captureCurrent();
        ScalabilitySyntheticPoolGenerator generator = new ScalabilitySyntheticPoolGenerator(config.seed());

        List<ScalabilityBenchmarkCell> cells = new ArrayList<>();

        // Generate benchmark requests up-front
        int maxIterations = config.concurrencyLevels().stream()
            .mapToInt(config::operationsForConcurrency)
            .max()
            .orElse(config.measuredIterations());
        maxIterations = Math.max(maxIterations, 1000);
        List<BloodRequest> requests = generator.generateRequests(maxIterations);
        List<Context> contexts = requests.stream().map(generator::createContext).toList();

        for (int poolSize : config.donorPoolSizes()) {
            log.info("Generating deterministic donor pool for size {} (seed={})...", poolSize, config.seed());
            List<DonorSnapshot> pool = generator.generatePool(poolSize);

            // Warm-up phase: run warm-up queries to trigger JIT compilation (timings discarded)
            log.debug("Warming up JIT for pool size {} ({} iterations)...", poolSize, config.warmupIterations());
            for (int w = 0; w < config.warmupIterations(); w++) {
                int reqIdx = w % requests.size();
                ranker.rank(pool, requests.get(reqIdx), contexts.get(reqIdx), config.topN());
            }

            for (int concurrency : config.concurrencyLevels()) {
                int totalOps = config.operationsForConcurrency(concurrency);
                log.info("Benchmarking cell: poolSize={}, concurrency={}, totalOps={} ({} ops/thread)",
                    poolSize, concurrency, totalOps, String.format(Locale.US, "%.1f", (double) totalOps / concurrency));

                long[] elapsedNanos = new long[totalOps];
                ExecutorService executor = Executors.newFixedThreadPool(concurrency);
                CountDownLatch startGate = new CountDownLatch(1);
                CountDownLatch doneGate = new CountDownLatch(totalOps);

                for (int iter = 0; iter < totalOps; iter++) {
                    final int iterationIndex = iter;
                    final int reqIndex = iter % requests.size();
                    final BloodRequest request = requests.get(reqIndex);
                    final Context ctx = contexts.get(reqIndex);

                    executor.submit(() -> {
                        try {
                            startGate.await();
                            long start = System.nanoTime();
                            ranker.rank(pool, request, ctx, config.topN());
                            long duration = System.nanoTime() - start;
                            elapsedNanos[iterationIndex] = duration;
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            doneGate.countDown();
                        }
                    });
                }

                long wallClockStart = System.nanoTime();
                startGate.countDown(); // simultaneously release all worker threads

                try {
                    boolean finished = doneGate.await(10, TimeUnit.MINUTES);
                    if (!finished) {
                        throw new RuntimeException("Benchmark timed out for pool=" + poolSize + ", concurrency=" + concurrency);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Benchmark interrupted", e);
                } finally {
                    executor.shutdown();
                }

                long wallClockDuration = System.nanoTime() - wallClockStart;

                ScalabilityBenchmarkCell cell = ScalabilityBenchmarkCell.compute(
                    poolSize,
                    concurrency,
                    elapsedNanos,
                    wallClockDuration
                );

                log.info("Cell completed: pool={}, concurrency={} -> mean={} ms, p50={} ms, p95={} ms, p99={} ms, thr={} req/s",
                    poolSize, concurrency,
                    String.format(Locale.US, "%.3f", cell.meanLatencyMs()),
                    String.format(Locale.US, "%.3f", cell.p50LatencyMs()),
                    String.format(Locale.US, "%.3f", cell.p95LatencyMs()),
                    String.format(Locale.US, "%.3f", cell.p99LatencyMs()),
                    String.format(Locale.US, "%.1f", cell.throughputReqPerSec())
                );

                cells.add(cell);
            }
        }

        ScalabilityBenchmarkResult result = new ScalabilityBenchmarkResult(config, environment, cells);
        log.info("E8 Scalability Benchmark completed successfully across {} grid cells.", cells.size());
        return result;
    }

    /**
     * Exports the benchmark results to CSV, JSON, and Markdown files.
     */
    public void exportResults(ScalabilityBenchmarkResult result, Path outputDir) {
        try {
            Files.createDirectories(outputDir);

            // 1. Export CSV
            Path csvPath = outputDir.resolve("e8_scalability.csv");
            try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8))) {
                pw.println("donor_pool_size,concurrency,iterations,mean_latency_ms,p50_latency_ms,p95_latency_ms,p99_latency_ms,min_latency_ms,max_latency_ms,std_dev_ms,throughput_req_per_sec");
                for (ScalabilityBenchmarkCell c : result.cells()) {
                    pw.printf(Locale.US, "%d,%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.2f%n",
                        c.donorPoolSize(),
                        c.concurrency(),
                        c.measuredIterations(),
                        c.meanLatencyMs(),
                        c.p50LatencyMs(),
                        c.p95LatencyMs(),
                        c.p99LatencyMs(),
                        c.minLatencyMs(),
                        c.maxLatencyMs(),
                        c.stdDevMs(),
                        c.throughputReqPerSec()
                    );
                }
            }

            // 2. Export JSON
            Path jsonPath = outputDir.resolve("e8_scalability.json");
            exportJson(result, jsonPath);

            // 3. Export Markdown Report
            Path mdPath = outputDir.resolve("e8_benchmark_report.md");
            Files.writeString(mdPath, result.toMarkdownReport(), StandardCharsets.UTF_8);

            log.info("E8 benchmark artifacts successfully written to {}", outputDir.toAbsolutePath());
        } catch (Exception e) {
            throw new RuntimeException("Failed to export E8 results to " + outputDir, e);
        }
    }

    private void exportJson(ScalabilityBenchmarkResult result, Path jsonPath) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"experiment\": \"E8\",\n");
        sb.append("  \"claim\": \"C7\",\n");
        sb.append("  \"title\": \"Engine Scalability and Latency Benchmark\",\n");
        sb.append("  \"timestamp\": \"").append(result.environment().timestamp()).append("\",\n");

        // Environment block
        sb.append("  \"environment\": {\n");
        sb.append("    \"java_version\": \"").append(result.environment().javaVersion()).append("\",\n");
        sb.append("    \"java_vendor\": \"").append(result.environment().javaVendor()).append("\",\n");
        sb.append("    \"vm_name\": \"").append(result.environment().vmName()).append("\",\n");
        sb.append("    \"os_name\": \"").append(result.environment().osName()).append("\",\n");
        sb.append("    \"os_arch\": \"").append(result.environment().osArch()).append("\",\n");
        sb.append("    \"available_processors\": ").append(result.environment().availableProcessors()).append(",\n");
        sb.append("    \"max_memory_mb\": ").append(result.environment().maxMemoryMb()).append(",\n");
        sb.append("    \"methodology\": \"").append(result.environment().methodologyDescription().replace("\"", "\\\"")).append("\",\n");
        sb.append("    \"limitations\": \"").append(result.environment().limitationsCaveat().replace("\"", "\\\"")).append("\"\n");
        sb.append("  },\n");

        // Configuration block
        sb.append("  \"configuration\": {\n");
        sb.append("    \"warmup_iterations\": ").append(result.config().warmupIterations()).append(",\n");
        sb.append("    \"measured_iterations\": ").append(result.config().measuredIterations()).append(",\n");
        sb.append("    \"top_n\": ").append(result.config().topN()).append(",\n");
        sb.append("    \"seed\": ").append(result.config().seed()).append(",\n");
        sb.append("    \"operations_per_concurrency\": {\n");
        int opIdx = 0;
        int opTotal = result.config().operationsPerConcurrency().size();
        for (var entry : result.config().operationsPerConcurrency().entrySet()) {
            sb.append("      \"").append(entry.getKey()).append("\": ").append(entry.getValue());
            if (++opIdx < opTotal) sb.append(",");
            sb.append("\n");
        }
        sb.append("    }\n");
        sb.append("  },\n");

        // Benchmark cells array
        sb.append("  \"cells\": [\n");
        for (int i = 0; i < result.cells().size(); i++) {
            ScalabilityBenchmarkCell c = result.cells().get(i);
            sb.append("    {\n");
            sb.append("      \"donor_pool_size\": ").append(c.donorPoolSize()).append(",\n");
            sb.append("      \"concurrency\": ").append(c.concurrency()).append(",\n");
            sb.append("      \"iterations\": ").append(c.measuredIterations()).append(",\n");
            sb.append(String.format(Locale.US, "      \"mean_latency_ms\": %.4f,\n", c.meanLatencyMs()));
            sb.append(String.format(Locale.US, "      \"p50_latency_ms\": %.4f,\n", c.p50LatencyMs()));
            sb.append(String.format(Locale.US, "      \"p95_latency_ms\": %.4f,\n", c.p95LatencyMs()));
            sb.append(String.format(Locale.US, "      \"p99_latency_ms\": %.4f,\n", c.p99LatencyMs()));
            sb.append(String.format(Locale.US, "      \"min_latency_ms\": %.4f,\n", c.minLatencyMs()));
            sb.append(String.format(Locale.US, "      \"max_latency_ms\": %.4f,\n", c.maxLatencyMs()));
            sb.append(String.format(Locale.US, "      \"std_dev_ms\": %.4f,\n", c.stdDevMs()));
            sb.append(String.format(Locale.US, "      \"throughput_req_per_sec\": %.2f\n", c.throughputReqPerSec()));
            sb.append("    }");
            if (i < result.cells().size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n");
        sb.append("}\n");

        Files.writeString(jsonPath, sb.toString(), StandardCharsets.UTF_8);
    }
}

