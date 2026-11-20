package lifeline.eval.study.inventory;

import lifeline.eval.metric.TidyMetricRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class E4InventoryStudyTest {

    @Test
    @DisplayName("E4InventoryStudy runs end-to-end on test configuration and produces valid artifacts")
    void testEndToEnd(@TempDir Path tempDir) {
        E4Config config = new E4Config(
            "E4",
            List.of(0.0, 0.50),
            List.of("S6_TIER0_ON", "S6_TIER0_OFF"),
            List.of("M1"),
            List.of(1L),
            tempDir.toString(),
            2,
            25.0,
            null
        );

        E4InventoryStudy study = new E4InventoryStudy(config);
        E4StudyResult result = study.run();

        // 2 levels * 1 seed * 2 systems * 1 model = 4 simulations
        assertThat(result.totalSimulations()).isEqualTo(4);
        assertThat(result.worldGenerations()).isEqualTo(1); // 1 world generated per seed

        Path csvPath = tempDir.resolve("e4_results.csv");
        assertThat(Files.exists(csvPath)).isTrue();

        Path manifestPath = tempDir.resolve("run_manifest.json");
        assertThat(Files.exists(manifestPath)).isTrue();

        List<TidyMetricRow> rows = result.rows();
        assertThat(rows).isNotEmpty();

        // Check slices
        assertThat(rows).anyMatch(r -> r.experiment().equals("E4_0PCT"));
        assertThat(rows).anyMatch(r -> r.experiment().equals("E4_50PCT"));

        // Check systems
        assertThat(rows).anyMatch(r -> r.system().equals("S6_TIER0_ON"));
        assertThat(rows).anyMatch(r -> r.system().equals("S6_TIER0_OFF"));

        // Check new metrics
        assertThat(rows).anyMatch(r -> r.metric().equals("tier0_resolved_share"));
        assertThat(rows).anyMatch(r -> r.metric().equals("total_notifications_sent"));

        // In 0PCT slice:
        double t0OnZero = rows.stream()
            .filter(r -> r.experiment().equals("E4_0PCT") && r.system().equals("S6_TIER0_ON") && r.metric().equals("tier0_resolved_share"))
            .mapToDouble(TidyMetricRow::value)
            .findFirst()
            .orElseThrow();
        double notifOnZero = rows.stream()
            .filter(r -> r.experiment().equals("E4_0PCT") && r.system().equals("S6_TIER0_ON") && r.metric().equals("total_notifications_sent"))
            .mapToDouble(TidyMetricRow::value)
            .findFirst()
            .orElseThrow();
        double notifOffZero = rows.stream()
            .filter(r -> r.experiment().equals("E4_0PCT") && r.system().equals("S6_TIER0_OFF") && r.metric().equals("total_notifications_sent"))
            .mapToDouble(TidyMetricRow::value)
            .findFirst()
            .orElseThrow();

        assertThat(t0OnZero).isEqualTo(0.0);
        assertThat(notifOnZero).isEqualTo(notifOffZero);

        // In 50PCT slice:
        double t0On50 = rows.stream()
            .filter(r -> r.experiment().equals("E4_50PCT") && r.system().equals("S6_TIER0_ON") && r.metric().equals("tier0_resolved_share"))
            .mapToDouble(TidyMetricRow::value)
            .findFirst()
            .orElseThrow();
        double notifOn50 = rows.stream()
            .filter(r -> r.experiment().equals("E4_50PCT") && r.system().equals("S6_TIER0_ON") && r.metric().equals("total_notifications_sent"))
            .mapToDouble(TidyMetricRow::value)
            .findFirst()
            .orElseThrow();
        double notifOff50 = rows.stream()
            .filter(r -> r.experiment().equals("E4_50PCT") && r.system().equals("S6_TIER0_OFF") && r.metric().equals("total_notifications_sent"))
            .mapToDouble(TidyMetricRow::value)
            .findFirst()
            .orElseThrow();

        assertThat(t0On50).isGreaterThan(0.0);
        assertThat(notifOn50).isLessThan(notifOff50);
    }
}

