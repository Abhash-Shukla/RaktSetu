package lifeline.eval.study.scalability;

import java.util.Arrays;
import java.util.Objects;

/**
 * Result metrics for a single (donorPoolSize x concurrencyLevel) benchmark cell in E8.
 */
public record ScalabilityBenchmarkCell(
    int donorPoolSize,
    int concurrency,
    int measuredIterations,
    double meanLatencyMs,
    double p50LatencyMs,
    double p95LatencyMs,
    double p99LatencyMs,
    double minLatencyMs,
    double maxLatencyMs,
    double stdDevMs,
    double throughputReqPerSec
) {
    /**
     * Factory computing statistics from raw elapsed nanosecond recordings.
     */
    public static ScalabilityBenchmarkCell compute(
        int donorPoolSize,
        int concurrency,
        long[] rawElapsedNanos,
        long totalWallClockNanos
    ) {
        Objects.requireNonNull(rawElapsedNanos, "rawElapsedNanos cannot be null");
        int n = rawElapsedNanos.length;
        if (n == 0) {
            throw new IllegalArgumentException("Cannot compute cell statistics on empty latency array");
        }

        double[] latenciesMs = new double[n];
        for (int i = 0; i < n; i++) {
            latenciesMs[i] = rawElapsedNanos[i] / 1_000_000.0;
        }

        Arrays.sort(latenciesMs);

        double mean = PercentileCalculator.mean(latenciesMs);
        double stdDev = PercentileCalculator.standardDeviation(latenciesMs, mean);
        double min = latenciesMs[0];
        double max = latenciesMs[n - 1];
        double p50 = PercentileCalculator.percentile(latenciesMs, 50.0);
        double p95 = PercentileCalculator.percentile(latenciesMs, 95.0);
        double p99 = PercentileCalculator.percentile(latenciesMs, 99.0);

        double wallClockSec = totalWallClockNanos / 1_000_000_000.0;
        double throughput = wallClockSec > 0 ? (n / wallClockSec) : 0.0;

        return new ScalabilityBenchmarkCell(
            donorPoolSize,
            concurrency,
            n,
            mean,
            p50,
            p95,
            p99,
            min,
            max,
            stdDev,
            throughput
        );
    }
}

