package lifeline.eval.study.inventory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class E4ConfigTest {

    @Test
    @DisplayName("Load official configs/e4.yaml verifies all approved parameters")
    void testLoadOfficialConfig() throws IOException {
        Path path = Path.of("configs", "e4.yaml");
        if (!Files.exists(path)) {
            path = Path.of("..", "configs", "e4.yaml");
        }
        E4Config config = E4Config.load(path);

        assertThat(config.experiment()).isEqualTo("E4");
        assertThat(config.inventoryLevels()).containsExactly(0.0, 0.25, 0.50, 1.00);
        assertThat(config.systems()).containsExactly("S6_TIER0_ON", "S6_TIER0_OFF");
        assertThat(config.responseModels()).containsExactly("M1", "M2", "M3");
        assertThat(config.seeds()).hasSize(30);
        assertThat(config.seeds().get(0)).isEqualTo(1L);
        assertThat(config.seeds().get(29)).isEqualTo(30L);
        assertThat(config.outputDir()).isEqualTo("results/e4-final");
        assertThat(config.tier0RadiusKm()).isEqualTo(25.0);
    }

    @Test
    @DisplayName("Load configs/e4-smoke.yaml verifies smoke parameters")
    void testLoadSmokeConfig() throws IOException {
        Path path = Path.of("configs", "e4-smoke.yaml");
        if (!Files.exists(path)) {
            path = Path.of("..", "configs", "e4-smoke.yaml");
        }
        E4Config config = E4Config.load(path);

        assertThat(config.experiment()).isEqualTo("E4");
        assertThat(config.inventoryLevels()).containsExactly(0.0, 0.25, 0.50, 1.00);
        assertThat(config.systems()).containsExactly("S6_TIER0_ON", "S6_TIER0_OFF");
        assertThat(config.responseModels()).containsExactly("M1");
        assertThat(config.seeds()).containsExactly(1L);
        assertThat(config.outputDir()).isEqualTo("results/e4-smoke");
        assertThat(config.tier0RadiusKm()).isEqualTo(25.0);
    }

    @Test
    @DisplayName("Invalid parameters in E4Config throw IllegalArgumentException")
    void testValidation() {
        assertThatThrownBy(() -> new E4Config(
            "E4",
            List.of(),
            List.of("S6_TIER0_ON"),
            List.of("M1"),
            List.of(1L),
            "results/e4",
            1,
            25.0,
            null
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new E4Config(
            "E4",
            List.of(0.0, -0.25),
            List.of("S6_TIER0_ON"),
            List.of("M1"),
            List.of(1L),
            "results/e4",
            1,
            25.0,
            null
        )).isInstanceOf(IllegalArgumentException.class);
    }
}

