package lifeline.eval.stats;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BootstrapConfidenceIntervalTest {

    @Test
    @DisplayName("Deterministic seeded bootstrap produces bitwise identical intervals")
    void testBootstrapReproducibility() {
        double[] sample = {12.0, 15.0, 14.0, 18.0, 22.0, 19.0, 25.0, 30.0, 28.0, 21.0};
        long seed = 12345L;

        ConfidenceInterval ci1 = BootstrapConfidenceInterval.bootstrapMean(sample, 0.95, 1000, seed);
        ConfidenceInterval ci2 = BootstrapConfidenceInterval.bootstrapMean(sample, 0.95, 1000, seed);

        assertThat(ci1.estimate()).isEqualTo(ci2.estimate());
        assertThat(ci1.lower()).isEqualTo(ci2.lower());
        assertThat(ci1.upper()).isEqualTo(ci2.upper());

        ConfidenceInterval medCi1 = BootstrapConfidenceInterval.bootstrapMedian(sample, 0.95, 1000, seed);
        ConfidenceInterval medCi2 = BootstrapConfidenceInterval.bootstrapMedian(sample, 0.95, 1000, seed);

        assertThat(medCi1.estimate()).isEqualTo(medCi2.estimate());
        assertThat(medCi1.lower()).isEqualTo(medCi2.lower());
        assertThat(medCi1.upper()).isEqualTo(medCi2.upper());
    }

    @Test
    @DisplayName("Confidence interval contains point estimate and respects expected ordering")
    void testIntervalBounds() {
        double[] sample = {10.0, 12.0, 14.0, 16.0, 18.0, 20.0};
        ConfidenceInterval ci = BootstrapConfidenceInterval.bootstrapMean(sample, 42L);

        double mean = BootstrapConfidenceInterval.computeMean(sample);
        assertThat(ci.estimate()).isEqualTo(mean);
        assertThat(ci.lower()).isLessThanOrEqualTo(mean);
        assertThat(ci.upper()).isGreaterThanOrEqualTo(mean);
        assertThat(ci.lower()).isLessThanOrEqualTo(ci.upper());
    }

    @Test
    @DisplayName("Sample with fewer than MIN_BOOTSTRAP_SAMPLE_SIZE returns NaN confidence bounds")
    void testSmallSampleReturnsNaNBounds() {
        double[] sample1 = {42.0};
        ConfidenceInterval ci1 = BootstrapConfidenceInterval.bootstrapMean(sample1, 42L);

        assertThat(ci1.estimate()).isEqualTo(42.0);
        assertThat(ci1.lower()).isNaN();
        assertThat(ci1.upper()).isNaN();

        double[] sample3 = {10.0, 20.0, 30.0};
        ConfidenceInterval ci3 = BootstrapConfidenceInterval.bootstrapMean(sample3, 42L);
        assertThat(ci3.estimate()).isEqualTo(20.0);
        assertThat(ci3.lower()).isNaN();
        assertThat(ci3.upper()).isNaN();

        double[] sample5 = {10.0, 20.0, 30.0, 40.0, 50.0};
        ConfidenceInterval ci5 = BootstrapConfidenceInterval.bootstrapMean(sample5, 42L);
        assertThat(ci5.estimate()).isEqualTo(30.0);
        assertThat(ci5.lower()).isNaN();
        assertThat(ci5.upper()).isNaN();
    }
}

