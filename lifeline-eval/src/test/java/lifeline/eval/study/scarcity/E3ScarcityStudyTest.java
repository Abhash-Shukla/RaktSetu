package lifeline.eval.study.scarcity;

import lifeline.eval.metric.TidyMetricRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class E3ScarcityStudyTest {

    @Test
    @DisplayName("E3ScarcityStudy runs end-to-end on test configuration and produces valid artifacts")
    void testEndToEnd(@TempDir Path tempDir) {
        E3Config config = new E3Config(
            List.of(1.0, 2.0),
            List.of("S6", "S6_NO_RARITY"),
            List.of("M1"),
            List.of(1001L),
            tempDir.toString(),
            2
        );

        E3ScarcityStudy study = new E3ScarcityStudy(config);
        E3StudyResult result = study.run();

        assertThat(result.totalSimulations()).isEqualTo(4); // 2 multipliers * 1 seed * 2 systems * 1 model
        assertThat(result.worldGenerations()).isEqualTo(1); // 1 world generated per seed

        Path csvPath = tempDir.resolve("e3_results.csv");
        assertThat(Files.exists(csvPath)).isTrue();

        Path manifestPath = tempDir.resolve("run_manifest.json");
        assertThat(Files.exists(manifestPath)).isTrue();

        List<TidyMetricRow> rows = result.rows();
        assertThat(rows).isNotEmpty();

        // Check slices
        assertThat(rows).anyMatch(r -> r.experiment().equals("E3_1.0X"));
        assertThat(rows).anyMatch(r -> r.experiment().equals("E3_2.0X"));

        // Check systems
        assertThat(rows).anyMatch(r -> r.system().equals("S6"));
        assertThat(rows).anyMatch(r -> r.system().equals("S6_NO_RARITY"));

        // Check new metrics
        assertThat(rows).anyMatch(r -> r.metric().equals("rare_request_fulfillment"));
        assertThat(rows).anyMatch(r -> r.metric().equals("common_request_fulfillment"));
    }
}
