package lifeline.sim;

import lifeline.sim.calibration.CalibrationReport;
import lifeline.sim.model.SimulationParameters;
import lifeline.sim.world.World;
import lifeline.sim.world.WorldGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CalibrationReportTest {

    @Test
    @DisplayName("Calibration report computes metrics satisfying docs/PARAMETERS.md Section B checks")
    void calibrationReportMetricsSatisfySectionBChecks() {
        SimulationParameters params = SimulationParameters.loadDefault();
        WorldGenerator gen = new WorldGenerator(params);
        World world = gen.generate(42L);

        CalibrationReport.Metrics m = CalibrationReport.computeMetrics(world, params);

        // Check 1: Mean acceptance probability in 0.10 to 0.50 (P-14)
        assertThat(m.meanAcceptanceProbability())
            .as("Mean acceptance probability should be near target 0.30")
            .isBetween(0.15, 0.45);

        // Check 2: Intention-behaviour gap (Ou-Yang et al. 2020, P-16): no donor group near 100%
        assertThat(m.p90AcceptanceProbability())
            .as("90th percentile acceptance probability should stay well below 1.0 (intention-behaviour gap)")
            .isLessThan(0.75);

        assertThat(m.maxAcceptanceProbability())
            .as("Max acceptance probability should not reach 100%")
            .isLessThan(1.0);

        // Check 3: Top vs bottom willingness ratio ~1.96x (Wu et al. 2022, P-15)
        assertThat(m.topVsBottomWillingnessRatio())
            .as("Top vs bottom willingness ratio should be around ~1.5x - 4.5x")
            .isBetween(1.5, 4.5);

        // Check 4: Response delay median around 30 min (P-17)
        assertThat(m.medianResponseDelayMinutes())
            .as("Median response delay should be around 30 min")
            .isBetween(15.0, 60.0);

        // Check 5: Observable proxy lift over random (heuristic proxy, Phase 5 S5 will measure learned ranker)
        assertThat(m.observableProxyLiftOverRandom())
            .as("Observable proxy lift over random should be around 1.20x - 3.50x")
            .isBetween(1.20, 3.50);
    }

    @Test
    @DisplayName("Observable proxy ranking strictly depends on snapshot and cannot access latent willingness")
    void observableProxyCannotAccessLatentWillingness() {
        SimulationParameters params = SimulationParameters.loadDefault();
        WorldGenerator gen = new WorldGenerator(params);
        World world = gen.generate(42L);

        lifeline.core.domain.DonorSnapshot commonSnapshot = new lifeline.core.domain.DonorSnapshot(
            "DONOR-PROXY-TEST",
            lifeline.core.domain.BloodGroup.O_POS,
            lifeline.core.domain.Sex.MALE,
            "Z01",
            java.time.LocalDate.of(2026, 1, 1),
            lifeline.core.state.DonorState.AVAILABLE,
            java.util.Optional.empty(),
            new lifeline.core.domain.HistoryStats(10, 8, 1, 1, java.time.Instant.now())
        );

        // Two latent donors sharing the exact same observable snapshot, but opposite latent traits
        lifeline.sim.model.LatentDonor highWillingness = new lifeline.sim.model.LatentDonor(
            commonSnapshot, 0.99, 1.0, 10.0, 20.0, 0.95, 0.5
        );
        lifeline.sim.model.LatentDonor lowWillingness = new lifeline.sim.model.LatentDonor(
            commonSnapshot, 0.01, 0.5, 90.0, 5.0, 0.50, -0.5
        );

        double scoreHigh = CalibrationReport.scoreObservableProxy(
            highWillingness.snapshot(),
            "Z02",
            world.zoneMap(),
            params.travelToleranceKm()
        );
        double scoreLow = CalibrationReport.scoreObservableProxy(
            lowWillingness.snapshot(),
            "Z02",
            world.zoneMap(),
            params.travelToleranceKm()
        );

        // Scores MUST be completely identical because the proxy sees ONLY the observable snapshot
        assertThat(scoreHigh)
            .as("Observable proxy must not be influenced by unobservable latent willingness")
            .isEqualTo(scoreLow);
    }

    @Test
    @DisplayName("Calibration output explicitly identifies the observable proxy and distinguishes it from S5")
    void calibrationOutputDistinguishesObservableProxyFromS5() {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        java.io.PrintStream originalOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(out));
            CalibrationReport.Metrics m = new CalibrationReport.Metrics(
                0.30, 0.01, 0.05, 0.28, 0.60, 0.95, 3.52, 30.0, 2.35
            );
            CalibrationReport.printReport(m);
            String output = out.toString();

            assertThat(output).contains("Observable-Proxy Lift over Random");
            assertThat(output).contains("NOT the actual S5 learned ranker");
            assertThat(output).contains("S5 must be trained and measured in Phase 5");
        } finally {
            System.setOut(originalOut);
        }
    }
}
