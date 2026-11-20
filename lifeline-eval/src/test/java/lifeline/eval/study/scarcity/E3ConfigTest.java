package lifeline.eval.study.scarcity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class E3ConfigTest {

    @Test
    @DisplayName("Load official configs/e3.yaml verifies all approved parameters")
    void testLoadOfficialConfig() {
        Path path = Path.of("configs", "e3.yaml");
        if (!java.nio.file.Files.exists(path)) {
            path = Path.of("..", "configs", "e3.yaml");
        }
        E3Config config = E3Config.load(path);

        assertThat(config.scarcityMultipliers()).containsExactly(1.0, 1.5, 2.0, 3.0);
        assertThat(config.systems()).containsExactly("S6", "S6_NO_RARITY");
        assertThat(config.responseModels()).containsExactly("M1", "M2", "M3");
        assertThat(config.seeds()).hasSize(30);
        assertThat(config.seeds().get(0)).isEqualTo(1L);
        assertThat(config.seeds().get(29)).isEqualTo(30L);
        assertThat(config.outputDir()).isEqualTo("results/e3-final");
    }

    @Test
    @DisplayName("Load configs/e3-smoke.yaml verifies smoke parameters")
    void testLoadSmokeConfig() {
        Path path = Path.of("configs", "e3-smoke.yaml");
        if (!java.nio.file.Files.exists(path)) {
            path = Path.of("..", "configs", "e3-smoke.yaml");
        }
        E3Config config = E3Config.load(path);

        assertThat(config.scarcityMultipliers()).containsExactly(1.0, 2.0);
        assertThat(config.systems()).containsExactly("S6", "S6_NO_RARITY");
        assertThat(config.responseModels()).containsExactly("M1");
        assertThat(config.seeds()).containsExactly(1001L);
        assertThat(config.outputDir()).isEqualTo("results/e3-smoke");
    }

    @Test
    @DisplayName("Invalid parameters in E3Config throw IllegalArgumentException")
    void testValidation() {
        assertThatThrownBy(() -> new E3Config(
            List.of(),
            List.of("S6"),
            List.of("M1"),
            List.of(1L),
            "results/e3",
            1
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new E3Config(
            List.of(1.0, -1.5),
            List.of("S6"),
            List.of("M1"),
            List.of(1L),
            "results/e3",
            1
        )).isInstanceOf(IllegalArgumentException.class);
    }
}

