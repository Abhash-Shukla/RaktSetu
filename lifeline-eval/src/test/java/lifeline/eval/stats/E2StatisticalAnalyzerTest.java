package lifeline.eval.stats;

import lifeline.eval.metric.TidyMetricRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class E2StatisticalAnalyzerTest {

    @Test
    @DisplayName("E1 mappings preserved exactly: S6 and S7 each evaluated against S0, S2, S3, S4, S5")
    void testE1DefaultMappingsPreserved() {
        StatisticalAnalyzer e1Analyzer = StatisticalAnalyzer.forExperiment("E1");

        Map<String, List<String>> mappings = e1Analyzer.candidateToBaselines();
        assertThat(mappings).containsOnlyKeys("S6", "S7");

        List<String> expectedBaselines = List.of("S0", "S2", "S3", "S4", "S5");
        assertThat(mappings.get("S6")).containsExactlyElementsOf(expectedBaselines);
        assertThat(mappings.get("S7")).containsExactlyElementsOf(expectedBaselines);

        // Default no-arg constructor must match E1 exactly
        StatisticalAnalyzer defaultAnalyzer = new StatisticalAnalyzer();
        assertThat(defaultAnalyzer.candidateToBaselines()).isEqualTo(mappings);
    }

    @Test
    @DisplayName("E2 mappings partition families correctly: S6 vs 4 ablations, S7 vs 1 ablation")
    void testE2MappingsPartitioning() {
        StatisticalAnalyzer e2Analyzer = StatisticalAnalyzer.forExperiment("E2");

        Map<String, List<String>> mappings = e2Analyzer.candidateToBaselines();
        assertThat(mappings).containsOnlyKeys("S6", "S7");

        List<String> s6Baselines = mappings.get("S6");
        assertThat(s6Baselines).containsExactly(
            "S6_NO_PROXIMITY",
            "S6_NO_RARITY",
            "S6_NO_FRESHNESS",
            "S6_SINGLE_URGENCY"
        );

        List<String> s7Baselines = mappings.get("S7");
        assertThat(s7Baselines).containsExactly("S7_NO_RESPONSIVENESS");
    }

    @Test
    @DisplayName("E2 end-to-end analysis: S6 family has m=4 Holm comparisons, S7 has m=1")
    void testE2EndToEndAnalysis() {
        List<TidyMetricRow> rows = new ArrayList<>();
        // N=8 seeds required to achieve Holm-adjusted significance (min p_adj = 4 * 2/256 = 0.03125 <= 0.05) across m=4 comparisons
        long[] seeds = {1001, 1002, 1003, 1004, 1005, 1006, 1007, 1008};

        // Populate S6, S7 and all 5 ablation systems for 1 metric under M1
        for (long s : seeds) {
            rows.add(new TidyMetricRow("E2", "S6", "M1", s, "fulfillment_within_deadline", 0.85));
            rows.add(new TidyMetricRow("E2", "S6_NO_PROXIMITY", "M1", s, "fulfillment_within_deadline", 0.50));
            rows.add(new TidyMetricRow("E2", "S6_NO_RARITY", "M1", s, "fulfillment_within_deadline", 0.80));
            rows.add(new TidyMetricRow("E2", "S6_NO_FRESHNESS", "M1", s, "fulfillment_within_deadline", 0.75));
            rows.add(new TidyMetricRow("E2", "S6_SINGLE_URGENCY", "M1", s, "fulfillment_within_deadline", 0.70));

            rows.add(new TidyMetricRow("E2", "S7", "M1", s, "fulfillment_within_deadline", 0.90));
            rows.add(new TidyMetricRow("E2", "S7_NO_RESPONSIVENESS", "M1", s, "fulfillment_within_deadline", 0.82));
        }

        StatisticalAnalyzer analyzer = StatisticalAnalyzer.forExperiment("E2");
        List<StatisticalComparison> comparisons = analyzer.analyze(rows);

        // S6 vs 4 baselines + S7 vs 1 baseline = exactly 5 comparisons
        assertThat(comparisons).hasSize(5);

        List<StatisticalComparison> s6Comps = comparisons.stream()
            .filter(c -> c.candidateSystem().equals("S6"))
            .toList();
        assertThat(s6Comps).hasSize(4);

        List<String> s6BaselineNames = s6Comps.stream().map(StatisticalComparison::baselineSystem).toList();
        assertThat(s6BaselineNames).containsExactlyInAnyOrder(
            "S6_NO_PROXIMITY",
            "S6_NO_RARITY",
            "S6_NO_FRESHNESS",
            "S6_SINGLE_URGENCY"
        );

        List<StatisticalComparison> s7Comps = comparisons.stream()
            .filter(c -> c.candidateSystem().equals("S7"))
            .toList();
        assertThat(s7Comps).hasSize(1);
        assertThat(s7Comps.get(0).baselineSystem()).isEqualTo("S7_NO_RESPONSIVENESS");

        // Verify that all 5 comparisons yield IMPROVEMENT for candidate over ablated baseline
        for (StatisticalComparison sc : comparisons) {
            assertThat(sc.decision()).isEqualTo(ComparisonDecision.IMPROVEMENT);
            assertThat(sc.cliffsDelta()).isGreaterThan(0.0);
            assertThat(sc.wins()).isEqualTo(8);
            assertThat(sc.losses()).isEqualTo(0);
        }
    }
}

