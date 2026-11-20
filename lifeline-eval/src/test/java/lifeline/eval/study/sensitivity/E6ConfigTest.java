package lifeline.eval.study.sensitivity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class E6ConfigTest {

    @Test
    @DisplayName("Load official configs/e6.yaml verifies all approved parameters")
    void testLoadOfficialConfig() throws IOException {
        Path path = Path.of("configs", "e6.yaml");
        if (!Files.exists(path)) {
            path = Path.of("..", "configs", "e6.yaml");
        }
        E6Config config = E6Config.load(path);

        assertThat(config.experiment()).isEqualTo("E6");
        assertThat(config.candidateCount()).isEqualTo(20);
        assertThat(config.masterSamplingSeed()).isEqualTo(20261117L);
        assertThat(config.responseModels()).containsExactly("M1", "M2", "M3");
        assertThat(config.tuningSeeds()).hasSize(30);
        assertThat(config.tuningSeeds().get(0)).isEqualTo(1000L);
        assertThat(config.reportingSeeds()).hasSize(30);
        assertThat(config.reportingSeeds().get(0)).isEqualTo(1L);
        assertThat(config.outputDir()).isEqualTo("results/e6-final");
    }

    @Test
    @DisplayName("Load configs/e6-smoke.yaml verifies smoke parameters")
    void testLoadSmokeConfig() throws IOException {
        Path path = Path.of("configs", "e6-smoke.yaml");
        if (!Files.exists(path)) {
            path = Path.of("..", "configs", "e6-smoke.yaml");
        }
        E6Config config = E6Config.load(path);

        assertThat(config.experiment()).isEqualTo("E6");
        assertThat(config.candidateCount()).isEqualTo(2);
        assertThat(config.responseModels()).containsExactly("M1");
        assertThat(config.tuningSeeds()).containsExactly(1001L);
        assertThat(config.reportingSeeds()).containsExactly(1L);
        assertThat(config.outputDir()).isEqualTo("results/e6-smoke");
    }
}
