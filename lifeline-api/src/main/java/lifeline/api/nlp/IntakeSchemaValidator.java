package lifeline.api.nlp;

import lifeline.api.dto.ParsedIntakeDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Strict schema validator and normalizer for parsed intake data (SRS NLP-3).
 * Never trusts raw LLM or external parsing output directly.
 */
@Component
public class IntakeSchemaValidator {

    private static final Set<String> CANONICAL_BLOOD_GROUPS = Set.of(
        "A_POS", "A_NEG", "B_POS", "B_NEG", "AB_POS", "AB_NEG", "O_POS", "O_NEG"
    );

    private static final Set<String> CANONICAL_URGENCIES = Set.of(
        "CRITICAL", "URGENT", "ROUTINE"
    );

    private final double confidenceThreshold;

    public IntakeSchemaValidator(
        @Value("${lifeline.nlp.confidence-threshold:0.60}") double confidenceThreshold
    ) {
        this.confidenceThreshold = confidenceThreshold;
    }

    /**
     * Validates and normalizes candidate parsed intake data against the strict domain schema.
     */
    public ParsedIntakeDto validateAndNormalize(
        String rawBloodGroup,
        String rawUrgency,
        String rawZone,
        Integer rawUnits,
        String rawContact,
        Double rawConfidence,
        String unparsedText,
        Map<String, Double> fieldConfidences
    ) {
        List<String> flags = new ArrayList<>();
        Map<String, Double> fConf = fieldConfidences != null ? new HashMap<>(fieldConfidences) : new HashMap<>();

        // 1. Validate Blood Group
        String normalizedBloodGroup = normalizeBloodGroup(rawBloodGroup);
        if (normalizedBloodGroup == null) {
            flags.add("INVALID_BLOOD_GROUP");
            fConf.put("bloodGroup", 0.0);
        } else {
            fConf.putIfAbsent("bloodGroup", 0.9);
        }

        // 2. Validate Urgency
        String normalizedUrgency = normalizeUrgency(rawUrgency);
        if (normalizedUrgency == null) {
            flags.add("INVALID_URGENCY");
            fConf.put("urgency", 0.0);
        } else {
            fConf.putIfAbsent("urgency", 0.85);
        }

        // 3. Validate Units
        Integer normalizedUnits = rawUnits;
        if (normalizedUnits == null) {
            normalizedUnits = 1; // Default assumption for single unit request
            flags.add("DEFAULT_UNITS_APPLIED");
            fConf.putIfAbsent("units", 0.5);
        } else if (normalizedUnits < 1 || normalizedUnits > 50) {
            flags.add("INVALID_UNITS");
            fConf.put("units", 0.0);
            normalizedUnits = null;
        } else {
            fConf.putIfAbsent("units", 0.95);
        }

        // 4. Validate Zone / Pincode
        String normalizedZone = normalizeZone(rawZone);
        if (normalizedZone == null || normalizedZone.isBlank()) {
            flags.add("MISSING_ZONE");
            fConf.put("zone", 0.0);
        } else {
            fConf.putIfAbsent("zone", 0.85);
        }

        // 5. Contact info sanitization (optional)
        String normalizedContact = sanitizeContact(rawContact);
        if (normalizedContact != null) {
            fConf.putIfAbsent("contact", 0.9);
        }

        // 6. Compute overall confidence if not provided or re-evaluate
        double confidence = rawConfidence != null ? Math.max(0.0, Math.min(1.0, rawConfidence)) : calculateAggregateConfidence(fConf);
        if (confidence < confidenceThreshold) {
            flags.add("LOW_CONFIDENCE");
        }

        boolean isValid = normalizedBloodGroup != null
            && normalizedUrgency != null
            && normalizedUnits != null
            && normalizedZone != null
            && !normalizedZone.isBlank()
            && confidence >= confidenceThreshold
            && !flags.contains("INVALID_BLOOD_GROUP")
            && !flags.contains("INVALID_URGENCY")
            && !flags.contains("INVALID_UNITS")
            && !flags.contains("MISSING_ZONE");

        boolean fallbackRecommended = !isValid;
        String fallbackReason = fallbackRecommended
            ? "Automated extraction incomplete or below confidence threshold. Flags: " + flags + ". Fallback to manual form."
            : null;

        return new ParsedIntakeDto(
            normalizedBloodGroup,
            normalizedUrgency,
            normalizedZone,
            normalizedUnits,
            normalizedContact,
            confidence,
            unparsedText,
            fConf,
            flags,
            isValid,
            fallbackRecommended,
            fallbackReason
        );
    }

    public static String normalizeBloodGroup(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.trim().toUpperCase()
            .replace("+", "_POS")
            .replace("-", "_NEG")
            .replace("POSITIVE", "_POS")
            .replace("NEGATIVE", "_NEG")
            .replace("POS", "_POS")
            .replace("NEG", "_NEG")
            .replaceAll("[^A-Z_]", "");

        // Remove double underscores if any
        while (cleaned.contains("__")) {
            cleaned = cleaned.replace("__", "_");
        }

        if (CANONICAL_BLOOD_GROUPS.contains(cleaned)) {
            return cleaned;
        }
        return null;
    }

    public static String normalizeUrgency(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String upper = raw.trim().toUpperCase();
        if (CANONICAL_URGENCIES.contains(upper)) {
            return upper;
        }
        if (upper.contains("CRITIC") || upper.contains("EMERGENC") || upper.contains("IMMEDIAT")) {
            return "CRITICAL";
        }
        if (upper.contains("URGENT") || upper.contains("ASAP") || upper.contains("SOON")) {
            return "URGENT";
        }
        if (upper.contains("ROUTINE") || upper.contains("ELECTIVE") || upper.contains("PLANNED") || upper.contains("SCHEDULED")) {
            return "ROUTINE";
        }
        return null;
    }

    private String normalizeZone(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.replaceAll("[<>{}\"';\r\n]", " ").trim();
        if (cleaned.length() > 50) {
            cleaned = cleaned.substring(0, 50).trim();
        }
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String sanitizeContact(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.replaceAll("[^0-9+()\\-\\s]", "").trim();
        if (cleaned.length() > 50) {
            cleaned = cleaned.substring(0, 50).trim();
        }
        return cleaned.isEmpty() ? null : cleaned;
    }

    private double calculateAggregateConfidence(Map<String, Double> fieldConfidences) {
        if (fieldConfidences.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (double val : fieldConfidences.values()) {
            sum += val;
        }
        return Math.round((sum / fieldConfidences.size()) * 100.0) / 100.0;
    }

    public double getConfidenceThreshold() {
        return confidenceThreshold;
    }
}
