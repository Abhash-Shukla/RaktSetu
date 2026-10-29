package lifeline.core.config;

import lifeline.core.domain.Sex;
import lifeline.core.domain.Urgency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YamlConfigLoaderTest {

    @Test
    @DisplayName("Load bundled default engine configuration from classpath")
    void loadDefaultConfig() {
        EngineConfig config = YamlConfigLoader.loadDefault();

        assertThat(config).isNotNull();
        assertThat(config.cooldown().cooldownDaysMale()).isEqualTo(90);
        assertThat(config.cooldown().cooldownDaysFemale()).isEqualTo(120);
        assertThat(config.cooldown().cooldownDaysFor(Sex.MALE)).isEqualTo(90);
        assertThat(config.cooldown().cooldownDaysFor(Sex.FEMALE)).isEqualTo(120);

        assertThat(config.geography().roadFactor()).isEqualTo(1.3);
        assertThat(config.geography().locationFreshnessHours()).isEqualTo(24);

        WeightProfile critical = config.urgencyProfiles().get(Urgency.CRITICAL);
        assertThat(critical.proximity()).isEqualTo(0.85);
        assertThat(critical.rarity()).isEqualTo(0.15);
        assertThat(critical.responsiveness()).isEqualTo(0.0);

        assertThat(config.selection().defaultTopN()).isEqualTo(5);
    }

    @Test
    @DisplayName("Load custom configuration from YAML string")
    void loadCustomConfig() {
        String yaml = """
            cooldown:
              cooldownDaysMale: 60
              cooldownDaysFemale: 90
            geography:
              roadFactor: 1.5
              locationFreshnessHours: 12
            urgencyProfiles:
              CRITICAL:
                proximity: 0.70
                rarity: 0.30
                responsiveness: 0.0
              URGENT:
                proximity: 0.50
                rarity: 0.50
                responsiveness: 0.0
              ROUTINE:
                proximity: 0.30
                rarity: 0.70
                responsiveness: 0.0
            selection:
              defaultTopN: 10
            """;

        EngineConfig config = YamlConfigLoader.load(yaml);

        assertThat(config.cooldown().cooldownDaysMale()).isEqualTo(60);
        assertThat(config.cooldown().cooldownDaysFemale()).isEqualTo(90);
        assertThat(config.geography().roadFactor()).isEqualTo(1.5);
        assertThat(config.geography().locationFreshnessHours()).isEqualTo(12);
        assertThat(config.selection().defaultTopN()).isEqualTo(10);
        assertThat(config.urgencyProfiles().get(Urgency.CRITICAL).proximity()).isEqualTo(0.70);
    }

    @Test
    @DisplayName("Invalid weights that do not sum to 1.0 throw IllegalArgumentException")
    void invalidWeightsThrow() {
        String yaml = """
            urgencyProfiles:
              CRITICAL:
                proximity: 0.80
                rarity: 0.50
                responsiveness: 0.0
            """;

        assertThatThrownBy(() -> YamlConfigLoader.load(yaml))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must sum to 1.0");
    }
}
