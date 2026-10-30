package lifeline.eval.runner;

import lifeline.eval.metric.TidyMetricRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExperimentRunnerTest {

    @Test
    @DisplayName("Runner output is deterministic for a fixed seed")
    void runnerOutputDeterministicForFixedSeed(@TempDir Path tempDir) throws Exception {
        ExperimentConfig config1 = new ExperimentConfig(
            "TEST_EXP",
            List.of("S0", "S2", "S6"),
            List.of("M1"),
            List.of(1042L),
            tempDir.resolve("run1").toString(),
            1
        );

        ExperimentConfig config2 = new ExperimentConfig(
            "TEST_EXP",
            List.of("S0", "S2", "S6"),
            List.of("M1"),
            List.of(1042L),
            tempDir.resolve("run2").toString(),
            1
        );

        ExperimentRunner runner1 = new ExperimentRunner(config1);
        ExperimentRunResult res1 = runner1.execute();

        ExperimentRunner runner2 = new ExperimentRunner(config2);
        ExperimentRunResult res2 = runner2.execute();

        assertThat(res1.rows()).hasSize(res2.rows().size());
        assertThat(res1.rows().size()).isGreaterThan(0);

        for (int i = 0; i < res1.rows().size(); i++) {
            TidyMetricRow row1 = res1.rows().get(i);
            TidyMetricRow row2 = res2.rows().get(i);

            assertThat(row1.metric()).isEqualTo(row2.metric());
            assertThat(row1.system()).isEqualTo(row2.system());
            assertThat(row1.value())
                .as("Metric %s for system %s must be identical across identical seeds", row1.metric(), row1.system())
                .isEqualTo(row2.value());
        }

        // Verify CSV file exists and has correct header
        assertThat(Files.exists(res1.csvPath())).isTrue();
        List<String> csvLines = Files.readAllLines(res1.csvPath());
        assertThat(csvLines.get(0)).isEqualTo("experiment,system,response_model,seed,metric,value");
        assertThat(csvLines.size()).isGreaterThan(1);

        // Verify manifest JSON exists and contains key fields
        assertThat(Files.exists(res1.manifestPath())).isTrue();
        String manifestJson = Files.readString(res1.manifestPath());
        assertThat(manifestJson).contains("\"experiment\": \"TEST_EXP\"");
        assertThat(manifestJson).contains("\"git_commit\"");
        assertThat(manifestJson).contains("\"config_hash\"");
        assertThat(manifestJson).contains("\"seeds\": [1042]");
    }
}
