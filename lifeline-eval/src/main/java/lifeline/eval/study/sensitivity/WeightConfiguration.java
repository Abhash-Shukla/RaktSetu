package lifeline.eval.study.sensitivity;

import lifeline.core.config.UrgencyProfile;
import lifeline.core.config.WeightProfile;
import lifeline.core.domain.Urgency;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Encapsulates a sampled or reference weight configuration for Experiment E6.
 */
public record WeightConfiguration(
    String id,
    String description,
    Map<Urgency, WeightProfile> weights
) {
    public WeightConfiguration {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(weights, "weights cannot be null");
    }

    public UrgencyProfile toUrgencyProfile() {
        return new UrgencyProfile(weights);
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "    {\"id\": \"%s\", \"description\": \"%s\", \"weights\": {", id, description));
        boolean first = true;
        for (Map.Entry<Urgency, WeightProfile> e : weights.entrySet()) {
            if (!first) sb.append(", ");
            WeightProfile wp = e.getValue();
            sb.append(String.format(Locale.ROOT, "\"%s\": {\"proximity\": %.4f, \"rarity\": %.4f, \"responsiveness\": %.4f}",
                e.getKey(), wp.proximity(), wp.rarity(), wp.responsiveness()));
            first = false;
        }
        sb.append("}}");
        return sb.toString();
    }
}
