package lifeline.eval.stats;

import lifeline.eval.metric.TidyMetricRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StatisticalAnalyzerTest {

    @Test
    @DisplayName("End-to-end statistical analysis: wins/losses/ties, Holm correction, and decision rule")
    void testEndToEndStatisticalAnalysis(@TempDir Path tempDir) throws Exception {
        List<TidyMetricRow> rows = new ArrayList<>();

        // 6 seeds comparing S6 vs S0 under M1 for fulfillment_within_deadline (higher is better)
        // Note: N=6 is the minimum sample size where exact Wilcoxon two-sided p < 0.05 (2 * 1 / 2^6 = 0.03125)
        long[] seeds = {1, 2, 3, 4, 5, 6};
        double[] s6Fulfill = {0.80, 0.85, 0.90, 0.75, 0.88, 0.82};
        double[] s0Fulfill = {0.10, 0.15, 0.12, 0.08, 0.14, 0.11};

        for (int i = 0; i < seeds.length; i++) {
            rows.add(new TidyMetricRow("E1", "S6", "M1", seeds[i], "fulfillment_within_deadline", s6Fulfill[i]));
            rows.add(new TidyMetricRow("E1", "S0", "M1", seeds[i], "fulfillment_within_deadline", s0Fulfill[i]));
        }

        // 6 seeds comparing S6 vs S0 under M1 for contacts_per_fulfilled_request (lower is better)
        // S6 has lower contacts across all 6 seeds -> 6 wins for S6
        double[] s6Contacts = {1.5, 1.8, 1.4, 2.0, 1.6, 1.7};
        double[] s0Contacts = {15.0, 18.0, 14.0, 20.0, 16.0, 17.0};

        for (int i = 0; i < seeds.length; i++) {
            rows.add(new TidyMetricRow("E1", "S6", "M1", seeds[i], "contacts_per_fulfilled_request", s6Contacts[i]));
            rows.add(new TidyMetricRow("E1", "S0", "M1", seeds[i], "contacts_per_fulfilled_request", s0Contacts[i]));
        }

        StatisticalAnalyzer analyzer = new StatisticalAnalyzer(
            0.05,
            42L,
            List.of("S6"),
            List.of("S0")
        );

        List<StatisticalComparison> comparisons = analyzer.analyze(rows);
        assertThat(comparisons).hasSize(2);

        // Verification of fulfillment metric
        StatisticalComparison fulfillComp = comparisons.stream()
            .filter(c -> c.metric().equals("fulfillment_within_deadline"))
            .findFirst()
            .orElseThrow();

        assertThat(fulfillComp.wins()).isEqualTo(6);
        assertThat(fulfillComp.losses()).isEqualTo(0);
        assertThat(fulfillComp.ties()).isEqualTo(0);
        assertThat(fulfillComp.hodgesLehmannShift()).isGreaterThan(0.60);
        assertThat(fulfillComp.cliffsDelta()).isEqualTo(1.0);
        assertThat(fulfillComp.cliffsMagnitude()).isEqualTo(EffectSizeCalculator.Magnitude.LARGE);
        assertThat(fulfillComp.rawPValue()).isLessThan(0.05);
        assertThat(fulfillComp.holmPValue()).isLessThan(0.05);
        assertThat(fulfillComp.decision()).isEqualTo(ComparisonDecision.IMPROVEMENT);

        // Verification of contacts metric (lower is better)
        StatisticalComparison contactsComp = comparisons.stream()
            .filter(c -> c.metric().equals("contacts_per_fulfilled_request"))
            .findFirst()
            .orElseThrow();

        assertThat(contactsComp.wins()).isEqualTo(6);
        assertThat(contactsComp.losses()).isEqualTo(0);
        assertThat(contactsComp.ties()).isEqualTo(0);
        assertThat(contactsComp.hodgesLehmannShift()).isLessThan(-10.0);
        assertThat(contactsComp.decision()).isEqualTo(ComparisonDecision.IMPROVEMENT);

        // Test CSV writing and loading
        Path csvPath = tempDir.resolve("stat_summary.csv");
        analyzer.writeCsv(comparisons, csvPath);
        assertThat(Files.exists(csvPath)).isTrue();
        List<String> lines = Files.readAllLines(csvPath);
        assertThat(lines.get(0)).startsWith("experiment,dataset_type,response_model");
        assertThat(lines).hasSize(3); // header + 2 comparisons

        // Test Markdown report generation
        String mdReport = analyzer.generateMarkdownReport(comparisons);
        assertThat(mdReport).contains("# LifeLine Evaluation :: Statistical Comparison Report");
        assertThat(mdReport).contains("INCOMPLETE REPORTING EVALUATION");
        assertThat(mdReport).contains("S6 vs S0");
        assertThat(mdReport).contains("fulfillment_within_deadline");
        assertThat(mdReport).contains("contacts_per_fulfilled_request");
        assertThat(mdReport).contains("**IMPROVEMENT**");
    }

    @Test
    @DisplayName("One-seed input: marks decision as INSUFFICIENT_DATA and omits inferential claims")
    void testOneSeedInput() {
        List<TidyMetricRow> rows = List.of(
            new TidyMetricRow("E1", "S6", "M1", 1001L, "fulfillment_within_deadline", 0.75),
            new TidyMetricRow("E1", "S0", "M1", 1001L, "fulfillment_within_deadline", 0.20)
        );

        StatisticalAnalyzer analyzer = new StatisticalAnalyzer(0.05, 42L, List.of("S6"), List.of("S0"));
        List<StatisticalComparison> comparisons = analyzer.analyze(rows);

        assertThat(comparisons).hasSize(1);
        StatisticalComparison comp = comparisons.get(0);

        assertThat(comp.sampleSize()).isEqualTo(1);
        assertThat(comp.decision()).isEqualTo(ComparisonDecision.INSUFFICIENT_DATA);
        assertThat(comp.rawPValue()).isNaN();
        assertThat(comp.holmPValue()).isNaN();
        assertThat(comp.candidateMeanCiLower()).isNaN();
        assertThat(comp.candidateMeanCiUpper()).isNaN();
        assertThat(comp.baselineMeanCiLower()).isNaN();
        assertThat(comp.baselineMeanCiUpper()).isNaN();
        assertThat(comp.hodgesLehmannShift()).isNaN();
        assertThat(comp.cliffsDelta()).isNaN();
        assertThat(comp.candidateMean()).isEqualTo(0.75);
        assertThat(comp.baselineMean()).isEqualTo(0.20);
        assertThat(comp.wins()).isEqualTo(1);

        String md = analyzer.generateMarkdownReport(comparisons);
        assertThat(md).contains("INSUFFICIENT SAMPLE SIZE FOR HYPOTHESIS TESTING");
        assertThat(md).contains("**INSUFFICIENT_DATA**");
    }

    @Test
    @DisplayName("Insufficient-seed input (N = 5 < 6): marks decision as INSUFFICIENT_DATA because min p = 0.0625 > 0.05")
    void testInsufficientSeedInput() {
        // N = 5 observations. Min possible two-sided exact p-value is 0.0625 > 0.05
        List<TidyMetricRow> rows = List.of(
            new TidyMetricRow("E1", "S6", "M1", 1001L, "fulfillment_within_deadline", 0.70),
            new TidyMetricRow("E1", "S0", "M1", 1001L, "fulfillment_within_deadline", 0.20),
            new TidyMetricRow("E1", "S6", "M1", 1002L, "fulfillment_within_deadline", 0.80),
            new TidyMetricRow("E1", "S0", "M1", 1002L, "fulfillment_within_deadline", 0.25),
            new TidyMetricRow("E1", "S6", "M1", 1003L, "fulfillment_within_deadline", 0.75),
            new TidyMetricRow("E1", "S0", "M1", 1003L, "fulfillment_within_deadline", 0.22),
            new TidyMetricRow("E1", "S6", "M1", 1004L, "fulfillment_within_deadline", 0.85),
            new TidyMetricRow("E1", "S0", "M1", 1004L, "fulfillment_within_deadline", 0.21),
            new TidyMetricRow("E1", "S6", "M1", 1005L, "fulfillment_within_deadline", 0.78),
            new TidyMetricRow("E1", "S0", "M1", 1005L, "fulfillment_within_deadline", 0.23)
        );

        StatisticalAnalyzer analyzer = new StatisticalAnalyzer(0.05, 42L, List.of("S6"), List.of("S0"));
        List<StatisticalComparison> comparisons = analyzer.analyze(rows);

        assertThat(comparisons).hasSize(1);
        StatisticalComparison comp = comparisons.get(0);
        assertThat(comp.sampleSize()).isEqualTo(5);
        assertThat(comp.decision()).isEqualTo(ComparisonDecision.INSUFFICIENT_DATA);
        assertThat(comp.rawPValue()).isNaN();
        assertThat(comp.holmPValue()).isNaN();
        assertThat(comp.candidateMeanCiLower()).isNaN();
    }

    @Test
    @DisplayName("Correct pairing of observations by seed regardless of row insertion order")
    void testCorrectPairingOfObservationsBySeed() {
        // Deliberately insert rows in mixed / reverse order with 6 seeds
        List<TidyMetricRow> rows = List.of(
            new TidyMetricRow("E1", "S0", "M1", 1003L, "fulfillment_within_deadline", 0.30),
            new TidyMetricRow("E1", "S6", "M1", 1001L, "fulfillment_within_deadline", 0.60),
            new TidyMetricRow("E1", "S0", "M1", 1001L, "fulfillment_within_deadline", 0.10),
            new TidyMetricRow("E1", "S6", "M1", 1005L, "fulfillment_within_deadline", 0.90),
            new TidyMetricRow("E1", "S0", "M1", 1002L, "fulfillment_within_deadline", 0.20),
            new TidyMetricRow("E1", "S6", "M1", 1004L, "fulfillment_within_deadline", 0.85),
            new TidyMetricRow("E1", "S0", "M1", 1005L, "fulfillment_within_deadline", 0.40),
            new TidyMetricRow("E1", "S6", "M1", 1002L, "fulfillment_within_deadline", 0.70),
            new TidyMetricRow("E1", "S0", "M1", 1004L, "fulfillment_within_deadline", 0.35),
            new TidyMetricRow("E1", "S6", "M1", 1003L, "fulfillment_within_deadline", 0.80),
            new TidyMetricRow("E1", "S6", "M1", 1006L, "fulfillment_within_deadline", 0.95),
            new TidyMetricRow("E1", "S0", "M1", 1006L, "fulfillment_within_deadline", 0.45)
        );

        StatisticalAnalyzer analyzer = new StatisticalAnalyzer(0.05, 42L, List.of("S6"), List.of("S0"));
        List<StatisticalComparison> comparisons = analyzer.analyze(rows);

        assertThat(comparisons).hasSize(1);
        StatisticalComparison comp = comparisons.get(0);

        // Every paired difference is strictly +0.50:
        assertThat(comp.sampleSize()).isEqualTo(6);
        assertThat(comp.wins()).isEqualTo(6);
        assertThat(comp.losses()).isEqualTo(0);
        assertThat(comp.ties()).isEqualTo(0);
        assertThat(comp.hodgesLehmannShift()).isEqualTo(0.50);
        assertThat(comp.cliffsDelta()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Missing seed records are omitted from pairing without fabricating values")
    void testMissingSeedRecords() {
        // S6 has seeds 1..7, S0 has seeds 1, 2, 4, 5, 6, 7 (seed 3 missing for S0)
        List<TidyMetricRow> rows = List.of(
            new TidyMetricRow("E1", "S6", "M1", 1L, "fulfillment_within_deadline", 0.80),
            new TidyMetricRow("E1", "S0", "M1", 1L, "fulfillment_within_deadline", 0.20),
            new TidyMetricRow("E1", "S6", "M1", 2L, "fulfillment_within_deadline", 0.80),
            new TidyMetricRow("E1", "S0", "M1", 2L, "fulfillment_within_deadline", 0.20),
            new TidyMetricRow("E1", "S6", "M1", 3L, "fulfillment_within_deadline", 0.80),
            // seed 3 missing for S0!
            new TidyMetricRow("E1", "S6", "M1", 4L, "fulfillment_within_deadline", 0.80),
            new TidyMetricRow("E1", "S0", "M1", 4L, "fulfillment_within_deadline", 0.20),
            new TidyMetricRow("E1", "S6", "M1", 5L, "fulfillment_within_deadline", 0.80),
            new TidyMetricRow("E1", "S0", "M1", 5L, "fulfillment_within_deadline", 0.20),
            new TidyMetricRow("E1", "S6", "M1", 6L, "fulfillment_within_deadline", 0.80),
            new TidyMetricRow("E1", "S0", "M1", 6L, "fulfillment_within_deadline", 0.20),
            new TidyMetricRow("E1", "S6", "M1", 7L, "fulfillment_within_deadline", 0.80),
            new TidyMetricRow("E1", "S0", "M1", 7L, "fulfillment_within_deadline", 0.20)
        );

        StatisticalAnalyzer analyzer = new StatisticalAnalyzer(0.05, 42L, List.of("S6"), List.of("S0"));
        List<StatisticalComparison> comparisons = analyzer.analyze(rows);

        assertThat(comparisons).hasSize(1);
        StatisticalComparison comp = comparisons.get(0);

        // Genuinely paired sample size must be strictly 6, not 7
        assertThat(comp.sampleSize()).isEqualTo(6);
        assertThat(comp.wins()).isEqualTo(6);
    }

    @Test
    @DisplayName("Duplicate seed records for same system and metric throw IllegalArgumentException")
    void testDuplicateSeedRecordsThrowsException() {
        List<TidyMetricRow> rows = List.of(
            new TidyMetricRow("E1", "S6", "M1", 1001L, "fulfillment_within_deadline", 0.80),
            new TidyMetricRow("E1", "S6", "M1", 1001L, "fulfillment_within_deadline", 0.85), // Duplicate!
            new TidyMetricRow("E1", "S0", "M1", 1001L, "fulfillment_within_deadline", 0.20)
        );

        StatisticalAnalyzer analyzer = new StatisticalAnalyzer();
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> analyzer.analyze(rows)
        );
    }

    @Test
    @DisplayName("Correct grouping: models, metrics, and systems are strictly partitioned without cross-contamination")
    void testCorrectGroupingByModelMetricSystem() {
        List<TidyMetricRow> rows = new ArrayList<>();
        long[] seeds = {1, 2, 3, 4, 5, 6};

        for (long s : seeds) {
            // M1 fulfillment
            rows.add(new TidyMetricRow("E1", "S6", "M1", s, "fulfillment_within_deadline", 0.90));
            rows.add(new TidyMetricRow("E1", "S0", "M1", s, "fulfillment_within_deadline", 0.20));
            // M1 contacts
            rows.add(new TidyMetricRow("E1", "S6", "M1", s, "contacts_per_fulfilled_request", 2.0));
            rows.add(new TidyMetricRow("E1", "S0", "M1", s, "contacts_per_fulfilled_request", 20.0));
            // M2 fulfillment
            rows.add(new TidyMetricRow("E1", "S6", "M2", s, "fulfillment_within_deadline", 0.85));
            rows.add(new TidyMetricRow("E1", "S0", "M2", s, "fulfillment_within_deadline", 0.15));
            // M2 contacts
            rows.add(new TidyMetricRow("E1", "S6", "M2", s, "contacts_per_fulfilled_request", 3.0));
            rows.add(new TidyMetricRow("E1", "S0", "M2", s, "contacts_per_fulfilled_request", 25.0));
        }

        StatisticalAnalyzer analyzer = new StatisticalAnalyzer(0.05, 42L, List.of("S6"), List.of("S0"));
        List<StatisticalComparison> comparisons = analyzer.analyze(rows);

        // Expect exactly 4 comparisons: (M1, fulfill), (M1, contacts), (M2, fulfill), (M2, contacts)
        assertThat(comparisons).hasSize(4);

        long m1FulfillCount = comparisons.stream()
            .filter(c -> c.responseModel().equals("M1") && c.metric().equals("fulfillment_within_deadline"))
            .count();
        long m2ContactsCount = comparisons.stream()
            .filter(c -> c.responseModel().equals("M2") && c.metric().equals("contacts_per_fulfilled_request"))
            .count();

        assertThat(m1FulfillCount).isEqualTo(1);
        assertThat(m2ContactsCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Six-seed tuning dataset: labeled as EXPLORATORY_TUNING and formatted as exploratory in report")
    void testSixSeedTuningDataset() {
        List<TidyMetricRow> rows = new ArrayList<>();
        long[] seeds = {1001, 1002, 1003, 1004, 1005, 1006};

        for (long s : seeds) {
            rows.add(new TidyMetricRow("E1", "S6", "M1", s, "fulfillment_within_deadline", 0.85));
            rows.add(new TidyMetricRow("E1", "S0", "M1", s, "fulfillment_within_deadline", 0.15));
        }

        StatisticalAnalyzer analyzer = new StatisticalAnalyzer(0.05, 42L, List.of("S6"), List.of("S0"));
        List<StatisticalComparison> comparisons = analyzer.analyze(rows);

        assertThat(comparisons).hasSize(1);
        StatisticalComparison comp = comparisons.get(0);
        assertThat(comp.datasetType()).isEqualTo(EvaluationDatasetType.EXPLORATORY_TUNING);
        assertThat(comp.decision()).isEqualTo(ComparisonDecision.IMPROVEMENT);

        String md = analyzer.generateMarkdownReport(comparisons);
        assertThat(md).contains("EXPLORATORY / TUNING EVALUATION");
        assertThat(md).contains("cited as pre-registered research conclusions");
    }

    @Test
    @DisplayName("Dataset containing all 30 reporting seeds: labeled as FINAL_REPORTING")
    void testDatasetContainingAll30ReportingSeeds() {
        List<TidyMetricRow> rows = new ArrayList<>();
        for (long s = 1; s <= 30; s++) {
            rows.add(new TidyMetricRow("E1", "S6", "M1", s, "fulfillment_within_deadline", 0.85));
            rows.add(new TidyMetricRow("E1", "S0", "M1", s, "fulfillment_within_deadline", 0.15));
        }

        StatisticalAnalyzer analyzer = new StatisticalAnalyzer(0.05, 42L, List.of("S6"), List.of("S0"));
        List<StatisticalComparison> comparisons = analyzer.analyze(rows);

        assertThat(comparisons).hasSize(1);
        StatisticalComparison comp = comparisons.get(0);
        assertThat(comp.datasetType()).isEqualTo(EvaluationDatasetType.FINAL_REPORTING);
        assertThat(comp.sampleSize()).isEqualTo(30);

        String md = analyzer.generateMarkdownReport(comparisons);
        assertThat(md).contains("PRE-REGISTERED PROTOCOL EVALUATION");
        assertThat(md).contains("Completed on all 30 reporting seeds (1-30)");
    }

    @Test
    @DisplayName("Mixed tuning and reporting seeds throw IllegalArgumentException")
    void testMixedTuningReportingSeedRejection() {
        List<TidyMetricRow> rows = List.of(
            new TidyMetricRow("E1", "S6", "M1", 1L, "fulfillment_within_deadline", 0.85),
            new TidyMetricRow("E1", "S6", "M1", 1001L, "fulfillment_within_deadline", 0.85),
            new TidyMetricRow("E1", "S0", "M1", 1L, "fulfillment_within_deadline", 0.15),
            new TidyMetricRow("E1", "S0", "M1", 1001L, "fulfillment_within_deadline", 0.15)
        );

        StatisticalAnalyzer analyzer = new StatisticalAnalyzer();
        IllegalArgumentException ex = org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> analyzer.analyze(rows)
        );
        assertThat(ex.getMessage()).contains("Mixed seed types detected");
    }

    @Test
    @DisplayName("Incomplete reporting seed sets (e.g. 6/30 seeds): labeled as INCOMPLETE_REPORTING")
    void testIncompleteReportingSeedSets() {
        List<TidyMetricRow> rows = new ArrayList<>();
        long[] seeds = {1, 2, 3, 4, 5, 6};

        for (long s : seeds) {
            rows.add(new TidyMetricRow("E1", "S6", "M1", s, "fulfillment_within_deadline", 0.85));
            rows.add(new TidyMetricRow("E1", "S0", "M1", s, "fulfillment_within_deadline", 0.15));
        }

        StatisticalAnalyzer analyzer = new StatisticalAnalyzer(0.05, 42L, List.of("S6"), List.of("S0"));
        List<StatisticalComparison> comparisons = analyzer.analyze(rows);

        assertThat(comparisons).hasSize(1);
        StatisticalComparison comp = comparisons.get(0);
        assertThat(comp.datasetType()).isEqualTo(EvaluationDatasetType.INCOMPLETE_REPORTING);
        assertThat(comp.sampleSize()).isEqualTo(6);

        String md = analyzer.generateMarkdownReport(comparisons);
        assertThat(md).contains("INCOMPLETE REPORTING EVALUATION");
        assertThat(md).contains("6 of 30 reporting seeds");
    }

    @Test
    @DisplayName("Correct labelling of exploratory versus final reporting in CSV and Markdown")
    void testCorrectLabellingOfExploratoryVersusFinalReportingOutput() {
        // Exploratory
        List<TidyMetricRow> expRows = new ArrayList<>();
        for (long s = 1001; s <= 1006; s++) {
            expRows.add(new TidyMetricRow("E1", "S6", "M1", s, "fulfillment_within_deadline", 0.80));
            expRows.add(new TidyMetricRow("E1", "S0", "M1", s, "fulfillment_within_deadline", 0.20));
        }
        StatisticalAnalyzer analyzer = new StatisticalAnalyzer(0.05, 42L, List.of("S6"), List.of("S0"));
        List<StatisticalComparison> expComps = analyzer.analyze(expRows);
        assertThat(expComps.get(0).toCsvLine()).contains("EXPLORATORY_TUNING");

        // Final
        List<TidyMetricRow> finalRows = new ArrayList<>();
        for (long s = 1; s <= 30; s++) {
            finalRows.add(new TidyMetricRow("E1", "S6", "M1", s, "fulfillment_within_deadline", 0.80));
            finalRows.add(new TidyMetricRow("E1", "S0", "M1", s, "fulfillment_within_deadline", 0.20));
        }
        List<StatisticalComparison> finalComps = analyzer.analyze(finalRows);
        assertThat(finalComps.get(0).toCsvLine()).contains("FINAL_REPORTING");
    }

    @Test
    @DisplayName("Holm correction across 5 baselines: N=6 raw p < 0.05 becomes Holm p = 0.15625 > 0.05, while N=8 achieves Holm p <= 0.05")
    void testHolmCorrectionAcrossFiveBaselinesAtN6AndN8() {
        List<String> baselines = List.of("S0", "S2", "S3", "S4", "S5");

        // Case 1: N = 6 tuning seeds
        List<TidyMetricRow> n6Rows = new ArrayList<>();
        long[] n6Seeds = {1001, 1002, 1003, 1004, 1005, 1006};
        for (long s : n6Seeds) {
            n6Rows.add(new TidyMetricRow("E1", "S6", "M1", s, "fulfillment_within_deadline", 0.90));
            for (String b : baselines) {
                n6Rows.add(new TidyMetricRow("E1", b, "M1", s, "fulfillment_within_deadline", 0.20));
            }
        }

        StatisticalAnalyzer analyzerN6 = new StatisticalAnalyzer(0.05, 42L, List.of("S6"), baselines);
        List<StatisticalComparison> n6Comps = analyzerN6.analyze(n6Rows);
        assertThat(n6Comps).hasSize(5);

        for (StatisticalComparison comp : n6Comps) {
            // Raw p is 2 / 2^6 = 0.03125 (< 0.05)
            assertThat(comp.rawPValue()).isCloseTo(0.03125, org.assertj.core.data.Offset.offset(1e-4));
            // After Holm correction across m=5 baselines: min Holm p is 5 * 0.03125 = 0.15625 (> 0.05)
            assertThat(comp.holmPValue()).isGreaterThanOrEqualTo(0.1562);
            // Decision is NO_MEANINGFUL_DIFFERENCE because Holm-adjusted p >= 0.05
            assertThat(comp.decision()).isEqualTo(ComparisonDecision.NO_MEANINGFUL_DIFFERENCE);
        }

        // Case 2: N = 8 tuning seeds
        List<TidyMetricRow> n8Rows = new ArrayList<>();
        long[] n8Seeds = {1001, 1002, 1003, 1004, 1005, 1006, 1007, 1008};
        for (long s : n8Seeds) {
            n8Rows.add(new TidyMetricRow("E1", "S6", "M1", s, "fulfillment_within_deadline", 0.90));
            for (String b : baselines) {
                n8Rows.add(new TidyMetricRow("E1", b, "M1", s, "fulfillment_within_deadline", 0.20));
            }
        }

        StatisticalAnalyzer analyzerN8 = new StatisticalAnalyzer(0.05, 42L, List.of("S6"), baselines);
        List<StatisticalComparison> n8Comps = analyzerN8.analyze(n8Rows);
        assertThat(n8Comps).hasSize(5);

        for (StatisticalComparison comp : n8Comps) {
            // Raw p is 2 / 2^8 = 0.0078125
            assertThat(comp.rawPValue()).isCloseTo(0.0078125, org.assertj.core.data.Offset.offset(1e-4));
            // After Holm correction across m=5: Holm p is 5 * 0.0078125 = 0.0390625 (<= 0.05)
            assertThat(comp.holmPValue()).isCloseTo(0.0390625, org.assertj.core.data.Offset.offset(1e-4));
            // Decision is IMPROVEMENT
            assertThat(comp.decision()).isEqualTo(ComparisonDecision.IMPROVEMENT);
        }
    }

    @Test
    @DisplayName("S6 and S7 are corrected in separate hypothesis families of m=5, not a pooled family of m=10")
    void testS6AndS7AreCorrectedInSeparateHypothesisFamilies() {
        List<String> baselines = List.of("S0", "S2", "S3", "S4", "S5");
        long[] seeds = {1001, 1002, 1003, 1004, 1005, 1006, 1007, 1008};

        List<TidyMetricRow> rows = new ArrayList<>();
        for (long s : seeds) {
            rows.add(new TidyMetricRow("E1", "S6", "M1", s, "fulfillment_within_deadline", 0.90));
            rows.add(new TidyMetricRow("E1", "S7", "M1", s, "fulfillment_within_deadline", 0.92));
            for (String b : baselines) {
                rows.add(new TidyMetricRow("E1", b, "M1", s, "fulfillment_within_deadline", 0.20));
            }
        }

        // Uses default candidates (S6, S7) and baselines (S0, S2, S3, S4, S5)
        StatisticalAnalyzer analyzer = new StatisticalAnalyzer();
        List<StatisticalComparison> comparisons = analyzer.analyze(rows);

        // 5 comparisons for S6 + 5 comparisons for S7 = 10 total comparisons
        assertThat(comparisons).hasSize(10);

        List<StatisticalComparison> s6Comps = comparisons.stream()
            .filter(c -> c.candidateSystem().equals("S6"))
            .toList();
        List<StatisticalComparison> s7Comps = comparisons.stream()
            .filter(c -> c.candidateSystem().equals("S7"))
            .toList();

        assertThat(s6Comps).hasSize(5);
        assertThat(s7Comps).hasSize(5);

        // Because families are separate (m=5 each), Holm multiplier is 5 (giving ~0.03906 <= 0.05)
        // If they had been incorrectly pooled (m=10), Holm multiplier would be 10 (giving ~0.078125 > 0.05)
        for (StatisticalComparison c : s6Comps) {
            assertThat(c.holmPValue()).isCloseTo(0.0390625, org.assertj.core.data.Offset.offset(1e-4));
            assertThat(c.decision()).isEqualTo(ComparisonDecision.IMPROVEMENT);
        }
        for (StatisticalComparison c : s7Comps) {
            assertThat(c.holmPValue()).isCloseTo(0.0390625, org.assertj.core.data.Offset.offset(1e-4));
            assertThat(c.decision()).isEqualTo(ComparisonDecision.IMPROVEMENT);
        }
    }
}
