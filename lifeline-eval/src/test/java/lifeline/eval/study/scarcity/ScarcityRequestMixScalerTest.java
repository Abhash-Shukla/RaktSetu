package lifeline.eval.study.scarcity;

import lifeline.core.domain.BloodGroup;
import lifeline.eval.metric.MetricCalculator;
import lifeline.eval.runner.ExperimentRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ScarcityRequestMixScalerTest {

    private Map<BloodGroup, Double> baseFrequencies;

    @BeforeEach
    void setUp() {
        baseFrequencies = ExperimentRunner.loadPopulationFrequencies();
    }

    @Test
    @DisplayName("Baseline frequencies sum to 1.0 and match expected Indian population totals")
    void baselineFrequenciesValid() {
        double sum = baseFrequencies.values().stream().mapToDouble(Double::doubleValue).sum();
        assertThat(sum).isCloseTo(1.0, within(1e-5));

        double rareMass = baseFrequencies.entrySet().stream()
            .filter(e -> MetricCalculator.isRareGroup(e.getKey()))
            .mapToDouble(Map.Entry::getValue)
            .sum();
        // Indian Rh-negative share is ~5.87% (Patidar & Dhiman 2021)
        assertThat(rareMass).isCloseTo(0.058701, within(1e-4));
    }

    @ParameterizedTest(name = "alpha = {0}")
    @ValueSource(doubles = {1.0, 1.5, 2.0, 3.0})
    @DisplayName("Approved E3 multipliers scale rare groups and normalize common groups correctly")
    void testApprovedMultipliers(double alpha) {
        Map<BloodGroup, Double> scaled = ScarcityRequestMixScaler.scale(baseFrequencies, alpha);

        assertThat(scaled).hasSize(8);

        double scaledSum = 0.0;
        double baseRareSum = 0.0;
        double scaledRareSum = 0.0;
        double baseCommonSum = 0.0;
        double scaledCommonSum = 0.0;

        for (BloodGroup bg : BloodGroup.values()) {
            double pBase = baseFrequencies.get(bg);
            double pScaled = scaled.get(bg);

            assertThat(pScaled).isGreaterThan(0.0);
            scaledSum += pScaled;

            if (MetricCalculator.isRareGroup(bg)) {
                baseRareSum += pBase;
                scaledRareSum += pScaled;
                // Rare group must scale exactly by alpha
                assertThat(pScaled).isCloseTo(alpha * pBase, within(1e-9));
            } else {
                baseCommonSum += pBase;
                scaledCommonSum += pScaled;
            }
        }

        // Total probability must equal 1.0
        assertThat(scaledSum).isCloseTo(1.0, within(1e-6));

        // Rare mass must scale by alpha
        assertThat(scaledRareSum).isCloseTo(alpha * baseRareSum, within(1e-6));

        // Common mass must equal 1.0 - alpha * baseRareSum
        assertThat(scaledCommonSum).isCloseTo(1.0 - alpha * baseRareSum, within(1e-6));
    }

    @Test
    @DisplayName("Multiplier 1.0 returns exact base frequencies")
    void testIdentityMultiplier() {
        Map<BloodGroup, Double> scaled = ScarcityRequestMixScaler.scale(baseFrequencies, 1.0);
        for (BloodGroup bg : BloodGroup.values()) {
            assertThat(scaled.get(bg)).isCloseTo(baseFrequencies.get(bg), within(1e-9));
        }
    }

    @Test
    @DisplayName("Null input frequency map throws NullPointerException")
    void testNullBaseFrequencies() {
        assertThatThrownBy(() -> ScarcityRequestMixScaler.scale(null, 1.5))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Missing blood group in base frequencies throws IllegalArgumentException")
    void testMissingBloodGroup() {
        Map<BloodGroup, Double> incomplete = new EnumMap<>(baseFrequencies);
        incomplete.remove(BloodGroup.AB_NEG);

        assertThatThrownBy(() -> ScarcityRequestMixScaler.scale(incomplete, 1.5))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Missing base frequency for blood group: AB-");
    }

    @Test
    @DisplayName("Invalid alpha values throw IllegalArgumentException")
    void testInvalidAlpha() {
        assertThatThrownBy(() -> ScarcityRequestMixScaler.scale(baseFrequencies, 0.0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("alpha must be positive");

        assertThatThrownBy(() -> ScarcityRequestMixScaler.scale(baseFrequencies, -1.0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("alpha must be positive");

        assertThatThrownBy(() -> ScarcityRequestMixScaler.scale(baseFrequencies, Double.NaN))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> ScarcityRequestMixScaler.scale(baseFrequencies, Double.POSITIVE_INFINITY))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Base frequencies not summing to 1.0 throw IllegalArgumentException")
    void testNonNormalizedBaseFrequencies() {
        Map<BloodGroup, Double> unnormalized = new EnumMap<>(baseFrequencies);
        unnormalized.put(BloodGroup.O_POS, 0.90); // causes sum > 1.5

        assertThatThrownBy(() -> ScarcityRequestMixScaler.scale(unnormalized, 1.5))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must sum to 1.0");
    }

    @Test
    @DisplayName("Alpha exceeding rare capacity (alpha * f_rare >= 1.0) throws IllegalArgumentException")
    void testExcessiveAlpha() {
        // base rare mass ~0.0587; alpha = 20.0 yields rare mass 1.174 > 1.0
        assertThatThrownBy(() -> ScarcityRequestMixScaler.scale(baseFrequencies, 20.0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(">= 1.0");
    }
}
