package lifeline.eval.runner;

import lifeline.eval.metric.TidyMetricRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class E2ExperimentRunnerTest {

    @Test
    @DisplayName("E2 end-to-end runner test with inferential statistics on 6 tuning seeds")
    void testE2ExecutionWithInference(@TempDir Path tempDir) throws Exception {
        List<String> e2Systems = List.of(
            "S6",
            "S6_NO_PROXIMITY"
        );

        // 6 tuning seeds (1001-1006) so inferential analysis runs (MIN_PAIRED_SEEDS_FOR_INFERENCE = 6)
        List<Long> tuningSeeds = List.of(1001L, 1002L, 1003L, 1004L, 1005L, 1006L);

        ExperimentConfig config = new ExperimentConfig(
            "E2",
            e2Systems,
            List.of("M1"),
            tuningSeeds,
            tempDir.resolve("e2_out").toString(),
            2
        );

        ExperimentRunner runner = new ExperimentRunner(config);
        ExperimentRunResult result = runner.execute();

        // 2 systems * 1 model * 6 seeds = 12 simulation runs
        assertThat(result.totalSimulationRuns()).isEqualTo(12);
        assertThat(result.worldGenerations()).isEqualTo(6);
        assertThat(result.rows()).hasSize(12 * 18);

        // Verify result artifacts exist
        assertThat(Files.exists(result.csvPath())).isTrue();
        assertThat(Files.exists(result.manifestPath())).isTrue();
        assertThat(result.statisticalSummaryPath()).isNotNull();
        assertThat(Files.exists(result.statisticalSummaryPath())).isTrue();
        assertThat(result.statisticalReportPath()).isNotNull();
        assertThat(Files.exists(result.statisticalReportPath())).isTrue();

        // 1 model * 18 metrics * 1 comparison = 18 comparisons
        List<String> statLines = Files.readAllLines(result.statisticalSummaryPath());
        assertThat(statLines).hasSize(19); // header + 18 rows

        String report = Files.readString(result.statisticalReportPath());
        assertThat(report).contains("S6 vs S6_NO_PROXIMITY");
    }

    @Test
    @DisplayName("E2 execution verifies all 7 systems execute successfully on tuning seed")
    void testAll7SystemsRun(@TempDir Path tempDir) {
        List<String> e2Systems = List.of(
            "S6",
            "S6_NO_PROXIMITY",
            "S6_NO_RARITY",
            "S6_NO_FRESHNESS",
            "S6_SINGLE_URGENCY",
            "S7",
            "S7_NO_RESPONSIVENESS"
        );

        ExperimentConfig config = new ExperimentConfig(
            "E2",
            e2Systems,
            List.of("M1"),
            List.of(1001L),
            tempDir.resolve("e2_all7").toString(),
            2
        );

        ExperimentRunResult result = new ExperimentRunner(config).execute();
        assertThat(result.totalSimulationRuns()).isEqualTo(7);
        assertThat(result.rows()).hasSize(7 * 18);
        assertThat(Files.exists(result.csvPath())).isTrue();
        assertThat(Files.exists(result.manifestPath())).isTrue();
    }

    @Test
    @DisplayName("E2 determinism: sequential and parallel execution match bitwise on tuning seed")
    void testE2ParallelMatchesSequential(@TempDir Path tempDir) {
        List<String> e2Systems = List.of(
            "S6",
            "S6_NO_PROXIMITY"
        );

        List<Long> seed = List.of(1005L);

        ExperimentConfig seqConfig = new ExperimentConfig(
            "E2", e2Systems, List.of("M1"), seed, tempDir.resolve("seq").toString(), 1
        );
        ExperimentConfig parConfig = new ExperimentConfig(
            "E2", e2Systems, List.of("M1"), seed, tempDir.resolve("par").toString(), 4
        );

        ExperimentRunResult seqRes = new ExperimentRunner(seqConfig).execute();
        ExperimentRunResult parRes = new ExperimentRunner(parConfig).execute();

        assertThat(seqRes.rows()).hasSize(parRes.rows().size());
        for (int i = 0; i < seqRes.rows().size(); i++) {
            TidyMetricRow r1 = seqRes.rows().get(i);
            TidyMetricRow r2 = parRes.rows().get(i);
            assertThat(r1.system()).isEqualTo(r2.system());
            assertThat(r1.metric()).isEqualTo(r2.metric());
            assertThat(r1.value())
                .as("System %s metric %s must match bitwise between sequential and parallel", r1.system(), r1.metric())
                .isEqualTo(r2.value());
        }
    }
}
