package lifeline.eval.runner;

import lifeline.eval.metric.TidyMetricRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExperimentRunnerOptimizationTest {

    @Test
    @DisplayName("Priority 2: World generation occurs exactly once per seed across systems and models")
    void worldGenerationOccursOncePerSeed(@TempDir Path tempDir) {
        // 2 seeds, 3 systems, 2 models = 12 total simulation runs
        List<Long> seeds = List.of(1001L, 1002L);
        ExperimentConfig config = new ExperimentConfig(
            "E1_OPT_TEST",
            List.of("S0", "S2", "S6"),
            List.of("M1", "M2"),
            seeds,
            tempDir.resolve("opt_run").toString(),
            2
        );

        ExperimentRunner runner = new ExperimentRunner(config);
        ExperimentRunResult res = runner.execute();

        // Must execute 12 simulation runs
        assertThat(res.totalSimulationRuns()).isEqualTo(12);

        // Crucial optimization check: World generation must occur strictly 2 times (once per seed), NOT 12 times!
        assertThat(res.worldGenerations())
            .as("World must be generated strictly once per seed")
            .isEqualTo(2);

        assertThat(runner.worldGenerationCount()).isEqualTo(2);

        // Verify CSV and Manifest exist
        assertThat(Files.exists(res.csvPath())).isTrue();
        assertThat(Files.exists(res.manifestPath())).isTrue();
    }

    @Test
    @DisplayName("Parallel execution (parallelism > 1) matches sequential execution (parallelism = 1) bitwise")
    void parallelMatchesSequentialExecutionBitwise(@TempDir Path tempDir) throws Exception {
        ExperimentConfig seqConfig = new ExperimentConfig(
            "PAR_TEST",
            List.of("S0", "S6"),
            List.of("M1"),
            List.of(1005L),
            tempDir.resolve("seq").toString(),
            1
        );

        ExperimentConfig parConfig = new ExperimentConfig(
            "PAR_TEST",
            List.of("S0", "S6"),
            List.of("M1"),
            List.of(1005L),
            tempDir.resolve("par").toString(),
            4
        );

        ExperimentRunner seqRunner = new ExperimentRunner(seqConfig);
        ExperimentRunResult seqRes = seqRunner.execute();

        ExperimentRunner parRunner = new ExperimentRunner(parConfig);
        ExperimentRunResult parRes = parRunner.execute();

        assertThat(seqRes.rows()).hasSize(parRes.rows().size());
        assertThat(seqRes.rows()).isNotEmpty();

        for (int i = 0; i < seqRes.rows().size(); i++) {
            TidyMetricRow r1 = seqRes.rows().get(i);
            TidyMetricRow r2 = parRes.rows().get(i);

            assertThat(r1.metric()).isEqualTo(r2.metric());
            assertThat(r1.system()).isEqualTo(r2.system());
            assertThat(r1.seed()).isEqualTo(r2.seed());
            assertThat(r1.value())
                .as("Metric %s value must be identical between parallel and sequential execution", r1.metric())
                .isEqualTo(r2.value());
        }
    }

    @Test
    @DisplayName("Manifest JSON records world_generations, dataset_type, and marks statistical summary null when seeds < 6")
    void manifestRecordsOptimizationAndStats(@TempDir Path tempDir) throws Exception {
        ExperimentConfig config = new ExperimentConfig(
            "MANIFEST_TEST",
            List.of("S0", "S6"),
            List.of("M1"),
            List.of(1003L, 1004L),
            tempDir.resolve("manifest").toString(),
            2
        );

        ExperimentRunner runner = new ExperimentRunner(config);
        ExperimentRunResult res = runner.execute();

        String json = Files.readString(res.manifestPath());
        assertThat(json).contains("\"world_generations\": 2");
        assertThat(json).contains("\"total_runs\": 4");
        assertThat(json).contains("\"dataset_type\": \"EXPLORATORY_TUNING\"");
        // With 2 seeds (< 6 required for inference), statistical summary is safely skipped
        assertThat(json).contains("\"statistical_summary_file\": null");
        assertThat(res.statisticalSummaryPath()).isNull();
        assertThat(res.statisticalReportPath()).isNull();
    }
}

