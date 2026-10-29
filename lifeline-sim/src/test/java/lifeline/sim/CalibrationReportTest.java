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

        // Check 5: Learned ranker lift over random (Wu et al. 2022)
        assertThat(m.rankerVsRandomLift())
            .as("Ranker lift over random should be around 1.25x - 3.0x")
            .isBetween(1.20, 3.50);
    }
}
