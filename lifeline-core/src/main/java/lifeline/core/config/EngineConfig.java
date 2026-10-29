package lifeline.core.config;

import lifeline.core.domain.Urgency;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Complete immutable configuration for the LifeLine matching engine.
 *
 * @param cooldown        cooldown rules for male and female donors
 * @param geography       road multiplier and location freshness settings
 * @param urgencyProfiles weight profiles for CRITICAL, URGENT, and ROUTINE tiers
 * @param selection       top-N display settings
 */
public record EngineConfig(
    CooldownConfig cooldown,
    GeographyConfig geography,
    Map<Urgency, WeightProfile> urgencyProfiles,
    SelectionConfig selection
) {
    public EngineConfig {
        Objects.requireNonNull(cooldown, "cooldown cannot be null");
        Objects.requireNonNull(geography, "geography cannot be null");
        Objects.requireNonNull(urgencyProfiles, "urgencyProfiles cannot be null");
        Objects.requireNonNull(selection, "selection cannot be null");

        for (Urgency u : Urgency.values()) {
            if (!urgencyProfiles.containsKey(u)) {
                throw new IllegalArgumentException("Missing weight profile for urgency: " + u);
            }
        }
        urgencyProfiles = Collections.unmodifiableMap(new EnumMap<>(urgencyProfiles));
    }

    /**
     * Locked default engine configuration per SRS Section 8.4 and PARAMETERS.md.
     */
    public static EngineConfig defaults() {
        Map<Urgency, WeightProfile> profiles = new EnumMap<>(Urgency.class);
        // S6 locked-spec defaults (proximity / rarity / responsiveness):
        profiles.put(Urgency.CRITICAL, new WeightProfile(0.85, 0.15, 0.0));
        profiles.put(Urgency.URGENT, new WeightProfile(0.65, 0.35, 0.0));
        profiles.put(Urgency.ROUTINE, new WeightProfile(0.40, 0.60, 0.0));

        return new EngineConfig(
            CooldownConfig.defaults(),
            GeographyConfig.defaults(),
            profiles,
            SelectionConfig.defaults()
        );
    }
}
