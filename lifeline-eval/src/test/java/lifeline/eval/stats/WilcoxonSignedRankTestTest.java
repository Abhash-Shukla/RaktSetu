package lifeline.eval.stats;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class WilcoxonSignedRankTestTest {

    @Test
    @DisplayName("Hand-computed textbook example with symmetric differences")
    void testHandComputedSymmetricTiedExample() {
        // Paired differences d_i:
        // +15, -7, +5, +20, -20, -9, +17, -12, +5, -10
        // |d| = 5, 5, 7, 9, 10, 12, 15, 17, 20, 20
        // Ranks: 1.5 (+), 1.5 (+), 3 (-), 4 (-), 5 (-), 6 (-), 7 (+), 8 (+), 9.5 (+), 9.5 (-)
        // W+ = 1.5 + 1.5 + 7 + 8 + 9.5 = 27.5
        // W- = 3 + 4 + 5 + 6 + 9.5 = 27.5
        // Total = 55. W = min(W+, W-) = 27.5. Two-sided p = 1.0 (exact symmetry)
        double[] x = {125, 115, 130, 140, 120, 115, 140, 125, 140, 135};
        double[] y = {110, 122, 125, 120, 140, 124, 123, 137, 135, 145};

        WilcoxonResult res = WilcoxonSignedRankTest.test(x, y);

        assertThat(res.nTotal()).isEqualTo(10);
        assertThat(res.nNonZero()).isEqualTo(10);
        assertThat(res.wPlus()).isEqualTo(27.5);
        assertThat(res.wMinus()).isEqualTo(27.5);
        assertThat(res.statistic()).isEqualTo(27.5);
        assertThat(res.pValue()).isEqualTo(1.0);
        assertThat(res.exact()).isTrue();
    }

    @Test
    @DisplayName("Hand-computed strictly positive differences: exact two-sided p = 2 * (1 / 2^N)")
    void testStrictlyPositiveDifferences() {
        // 8 pairs, all positive, no ties: W+ = 36, W- = 0
        // Exact two-sided p = 2 * 1 / 2^8 = 2 / 256 = 0.0078125
        double[] x = {10, 20, 30, 40, 50, 60, 70, 80};
        double[] y = {9,  18, 27, 35, 42, 50, 60, 70};

        WilcoxonResult res = WilcoxonSignedRankTest.test(x, y);

        assertThat(res.statistic()).isEqualTo(0.0);
        assertThat(res.wPlus()).isEqualTo(36.0);
        assertThat(res.wMinus()).isEqualTo(0.0);
        assertThat(res.pValue()).isCloseTo(2.0 / 256.0, within(1e-6));
        assertThat(res.exact()).isTrue();
    }

    @Test
    @DisplayName("N=5 Wilcoxon behavior: min exact two-sided p is 2/2^5 = 0.0625, strictly > 0.05 (cannot reject H0)")
    void testN5WilcoxonCannotAchieveSignificance() {
        // 5 strictly positive differences: W+ = 15, W- = 0
        // Min possible two-sided exact p-value is 2 / 2^5 = 2 / 32 = 0.0625
        double[] diffs = {1.0, 2.0, 3.0, 4.0, 5.0};
        WilcoxonResult res = WilcoxonSignedRankTest.testDifferences(diffs, false);

        assertThat(res.nTotal()).isEqualTo(5);
        assertThat(res.statistic()).isEqualTo(0.0);
        assertThat(res.wPlus()).isEqualTo(15.0);
        assertThat(res.wMinus()).isEqualTo(0.0);
        assertThat(res.pValue()).isCloseTo(2.0 / 32.0, within(1e-6));
        assertThat(res.pValue()).isEqualTo(0.0625);
        assertThat(res.pValue()).isGreaterThan(0.05); // Cannot achieve alpha = 0.05 significance!
    }

    @Test
    @DisplayName("N=6 Wilcoxon behavior: min exact two-sided p is 2/2^6 = 0.03125, strictly <= 0.05 (can reject H0)")
    void testN6ExactWilcoxonAchievesSignificance() {
        // 6 strictly positive differences: W+ = 21, W- = 0
        // Min possible two-sided exact p-value is 2 / 2^6 = 2 / 64 = 0.03125
        double[] diffs = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0};
        WilcoxonResult res = WilcoxonSignedRankTest.testDifferences(diffs, false);

        assertThat(res.nTotal()).isEqualTo(6);
        assertThat(res.statistic()).isEqualTo(0.0);
        assertThat(res.wPlus()).isEqualTo(21.0);
        assertThat(res.wMinus()).isEqualTo(0.0);
        assertThat(res.pValue()).isCloseTo(2.0 / 64.0, within(1e-6));
        assertThat(res.pValue()).isEqualTo(0.03125);
        assertThat(res.pValue()).isLessThan(0.05); // Can achieve alpha = 0.05 significance!
    }

    @Test
    @DisplayName("Zero differences are excluded from ranking while preserved in nTotal")
    void testZeroDifferencesHandling() {
        // 5 pairs: 2 zero differences, 3 non-zero: +1, +2, +3
        // Non-zero: W+ = 1 + 2 + 3 = 6, W- = 0. p = 2 * 1 / 2^3 = 2 / 8 = 0.25
        double[] x = {10, 20, 30, 40, 50};
        double[] y = {10, 18, 30, 38, 47};

        WilcoxonResult res = WilcoxonSignedRankTest.test(x, y);

        assertThat(res.nTotal()).isEqualTo(5);
        assertThat(res.nNonZero()).isEqualTo(3);
        assertThat(res.wPlus()).isEqualTo(6.0);
        assertThat(res.wMinus()).isEqualTo(0.0);
        assertThat(res.pValue()).isCloseTo(0.25, within(1e-6));
    }

    @Test
    @DisplayName("All zero differences yields p = 1.0 and statistic = 0.0")
    void testAllZeroDifferences() {
        double[] x = {10, 20, 30};
        double[] y = {10, 20, 30};

        WilcoxonResult res = WilcoxonSignedRankTest.test(x, y);

        assertThat(res.nTotal()).isEqualTo(3);
        assertThat(res.nNonZero()).isEqualTo(0);
        assertThat(res.statistic()).isEqualTo(0.0);
        assertThat(res.pValue()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Normal approximation with continuity correction for larger sample size N = 25")
    void testNormalApproximationForLargeSample() {
        int n = 25;
        double[] x = new double[n];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = i * 2.0;
            y[i] = i * 1.0; // All strictly positive
        }

        WilcoxonResult res = WilcoxonSignedRankTest.test(x, y);

        assertThat(res.exact()).isFalse();
        assertThat(res.nNonZero()).isEqualTo(24); // index 0 is 0 - 0 = 0
        assertThat(res.pValue()).isLessThan(0.001);
        assertThat(res.zScore()).isGreaterThan(4.0);
    }
}

