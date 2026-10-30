package lifeline.eval.stats;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class EffectSizeCalculatorTest {

    @Test
    @DisplayName("Hand-computed Hodges-Lehmann pseudo-median from Walsh averages")
    void testHandComputedHodgesLehmannShift() {
        // diffs = [1.0, 2.0, 3.0]
        // Walsh averages:
        // (1+1)/2 = 1.0, (1+2)/2 = 1.5, (1+3)/2 = 2.0
        // (2+2)/2 = 2.0, (2+3)/2 = 2.5
        // (3+3)/2 = 3.0
        // Sorted: [1.0, 1.5, 2.0, 2.0, 2.5, 3.0]
        // Median = (2.0 + 2.0) / 2 = 2.0
        double[] diffs = {1.0, 2.0, 3.0};
        double hl = EffectSizeCalculator.hodgesLehmannShiftFromDifferences(diffs);

        assertThat(hl).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Hand-computed Hodges-Lehmann with odd number of Walsh averages")
    void testHodgesLehmannOddWalshAverages() {
        // diffs = [2.0, 6.0]
        // Walsh: (2+2)/2 = 2.0, (2+6)/2 = 4.0, (6+6)/2 = 6.0
        // Median = 4.0
        double[] x = {12.0, 16.0};
        double[] y = {10.0, 10.0};
        double hl = EffectSizeCalculator.hodgesLehmannShift(x, y);

        assertThat(hl).isEqualTo(4.0);
    }

    @Test
    @DisplayName("Hand-computed Cliff's delta")
    void testHandComputedCliffsDelta() {
        // x = [1, 2, 3], y = [2, 3, 4]
        // Comparisons:
        // 1 vs (2,3,4): -3
        // 2 vs (2,3,4): -2 (tie with 2)
        // 3 vs (2,3,4): -1 (tie with 3, greater than 2: +1 - 1 = 0)
        // sum = -3 - 2 + 0 = -5. n_x * n_y = 9.
        // delta = -5 / 9 = -0.555555... -> LARGE magnitude
        double[] x = {1.0, 2.0, 3.0};
        double[] y = {2.0, 3.0, 4.0};

        EffectSizeCalculator.CliffsDeltaResult res = EffectSizeCalculator.cliffsDelta(x, y);

        assertThat(res.delta()).isCloseTo(-5.0 / 9.0, within(1e-6));
        assertThat(res.magnitude()).isEqualTo(EffectSizeCalculator.Magnitude.LARGE);
    }

    @Test
    @DisplayName("Identical distributions produce Cliff's delta = 0.0 (NEGLIGIBLE)")
    void testIdenticalDistributionsCliffsDelta() {
        double[] x = {5.0, 10.0, 15.0};
        double[] y = {5.0, 10.0, 15.0};

        EffectSizeCalculator.CliffsDeltaResult res = EffectSizeCalculator.cliffsDelta(x, y);

        assertThat(res.delta()).isEqualTo(0.0);
        assertThat(res.magnitude()).isEqualTo(EffectSizeCalculator.Magnitude.NEGLIGIBLE);
    }

    @Test
    @DisplayName("Cliff's delta magnitude thresholds classification")
    void testMagnitudeClassification() {
        assertThat(EffectSizeCalculator.classifyMagnitude(0.10)).isEqualTo(EffectSizeCalculator.Magnitude.NEGLIGIBLE);
        assertThat(EffectSizeCalculator.classifyMagnitude(-0.14)).isEqualTo(EffectSizeCalculator.Magnitude.NEGLIGIBLE);
        assertThat(EffectSizeCalculator.classifyMagnitude(0.20)).isEqualTo(EffectSizeCalculator.Magnitude.SMALL);
        assertThat(EffectSizeCalculator.classifyMagnitude(0.40)).isEqualTo(EffectSizeCalculator.Magnitude.MEDIUM);
        assertThat(EffectSizeCalculator.classifyMagnitude(0.60)).isEqualTo(EffectSizeCalculator.Magnitude.LARGE);
    }
}

