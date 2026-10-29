package lifeline.core.config;

import lifeline.core.domain.Urgency;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Maps request urgency tiers to their corresponding factor weight profiles.
 * <p>
 * Under AGENTS.md Section 1:
 * "Urgency selects a weight profile (it is not an additive per-donor term)."
 */
public final class UrgencyProfile {

    private final Map<Urgency, WeightProfile> profiles;

    public UrgencyProfile(Map<Urgency, WeightProfile> profiles) {
        Objects.requireNonNull(profiles, "profiles cannot be null");
        for (Urgency u : Urgency.values()) {
            if (!profiles.containsKey(u)) {
                throw new IllegalArgumentException("Missing weight profile for urgency: " + u);
            }
        }
        this.profiles = Collections.unmodifiableMap(new EnumMap<>(profiles));
    }

    /**
     * S6 Locked specification default weights per PARAMETERS.md P-28.
     * Proximity / Rarity (responsiveness disabled = 0.0).
     */
    public static UrgencyProfile lockedSpec() {
        Map<Urgency, WeightProfile> map = new EnumMap<>(Urgency.class);
        map.put(Urgency.CRITICAL, new WeightProfile(0.85, 0.15, 0.0));
        map.put(Urgency.URGENT, new WeightProfile(0.65, 0.35, 0.0));
        map.put(Urgency.ROUTINE, new WeightProfile(0.40, 0.60, 0.0));
        return new UrgencyProfile(map);
    }

    /**
     * S7 Extension specification weights per PARAMETERS.md P-28.
     * Proximity / Rarity / Responsiveness.
     */
    public static UrgencyProfile extensionSpec() {
        Map<Urgency, WeightProfile> map = new EnumMap<>(Urgency.class);
        map.put(Urgency.CRITICAL, new WeightProfile(0.60, 0.10, 0.30));
        map.put(Urgency.URGENT, new WeightProfile(0.45, 0.25, 0.30));
        map.put(Urgency.ROUTINE, new WeightProfile(0.30, 0.45, 0.25));
        return new UrgencyProfile(map);
    }

    /**
     * Creates an UrgencyProfile from loaded EngineConfig.
     */
    public static UrgencyProfile fromConfig(EngineConfig config) {
        Objects.requireNonNull(config, "config cannot be null");
        return new UrgencyProfile(config.urgencyProfiles());
    }

    /**
     * Returns the weight profile for the specified urgency level.
     */
    public WeightProfile weightsFor(Urgency urgency) {
        Objects.requireNonNull(urgency, "urgency cannot be null");
        return profiles.get(urgency);
    }
}
