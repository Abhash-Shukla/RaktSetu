package lifeline.eval.study.sensitivity;

import lifeline.core.config.WeightProfile;
import lifeline.core.domain.Urgency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class E6WeightPerturberTest {

    @Test
    @DisplayName("Generate K = 20 configurations deterministically")
    void testDeterministicGeneration() {
        List<WeightConfiguration> set1 = E6WeightPerturber.generateConfigurations(20261117L, 20);
        List<WeightConfiguration> set2 = E6WeightPerturber.generateConfigurations(20261117L, 20);

        // 1 default + 20 alternative configurations = 21 total
        assertThat(set1).hasSize(21);
        assertThat(set1.get(0).id()).isEqualTo("S6_DEFAULT");

        for (int i = 0; i < set1.size(); i++) {
            WeightConfiguration c1 = set1.get(i);
            WeightConfiguration c2 = set2.get(i);
            assertThat(c1.id()).isEqualTo(c2.id());

            for (Urgency u : Urgency.values()) {
                WeightProfile wp1 = c1.weights().get(u);
                WeightProfile wp2 = c2.weights().get(u);

                assertThat(wp1.proximity()).isEqualTo(wp2.proximity());
                assertThat(wp1.rarity()).isEqualTo(wp2.rarity());
                assertThat(wp1.responsiveness()).isEqualTo(0.0);

                // All weights must sum to 1.0 within 1e-6
                assertThat(wp1.proximity() + wp1.rarity()).isBetween(0.999999, 1.000001);
                assertThat(wp1.proximity()).isGreaterThanOrEqualTo(0.0);
                assertThat(wp1.rarity()).isGreaterThanOrEqualTo(0.0);
            }
        }
    }
}
