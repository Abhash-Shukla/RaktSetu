package lifeline.eval.demo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DemoDataExporterTest {

    @Test
    void exportsValidDemoSeedJson(@TempDir Path tempDir) throws IOException {
        Path seedFile = tempDir.resolve("demo_seed.json");
        DemoDataExporter.exportDemoData(42L, 50, seedFile);

        assertThat(Files.exists(seedFile)).isTrue();
        String content = Files.readString(seedFile);

        assertThat(content).contains("\"seed\": 42");
        assertThat(content).contains("\"banks\": [");
        assertThat(content).contains("\"donors\": [");
        assertThat(content).contains("\"donorId\":");
        assertThat(content).contains("\"bloodGroup\":");
        assertThat(content).contains("\"sex\":");
        assertThat(content).contains("\"stock\":");
    }
}
