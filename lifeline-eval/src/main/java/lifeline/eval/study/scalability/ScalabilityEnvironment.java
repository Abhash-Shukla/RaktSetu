package lifeline.eval.study.scalability;

import java.time.Instant;

/**
 * Captures JVM runtime details, hardware characteristics, methodology, and limitations for E8.
 */
public record ScalabilityEnvironment(
    String javaVersion,
    String javaVendor,
    String vmName,
    String osName,
    String osArch,
    int availableProcessors,
    long maxMemoryMb,
    Instant timestamp,
    String methodologyDescription,
    String limitationsCaveat
) {
    public static ScalabilityEnvironment captureCurrent() {
        return new ScalabilityEnvironment(
            System.getProperty("java.version", "unknown"),
            System.getProperty("java.vendor", "unknown"),
            System.getProperty("java.vm.name", "unknown"),
            System.getProperty("os.name", "unknown"),
            System.getProperty("os.arch", "unknown"),
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().maxMemory() / (1024 * 1024),
            Instant.now(),
            "Isolated microbenchmark executing Ranker.rank() directly on deterministic in-memory candidate pools. " +
                "Pool generation and warm-up cycles are separated and discarded before timing. Elapsed time measured " +
                "via System.nanoTime() strictly surrounding the ranking operation.",
            "Hardware- and JVM-dependent. Benchmark measures CPU and memory-bound algorithmic ranking performance on " +
                "a development workstation. It does not measure network round-trip, database persistence overhead, or " +
                "multi-node distributed consensus. Latencies should not be cited as production SLA guarantees."
        );
    }
}

