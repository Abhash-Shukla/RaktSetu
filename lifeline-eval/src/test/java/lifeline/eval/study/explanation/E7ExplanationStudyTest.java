package lifeline.eval.study.explanation;

import lifeline.core.config.UrgencyProfile;
import lifeline.core.domain.*;
import lifeline.core.explain.Explanation;
import lifeline.core.explain.ExplanationGenerator;
import lifeline.core.factor.*;
import lifeline.core.filter.CandidateFilter;
import lifeline.core.rank.RankedMatch;
import lifeline.core.rank.Ranker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class E7ExplanationStudyTest {

    @Test
    @DisplayName("ReasonCodeCatalog defines all 7 expected engine reason codes")
    void catalogDefinesAllSevenEngineReasonCodes() {
        Map<String, ReasonCodeCatalog.ReasonCodeSpec> codes = ReasonCodeCatalog.allKnownCodes();
        assertThat(codes).hasSize(7);
        assertThat(codes).containsKeys(
            ProximityFactor.REASON_CURRENT,
            ProximityFactor.REASON_HOME,
            ProximityFactor.REASON_STALE_LOCATION,
            RarityFactor.REASON_EXACT_MATCH,
            RarityFactor.REASON_RARE_TYPE_CONSERVED,
            ResponsivenessFactor.REASON_PRIOR_ESTIMATE,
            ResponsivenessFactor.REASON_SMOOTHED_RATE
        );

        for (var spec : codes.values()) {
            assertThat(spec.code()).isNotBlank();
            assertThat(spec.factorName()).isNotBlank();
            assertThat(spec.description()).isNotBlank();
        }
    }

    @Test
    @DisplayName("ReasonCodeCatalog accurately verifies explanation faithfulness and detects flag mismatches")
    void catalogVerifiesExplanationFaithfulness() {
        Instant now = Instant.parse("2026-11-06T12:00:00Z");
        BloodRequest req = new BloodRequest("REQ-1", BloodGroup.A_POS, 1, Urgency.URGENT, "Z1", now, now.plus(Duration.ofHours(6)));

        DonorSnapshot donor = new DonorSnapshot(
            "D-1", BloodGroup.A_POS, Sex.MALE, "Z1", LocalDate.of(2026, 1, 1),
            true, false, Optional.of(new CurrentLocation("Z1", now)),
            new HistoryStats(0, 0, 0, 0, null)
        );

        List<FactorContribution> contributions = List.of(
            FactorContribution.of(ProximityFactor.NAME, 0.9, 0.65, ProximityFactor.REASON_CURRENT),
            FactorContribution.of(RarityFactor.NAME, 0.0, 0.35, RarityFactor.REASON_EXACT_MATCH)
        );

        Explanation faithfulExplanation = ExplanationGenerator.generate(donor, req, contributions);
        List<String> errors = ReasonCodeCatalog.verifyExplanationFaithfulness(donor, req, contributions, faithfulExplanation);
        assertThat(errors).isEmpty();

        // Deliberately incorrect explanation with false staleLocationFlag
        Explanation corruptExplanation = new Explanation(
            faithfulExplanation.summary(),
            faithfulExplanation.topContributingFactor(),
            true, // Corrupted flag: claims stale location when fresh
            faithfulExplanation.rareTypeConservedFlag()
        );

        List<String> detectedErrors = ReasonCodeCatalog.verifyExplanationFaithfulness(donor, req, contributions, corruptExplanation);
        assertThat(detectedErrors).isNotEmpty();
        assertThat(detectedErrors.get(0)).contains("staleLocationFlag");
    }

    @Test
    @DisplayName("E7Config loads defaults and validates inputs")
    void e7ConfigValidation() {
        E7Config def = E7Config.defaultConfig();
        assertThat(def.seeds()).containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(def.numericalTolerance()).isEqualTo(1.0e-12);
        assertThat(def.topN()).isEqualTo(10);
        assertThat(def.includeTargetedCoverageCases()).isTrue();

        assertThatThrownBy(() -> new E7Config(List.of(1L), -1.0, "out", 10, true))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new E7Config(List.of(1L), 1e-12, "out", 0, true))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("E7ExplanationStudy runs and exports valid artifacts")
    void e7StudyExecutionAndExport(@TempDir Path tempDir) {
        E7ExplanationStudy study = new E7ExplanationStudy();
        E7Config config = new E7Config(
            List.of(1L), // Run on single reporting seed for test speed
            1.0e-12,
            tempDir.toString(),
            5,
            true // Includes targeted coverage cases
        );

        E7StudyResult result = study.run(config);

        // Verify Additivity
        assertThat(result.additivityResult().totalEvaluations()).isPositive();
        assertThat(result.additivityResult().maxAbsoluteError()).isLessThanOrEqualTo(1.0e-12);
        assertThat(result.additivityResult().violationCount()).isZero();
        assertThat(result.additivityResult().passed()).isTrue();

        // Verify Counterfactual Tests
        assertThat(result.counterfactualResult().top1FlipDenominator()).isPositive();
        assertThat(result.counterfactualResult().top1FlipRate()).isBetween(0.0, 1.0);
        assertThat(result.counterfactualResult().top1MeanRankDrop()).isPositive();
        assertThat(result.counterfactualResult().individualShiftDenominator()).isPositive();
        assertThat(result.counterfactualResult().definition()).contains("ablation");

        // Verify Reason Code Coverage (100% of all 7 engine codes)
        assertThat(result.coverageResult().totalCatalogCodes()).isEqualTo(7);
        assertThat(result.coverageResult().coveredCodes()).hasSize(7);
        assertThat(result.coverageResult().coverageRate()).isEqualTo(1.0);
        assertThat(result.coverageResult().unsupportedCodesFound()).isEmpty();
        assertThat(result.coverageResult().semanticVerificationErrors()).isEmpty();
        assertThat(result.coverageResult().passed()).isTrue();

        // Verify Sample Explanations
        assertThat(result.sampleExplanations()).isNotEmpty();
        SampleExplanation sample = result.sampleExplanations().get(0);
        String md = sample.toMarkdown();
        assertThat(md).contains("Sample Match Explanation Report");
        assertThat(md).contains("Factor Contributions");

        // Verify File Export
        study.exportResults(result, tempDir);

        Path additivityCsv = tempDir.resolve("e7_additivity.csv");
        Path counterfactualCsv = tempDir.resolve("e7_counterfactual.csv");
        Path reasonCodesCsv = tempDir.resolve("e7_reason_codes.csv");
        Path summaryJson = tempDir.resolve("e7_summary.json");
        Path sampleMd = tempDir.resolve("sample_explanation.md");

        assertThat(Files.exists(additivityCsv)).isTrue();
        assertThat(Files.exists(counterfactualCsv)).isTrue();
        assertThat(Files.exists(reasonCodesCsv)).isTrue();
        assertThat(Files.exists(summaryJson)).isTrue();
        assertThat(Files.exists(sampleMd)).isTrue();
    }

    @Test
    @DisplayName("Regression Test: Counterfactual CSV exporter separates top-1 drop from candidate-wide metrics")
    void counterfactualExporterDistinguishesTop1DropFromCandidateWideMetrics(@TempDir Path tempDir) throws Exception {
        E7ExplanationStudy study = new E7ExplanationStudy();

        ScoreAdditivityResult additivity = new ScoreAdditivityResult(100, 1e-13, 1e-14, 1e-12, 0, true);
        CounterfactualResult cfResult = new CounterfactualResult(
            "Test ablation",
            50, // top1FlipDenominator
            45, // top1FlipNumerator
            0.90, // top1FlipRate
            12.75, // top1MeanRankDrop (DISTINCT from candidate-wide)
            500, // individualShiftDenominator
            400, // individualRankChangedCount
            0.80, // individualRankChangeRate
            365.25, // meanRankShift (candidate-wide)
            310.50  // meanRankDrop (candidate-wide)
        );
        ReasonCodeCoverageResult coverage = new ReasonCodeCoverageResult(
            7, Set.of("C1", "C2", "C3", "C4", "C5", "C6", "C7"), 1.0,
            Map.of("C1", 10L), List.of(), List.of(), true
        );

        E7StudyResult studyResult = new E7StudyResult(
            Instant.now(),
            E7Config.defaultConfig(),
            additivity,
            cfResult,
            coverage,
            List.of()
        );

        study.exportResults(studyResult, tempDir);

        Path csvPath = tempDir.resolve("e7_counterfactual.csv");
        List<String> lines = Files.readAllLines(csvPath);
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).isEqualTo("metric,numerator,denominator,rate,mean_rank_shift,mean_rank_drop,definition");

        // Top-1 flip row must export top1MeanRankDrop (12.7500) and NOT candidate-wide shift/drop (365.2500 / 310.5000)
        String top1Line = lines.get(1);
        assertThat(top1Line).startsWith("top1_flip,45,50,0.900000,12.7500,12.7500,");

        // Candidate-wide row must export candidate-wide metrics (365.2500, 310.5000)
        String allLine = lines.get(2);
        assertThat(allLine).startsWith("all_candidates_shift,400,500,0.800000,365.2500,310.5000,");

        // Verify JSON export reflects top1_mean_rank_drop
        Path jsonPath = tempDir.resolve("e7_summary.json");
        String json = Files.readString(jsonPath);
        assertThat(json).contains("\"top1_mean_rank_drop\": 12.75");
        assertThat(json).contains("\"mean_rank_shift\": 365.25");
        assertThat(json).contains("\"mean_rank_drop\": 310.5");
    }

    @Test
    @DisplayName("Zero-denominator cases handle cleanly without division by zero")
    void zeroDenominatorHandlingReturnsZeroGracefully(@TempDir Path tempDir) throws Exception {
        E7ExplanationStudy study = new E7ExplanationStudy();

        CounterfactualResult cfResult = new CounterfactualResult(
            "Zero denominator test",
            0, 0, 0.0, 0.0,
            0, 0, 0.0, 0.0, 0.0
        );

        ScoreAdditivityResult additivity = new ScoreAdditivityResult(0, 0.0, 0.0, 1e-12, 0, true);
        ReasonCodeCoverageResult coverage = new ReasonCodeCoverageResult(
            7, Set.of(), 0.0, Map.of(), List.of(), List.of(), false
        );

        E7StudyResult studyResult = new E7StudyResult(
            Instant.now(),
            E7Config.defaultConfig(),
            additivity,
            cfResult,
            coverage,
            List.of()
        );

        study.exportResults(studyResult, tempDir);

        Path csvPath = tempDir.resolve("e7_counterfactual.csv");
        List<String> lines = Files.readAllLines(csvPath);
        assertThat(lines.get(1)).startsWith("top1_flip,0,0,0.000000,0.0000,0.0000,");
        assertThat(lines.get(2)).startsWith("all_candidates_shift,0,0,0.000000,0.0000,0.0000,");
    }
}
