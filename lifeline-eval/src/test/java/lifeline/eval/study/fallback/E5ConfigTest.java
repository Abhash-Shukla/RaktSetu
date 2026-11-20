package lifeline.eval.study.fallback;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class E5ConfigTest {

    @Test
    @DisplayName("Load official configs/e5.yaml verifies all approved parameters")
    void testLoadOfficialConfig() throws IOException {
        Path path = Path.of("configs", "e5.yaml");
        if (!Files.exists(path)) {
            path = Path.of("..", "configs", "e5.yaml");
        }
        E5Config config = E5Config.load(path);

        assertThat(config.experiment()).isEqualTo("E5");
        assertThat(config.attemptBudgets()).containsExactly(1, 5, 10, 15, 20);
        assertThat(config.systems()).containsExactly("S6_FALLBACK_ON", "S6_FALLBACK_OFF");
        assertThat(config.responseModels()).containsExactly("M1", "M2", "M3");
        assertThat(config.seeds()).hasSize(30);
        assertThat(config.seeds().get(0)).isEqualTo(1L);
        assertThat(config.seeds().get(29)).isEqualTo(30L);
        assertThat(config.outputDir()).isEqualTo("results/e5-final");
    }

    @Test
    @DisplayName("Load configs/e5-smoke.yaml verifies smoke parameters")
    void testLoadSmokeConfig() throws IOException {
        Path path = Path.of("configs", "e5-smoke.yaml");
        if (!Files.exists(path)) {
            path = Path.of("..", "configs", "e5-smoke.yaml");
        }
        E5Config config = E5Config.load(path);

        assertThat(config.experiment()).isEqualTo("E5");
        assertThat(config.attemptBudgets()).containsExactly(1, 10);
        assertThat(config.systems()).containsExactly("S6_FALLBACK_ON", "S6_FALLBACK_OFF");
        assertThat(config.responseModels()).containsExactly("M1");
        assertThat(config.seeds()).containsExactly(1001L);
        assertThat(config.outputDir()).isEqualTo("results/e5-smoke");
    }

    @Test
    @DisplayName("Invalid attempt budgets reject non-positive values")
    void testInvalidBudgets() {
        assertThatThrownBy(() -> new E5Config(
            "E5",
            List.of(0, 5),
            List.of("S6_FALLBACK_ON", "S6_FALLBACK_OFF"),
            List.of("M1"),
            List.of(1L),
            "results/test",
            1,
            null
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
