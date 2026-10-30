package lifeline.eval.stats;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HolmCorrectionTest {

    @Test
    @DisplayName("Hand-computed 4-hypothesis test with monotonicity enforcement")
    void testHandComputedHolmAdjustment() {
        // Raw p-values: [0.01, 0.04, 0.03, 0.20]
        // Sorted:
        // k=1: p=0.01, mult=4 -> 0.04
        // k=2: p=0.03, mult=3 -> 0.09
        // k=3: p=0.04, mult=2 -> 0.08 -> monotonicity max(0.09, 0.08) = 0.09
        // k=4: p=0.20, mult=1 -> 0.20 -> max(0.09, 0.20) = 0.20
        // Expected adjusted: [0.04, 0.09, 0.09, 0.20]
        double[] raw = {0.01, 0.04, 0.03, 0.20};
        double[] adjusted = HolmCorrection.adjust(raw);

        assertThat(adjusted).hasSize(4);
        assertThat(adjusted[0]).isCloseTo(0.04, within(1e-9));
        assertThat(adjusted[1]).isCloseTo(0.09, within(1e-9));
        assertThat(adjusted[2]).isCloseTo(0.09, within(1e-9));
        assertThat(adjusted[3]).isCloseTo(0.20, within(1e-9));

        boolean[] rejected = HolmCorrection.isRejected(raw, 0.05);
        assertThat(rejected).containsExactly(true, false, false, false);
    }

    @Test
    @DisplayName("Adjusted values are strictly capped at 1.0")
    void testCapAtOne() {
        double[] raw = {0.60, 0.80};
        double[] adjusted = HolmCorrection.adjust(raw);

        // 0.60 * 2 = 1.2 -> capped at 1.0
        // 0.80 * 1 = 0.80 -> monotonic max(1.0, 0.80) = 1.0
        assertThat(adjusted[0]).isEqualTo(1.0);
        assertThat(adjusted[1]).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Single hypothesis preserves its p-value")
    void testSingleHypothesis() {
        double[] raw = {0.032};
        double[] adjusted = HolmCorrection.adjust(raw);
        assertThat(adjusted).containsExactly(0.032);
    }

    @Test
    @DisplayName("Empty array returns empty array")
    void testEmptyArray() {
        double[] raw = {};
        double[] adjusted = HolmCorrection.adjust(raw);
        assertThat(adjusted).isEmpty();
    }
}

