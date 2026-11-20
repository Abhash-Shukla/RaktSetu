package lifeline.eval.study.scalability;

import java.util.List;

/**
 * Complete result suite for Experiment E8: Engine Scalability and Latency.
 */
public record ScalabilityBenchmarkResult(
    ScalabilityConfig config,
    ScalabilityEnvironment environment,
    List<ScalabilityBenchmarkCell> cells
) {
    /**
     * Formats the benchmark results into an IEEE-ready Markdown report table.
     */
    public String toMarkdownReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("# LifeLine Matching Engine — Scalability and Latency Report (E8 / Claim C7)\n\n");
        sb.append("## Environment and Hardware\n\n");
        sb.append(String.format("- **Timestamp**: `%s`\n", environment.timestamp()));
        sb.append(String.format("- **Java Version**: `%s` (%s)\n", environment.javaVersion(), environment.javaVendor()));
        sb.append(String.format("- **JVM**: `%s`\n", environment.vmName()));
        sb.append(String.format("- **Operating System**: `%s` (%s)\n", environment.osName(), environment.osArch()));
        sb.append(String.format("- **Available CPU Threads**: `%d`\n", environment.availableProcessors()));
        sb.append(String.format("- **Max Allocated Heap**: `%d MB`\n\n", environment.maxMemoryMb()));

        sb.append("## Methodology\n\n");
        sb.append(environment.methodologyDescription()).append("\n\n");

        sb.append("## Limitations and Threats to Validity\n\n");
        sb.append("> [!NOTE]\n");
        sb.append("> ").append(environment.limitationsCaveat()).append("\n\n");

        sb.append("## Latency and Throughput Grid (Top-10 Greedy Ranking)\n\n");
        sb.append("| Donor Pool Size | Concurrency (Threads) | Iterations | Mean Latency (ms) | Median p50 (ms) | p95 (ms) | p99 (ms) | Min (ms) | Max (ms) | Std Dev (ms) | Throughput (req/s) |\n");
        sb.append("|---|---|---|---|---|---|---|---|---|---|---|\n");

        for (ScalabilityBenchmarkCell cell : cells) {
            sb.append(String.format(
                "| %d | %d | %d | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f | %.1f |\n",
                cell.donorPoolSize(),
                cell.concurrency(),
                cell.measuredIterations(),
                cell.meanLatencyMs(),
                cell.p50LatencyMs(),
                cell.p95LatencyMs(),
                cell.p99LatencyMs(),
                cell.minLatencyMs(),
                cell.maxLatencyMs(),
                cell.stdDevMs(),
                cell.throughputReqPerSec()
            ));
        }

        sb.append("\n## Analysis and Observations\n\n");
        sb.append("- **Linearity**: The ranking algorithm is strictly O(N log K) / O(N) evaluation over the eligible candidate pool.\n");
        sb.append("- **SLA Target Verification**: NFR-5 specifies a target of < 1000 ms for a 20,000-donor pool on development hardware.\n");

        return sb.toString();
    }
}

