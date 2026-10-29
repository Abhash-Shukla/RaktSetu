package lifeline.core.config;

import lifeline.core.domain.Urgency;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Loads typed {@link EngineConfig} instances from YAML documents.
 * Pure Java implementation using SnakeYAML.
 */
public final class YamlConfigLoader {

    private static final String DEFAULT_CONFIG_RESOURCE = "/config/engine-config.yaml";

    private YamlConfigLoader() {
    }

    /**
     * Loads the default configuration bundled in the classpath.
     */
    public static EngineConfig loadDefault() {
        try (InputStream in = YamlConfigLoader.class.getResourceAsStream(DEFAULT_CONFIG_RESOURCE)) {
            if (in == null) {
                // If bundled resource not on classpath, return programmatic defaults
                return EngineConfig.defaults();
            }
            return load(in);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load default engine configuration from " + DEFAULT_CONFIG_RESOURCE, e);
        }
    }

    /**
     * Loads configuration from a YAML string.
     */
    public static EngineConfig load(String yamlContent) {
        Objects.requireNonNull(yamlContent, "yamlContent cannot be null");
        return load(new StringReader(yamlContent));
    }

    /**
     * Loads configuration from an {@link InputStream}.
     */
    public static EngineConfig load(InputStream inputStream) {
        Objects.requireNonNull(inputStream, "inputStream cannot be null");
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Map<String, Object> root = yaml.load(inputStream);
        return parseConfig(root);
    }

    /**
     * Loads configuration from a {@link Reader}.
     */
    public static EngineConfig load(Reader reader) {
        Objects.requireNonNull(reader, "reader cannot be null");
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Map<String, Object> root = yaml.load(reader);
        return parseConfig(root);
    }

    @SuppressWarnings("unchecked")
    private static EngineConfig parseConfig(Map<String, Object> root) {
        if (root == null || root.isEmpty()) {
            return EngineConfig.defaults();
        }

        // 1. Cooldown
        CooldownConfig cooldown = CooldownConfig.defaults();
        if (root.get("cooldown") instanceof Map<?, ?> cdMap) {
            int male = cdMap.get("cooldownDaysMale") instanceof Number n
                ? n.intValue() : CooldownConfig.DEFAULT_MALE_DAYS;
            int female = cdMap.get("cooldownDaysFemale") instanceof Number n
                ? n.intValue() : CooldownConfig.DEFAULT_FEMALE_DAYS;
            cooldown = new CooldownConfig(male, female);
        }

        // 2. Geography
        GeographyConfig geography = GeographyConfig.defaults();
        if (root.get("geography") instanceof Map<?, ?> geoMap) {
            double roadFactor = geoMap.get("roadFactor") instanceof Number n
                ? n.doubleValue() : GeographyConfig.DEFAULT_ROAD_FACTOR;
            int freshnessHours = geoMap.get("locationFreshnessHours") instanceof Number n
                ? n.intValue() : GeographyConfig.DEFAULT_FRESHNESS_HOURS;
            geography = new GeographyConfig(roadFactor, freshnessHours);
        }

        // 3. Urgency profiles
        Map<Urgency, WeightProfile> profiles = new EnumMap<>(Urgency.class);
        if (root.get("urgencyProfiles") instanceof Map<?, ?> upMap) {
            for (Map.Entry<?, ?> entry : upMap.entrySet()) {
                String key = String.valueOf(entry.getKey()).toUpperCase();
                Urgency urgency = Urgency.valueOf(key);
                if (entry.getValue() instanceof Map<?, ?> wMap) {
                    double prox = wMap.get("proximity") instanceof Number n ? n.doubleValue() : 0.0;
                    double rar = wMap.get("rarity") instanceof Number n ? n.doubleValue() : 0.0;
                    double resp = wMap.get("responsiveness") instanceof Number n ? n.doubleValue() : 0.0;
                    profiles.put(urgency, new WeightProfile(prox, rar, resp));
                }
            }
        }
        // Fill any missing with defaults
        EngineConfig defs = EngineConfig.defaults();
        for (Urgency u : Urgency.values()) {
            profiles.putIfAbsent(u, defs.urgencyProfiles().get(u));
        }

        // 4. Selection
        SelectionConfig selection = SelectionConfig.defaults();
        if (root.get("selection") instanceof Map<?, ?> selMap) {
            int topN = selMap.get("defaultTopN") instanceof Number n
                ? n.intValue() : SelectionConfig.DEFAULT_TOP_N;
            selection = new SelectionConfig(topN);
        }

        return new EngineConfig(cooldown, geography, profiles, selection);
    }
}
