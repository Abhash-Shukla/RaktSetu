package lifeline.eval.study.fallback;

import lifeline.eval.metric.TidyMetricRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class E5FallbackStudyTest {

    @Test
    @DisplayName("E5FallbackStudy runs end-to-end on test configuration and produces valid artifacts")
    void testEndToEnd(@TempDir Path tempDir) {
        E5Config config = new E5Config(
            "E5",
            List.of(1, 10),
            List.of("S6_FALLBACK_ON", "S6_FALLBACK_OFF"),
            List.of("M1"),
            List.of(1001L), // tuning seed
            tempDir.toString(),
            2,
            null
        );

        E5FallbackStudy study = new E5FallbackStudy(config);
        E5StudyResult result = study.run();

        // 2 budgets * 1 seed * 2 systems * 1 model = 4 simulations
        assertThat(result.totalSimulations()).isEqualTo(4);
        assertThat(result.worldGenerations()).isEqualTo(1); // 1 world generated per seed

        Path csvPath = tempDir.resolve("e5_results.csv");
        assertThat(Files.exists(csvPath)).isTrue();

        Path manifestPath = tempDir.resolve("run_manifest.json");
        assertThat(Files.exists(manifestPath)).isTrue();

        List<TidyMetricRow> rows = result.rows();
        assertThat(rows).isNotEmpty();

        // Check slices
        assertThat(rows).anyMatch(r -> r.experiment().equals("E5_B1"));
        assertThat(rows).anyMatch(r -> r.experiment().equals("E5_B10"));

        // Check systems
        assertThat(rows).anyMatch(r -> r.system().equals("S6_FALLBACK_ON"));
        assertThat(rows).anyMatch(r -> r.system().equals("S6_FALLBACK_OFF"));

        // Check metrics
        assertThat(rows).anyMatch(r -> r.metric().equals("fallback_attempts_count"));
        assertThat(rows).anyMatch(r -> r.metric().equals("fallback_resolved_count"));
        assertThat(rows).anyMatch(r -> r.metric().equals("fulfillment_within_deadline"));

        // In E5_B1 (budget = 1), fallback attempts must be 0 for both systems
        double b1AttemptsOn = rows.stream()
            .filter(r -> r.experiment().equals("E5_B1") && r.system().equals("S6_FALLBACK_ON") && r.metric().equals("fallback_attempts_count"))
            .mapToDouble(TidyMetricRow::value).findFirst().orElseThrow();
        double b1AttemptsOff = rows.stream()
            .filter(r -> r.experiment().equals("E5_B1") && r.system().equals("S6_FALLBACK_OFF") && r.metric().equals("fallback_attempts_count"))
            .mapToDouble(TidyMetricRow::value).findFirst().orElseThrow();
        assertThat(b1AttemptsOn).isEqualTo(0.0);
        assertThat(b1AttemptsOff).isEqualTo(0.0);
    }
}
