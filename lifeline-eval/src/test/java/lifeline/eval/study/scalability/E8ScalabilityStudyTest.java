package lifeline.eval.study.scalability;

import lifeline.core.domain.BloodRequest;
import lifeline.core.domain.DonorSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

class E8ScalabilityStudyTest {

    @Test
    @DisplayName("PercentileCalculator computes exact and interpolated percentiles")
    void percentileCalculationAccuracy() {
        double[] values = {10.0, 20.0, 30.0, 40.0, 50.0};

        assertThat(PercentileCalculator.percentile(values, 0.0)).isEqualTo(10.0);
        assertThat(PercentileCalculator.percentile(values, 50.0)).isEqualTo(30.0);
        assertThat(PercentileCalculator.percentile(values, 100.0)).isEqualTo(50.0);

        // Interpolated 95th percentile: index 0.95 * 4 = 3.8 -> 40 + 0.8 * 10 = 48.0
        assertThat(PercentileCalculator.percentile(values, 95.0)).isCloseTo(48.0, offset(1e-6));

        // Mean and standard deviation
        assertThat(PercentileCalculator.mean(values)).isEqualTo(30.0);
        assertThat(PercentileCalculator.standardDeviation(values, 30.0)).isCloseTo(15.811388, offset(1e-5));

        // Edge case: single element
        double[] single = {42.0};
        assertThat(PercentileCalculator.percentile(single, 50.0)).isEqualTo(42.0);
        assertThat(PercentileCalculator.percentile(single, 99.0)).isEqualTo(42.0);

        // Errors
        assertThatThrownBy(() -> PercentileCalculator.percentile(new double[0], 50.0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PercentileCalculator.percentile(values, -5.0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PercentileCalculator.percentile(values, 105.0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ScalabilityConfig enforces required pool sizes, concurrency levels and validations")
    void scalabilityConfigRequirements() {
        ScalabilityConfig def = ScalabilityConfig.defaultConfig();
        assertThat(def.donorPoolSizes()).containsExactly(1000, 5000, 20000, 50000);
        assertThat(def.concurrencyLevels()).containsExactly(1, 10, 50, 100);
        assertThat(def.topN()).isEqualTo(10);
        assertThat(def.seed()).isEqualTo(1001L);

        assertThatThrownBy(() -> new ScalabilityConfig(List.of(), List.of(1), 5, 10, 10, 1L, "out"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScalabilityConfig(List.of(1000), List.of(), 5, 10, 10, 1L, "out"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScalabilityConfig(List.of(1000), List.of(1), -1, 10, 10, 1L, "out"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScalabilityConfig(List.of(1000), List.of(1), 5, 0, 10, 1L, "out"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ScalabilitySyntheticPoolGenerator generates strictly deterministic eligible pools")
    void syntheticPoolGenerationDeterminism() {
        long testSeed = 42L;
        ScalabilitySyntheticPoolGenerator gen1 = new ScalabilitySyntheticPoolGenerator(testSeed);
        ScalabilitySyntheticPoolGenerator gen2 = new ScalabilitySyntheticPoolGenerator(testSeed);

        List<DonorSnapshot> pool1 = gen1.generatePool(100);
        List<DonorSnapshot> pool2 = gen2.generatePool(100);

        assertThat(pool1).hasSize(100);
        assertThat(pool2).hasSize(100);

        LocalDate refDate = LocalDate.of(2026, 11, 6);
        for (int i = 0; i < 100; i++) {
            DonorSnapshot d1 = pool1.get(i);
            DonorSnapshot d2 = pool2.get(i);

            assertThat(d1.id()).isEqualTo(d2.id());
            assertThat(d1.bloodGroup()).isEqualTo(d2.bloodGroup());
            assertThat(d1.sex()).isEqualTo(d2.sex());
            assertThat(d1.homeZone()).isEqualTo(d2.homeZone());
            assertThat(d1.lastDonationDate()).isEqualTo(d2.lastDonationDate());

            // Cooldown eligibility verification (must be >= 90 days ago for men / 120 for women)
            long daysSinceDonation = ChronoUnit.DAYS.between(d1.lastDonationDate(), refDate);
            assertThat(daysSinceDonation).isGreaterThanOrEqualTo(90);
            assertThat(d1.availability()).isTrue();
            assertThat(d1.paused()).isFalse();
        }

        // Requests determinism
        List<BloodRequest> reqs1 = gen1.generateRequests(10);
        List<BloodRequest> reqs2 = gen2.generateRequests(10);
        assertThat(reqs1).hasSize(10);
        for (int i = 0; i < 10; i++) {
            assertThat(reqs1.get(i).id()).isEqualTo(reqs2.get(i).id());
            assertThat(reqs1.get(i).bloodGroup()).isEqualTo(reqs2.get(i).bloodGroup());
            assertThat(reqs1.get(i).urgency()).isEqualTo(reqs2.get(i).urgency());
        }

        // Different seeds yield different pools
        ScalabilitySyntheticPoolGenerator genDiff = new ScalabilitySyntheticPoolGenerator(999L);
        List<DonorSnapshot> poolDiff = genDiff.generatePool(100);
        assertThat(pool1).isNotEqualTo(poolDiff);
    }

    @Test
    @DisplayName("E8ScalabilityStudy executes benchmark and exports CSV, JSON, and Markdown reports")
    void scalabilityStudyExecutionAndExport(@TempDir Path tempDir) throws Exception {
        E8ScalabilityStudy study = new E8ScalabilityStudy();
        ScalabilityConfig config = new ScalabilityConfig(
            List.of(1000, 2000), // Small pool sizes for test speed
            List.of(1, 2),        // Concurrencies 1 and 2
            2,                    // Warmup
            5,                    // Measured iterations
            10,
            1001L,
            tempDir.toString()
        );

        ScalabilityBenchmarkResult result = study.run(config);

        assertThat(result.cells()).hasSize(4); // 2 pool sizes x 2 concurrencies

        for (ScalabilityBenchmarkCell cell : result.cells()) {
            assertThat(cell.measuredIterations()).isEqualTo(5);
            assertThat(cell.meanLatencyMs()).isPositive();
            assertThat(cell.p50LatencyMs()).isPositive();
            assertThat(cell.p95LatencyMs()).isPositive();
            assertThat(cell.p99LatencyMs()).isPositive();
            assertThat(cell.minLatencyMs()).isLessThanOrEqualTo(cell.p50LatencyMs());
            assertThat(cell.p50LatencyMs()).isLessThanOrEqualTo(cell.maxLatencyMs());
            assertThat(cell.throughputReqPerSec()).isPositive();
        }

        // Verify Environment details
        assertThat(result.environment().javaVersion()).isNotBlank();
        assertThat(result.environment().availableProcessors()).isPositive();
        assertThat(result.environment().methodologyDescription()).contains("Ranker.rank()");
        assertThat(result.environment().limitationsCaveat()).contains("Hardware- and JVM-dependent");

        // Verify Markdown Report
        String md = result.toMarkdownReport();
        assertThat(md).contains("Scalability and Latency Report");
        assertThat(md).contains("Throughput Grid");

        // Export and verify files
        study.exportResults(result, tempDir);

        Path csvPath = tempDir.resolve("e8_scalability.csv");
        Path jsonPath = tempDir.resolve("e8_scalability.json");
        Path mdPath = tempDir.resolve("e8_benchmark_report.md");

        assertThat(Files.exists(csvPath)).isTrue();
        assertThat(Files.exists(jsonPath)).isTrue();
        assertThat(Files.exists(mdPath)).isTrue();

        List<String> lines = Files.readAllLines(csvPath);
        assertThat(lines).hasSize(5); // Header + 4 cells
    }

    @Test
    @DisplayName("ScalabilityConfig parses and provides sustained operations per concurrency mapping")
    void sustainedConfigOperationsPerConcurrency() {
        ScalabilityConfig sustained = ScalabilityConfig.sustainedConfig();
        assertThat(sustained.operationsForConcurrency(1)).isEqualTo(100);
        assertThat(sustained.operationsForConcurrency(10)).isEqualTo(100);
        assertThat(sustained.operationsForConcurrency(50)).isEqualTo(500);
        assertThat(sustained.operationsForConcurrency(100)).isEqualTo(1000);
        assertThat(sustained.operationsForConcurrency(200)).isEqualTo(100); // Fallback to default measuredIterations

        // Verify from actual yaml file
        Path yamlPath = Files.exists(Path.of("configs", "e8-sustained.yaml"))
            ? Path.of("configs", "e8-sustained.yaml")
            : Path.of("..", "configs", "e8-sustained.yaml");
        assertThat(Files.exists(yamlPath)).isTrue();
        ScalabilityConfig loaded = ScalabilityConfig.load(yamlPath);
        assertThat(loaded.donorPoolSizes()).containsExactly(1000, 5000, 20000, 50000);
        assertThat(loaded.concurrencyLevels()).containsExactly(1, 10, 50, 100);
        assertThat(loaded.operationsForConcurrency(1)).isEqualTo(100);
        assertThat(loaded.operationsForConcurrency(10)).isEqualTo(100);
        assertThat(loaded.operationsForConcurrency(50)).isEqualTo(500);
        assertThat(loaded.operationsForConcurrency(100)).isEqualTo(1000);
        assertThat(loaded.outputDir()).isEqualTo("results/e8-sustained");
    }

    @Test
    @DisplayName("E8ScalabilityStudy executes concurrent cells with dynamic operation counts")
    void sustainedBenchmarkExecutionWithDynamicOperations(@TempDir Path tempDir) {
        E8ScalabilityStudy study = new E8ScalabilityStudy();
        ScalabilityConfig config = new ScalabilityConfig(
            List.of(1000),
            List.of(1, 4),
            2,
            5,
            java.util.Map.of(1, 10, 4, 20), // 10 ops for c=1, 20 ops for c=4
            10,
            1001L,
            tempDir.toString()
        );

        ScalabilityBenchmarkResult result = study.run(config);
        assertThat(result.cells()).hasSize(2);

        ScalabilityBenchmarkCell cell1 = result.cells().get(0);
        assertThat(cell1.concurrency()).isEqualTo(1);
        assertThat(cell1.measuredIterations()).isEqualTo(10);
        assertThat(cell1.meanLatencyMs()).isPositive();
        assertThat(cell1.throughputReqPerSec()).isPositive();

        ScalabilityBenchmarkCell cell2 = result.cells().get(1);
        assertThat(cell2.concurrency()).isEqualTo(4);
        assertThat(cell2.measuredIterations()).isEqualTo(20);
        assertThat(cell2.meanLatencyMs()).isPositive();
        assertThat(cell2.throughputReqPerSec()).isPositive();
    }
}
