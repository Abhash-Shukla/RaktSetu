package lifeline.api.nlp;

import lifeline.api.dto.ParsedIntakeDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, offline fake implementation of IntakeParser.
 * Operates without network access or API keys, using deterministic regex extraction.
 * Guarantees reproducibility and offline testing compliance.
 */
public class DeterministicFakeIntakeParser implements IntakeParser {

    private static final Logger log = LoggerFactory.getLogger(DeterministicFakeIntakeParser.class);

    private static final Pattern BLOOD_GROUP_PATTERN = Pattern.compile(
        "(?i)\\b(AB|A|B|O)\\s*([+-]|pos(?:itive)?|neg(?:ative)?)?\\b"
    );

    private static final Pattern UNITS_PATTERN = Pattern.compile(
        "(?i)\\b(\\d+)\\s*(?:unit|units|bottle|bottles|bag|bags|packet|packets|pint|pints)\\b"
    );

    private static final Pattern PINCODE_PATTERN = Pattern.compile(
        "\\b[1-9]\\d{5}\\b"
    );

    private static final Pattern HOSPITAL_ZONE_PATTERN = Pattern.compile(
        "(?i)\\b(AIIMS|Safdarjung|Apollo|Fortis|Max|City\\s+Hospital|District\\s+Hospital|Zone[- ]\\w+|Central|North|South|East|West)\\b"
    );

    private static final Pattern CONTACT_PATTERN = Pattern.compile(
        "(?:\\+91[- ]?)?\\b[6-9]\\d{9}\\b"
    );

    private final PromptInjectionGuard promptInjectionGuard;
    private final IntakeSchemaValidator schemaValidator;

    public DeterministicFakeIntakeParser(
        PromptInjectionGuard promptInjectionGuard,
        IntakeSchemaValidator schemaValidator
    ) {
        this.promptInjectionGuard = promptInjectionGuard;
        this.schemaValidator = schemaValidator;
    }

    @Override
    public ParsedIntakeDto parse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return schemaValidator.validateAndNormalize(
                null, null, null, null, null, 0.0, "", Map.of()
            );
        }

        // 1. Guard against prompt injection attacks
        if (promptInjectionGuard.containsSuspiciousInstruction(rawText)) {
            ParsedIntakeDto dummy = schemaValidator.validateAndNormalize(
                null, null, null, null, null, 0.0, rawText, Map.of()
            );
            return promptInjectionGuard.guardParsedOutput(dummy, rawText);
        }

        Map<String, Double> fieldConfidences = new HashMap<>();

        // 2. Extract Blood Group
        String bloodGroup = null;
        Matcher bgMatcher = BLOOD_GROUP_PATTERN.matcher(rawText);
        // Find best match (prefer explicit Rh sign if present)
        while (bgMatcher.find()) {
            String group = bgMatcher.group(1);
            String sign = bgMatcher.group(2);
            if (sign != null && !sign.isBlank()) {
                bloodGroup = group + sign;
                fieldConfidences.put("bloodGroup", 0.95);
                break;
            } else if (bloodGroup == null) {
                // Default to POS if Rh sign omitted
                bloodGroup = group + "+";
                fieldConfidences.put("bloodGroup", 0.65);
            }
        }

        // 3. Extract Urgency
        String urgency = null;
        String lower = rawText.toLowerCase();
        if (lower.contains("critical") || lower.contains("emergency") || lower.contains("immediate") || lower.contains("right now")) {
            urgency = "CRITICAL";
            fieldConfidences.put("urgency", 0.95);
        } else if (lower.contains("urgent") || lower.contains("asap") || lower.contains("needed today") || lower.contains("fast")) {
            urgency = "URGENT";
            fieldConfidences.put("urgency", 0.90);
        } else if (lower.contains("routine") || lower.contains("elective") || lower.contains("planned") || lower.contains("tomorrow")) {
            urgency = "ROUTINE";
            fieldConfidences.put("urgency", 0.85);
        } else if (bloodGroup != null) {
            // Default reasonable urgency for blood requests
            urgency = "URGENT";
            fieldConfidences.put("urgency", 0.60);
        }

        // 4. Extract Units
        Integer units = null;
        Matcher unitsMatcher = UNITS_PATTERN.matcher(rawText);
        if (unitsMatcher.find()) {
            try {
                units = Integer.parseInt(unitsMatcher.group(1));
                fieldConfidences.put("units", 0.95);
            } catch (NumberFormatException ignored) {
            }
        } else {
            // Check standalone number near request text
            Pattern standaloneNum = Pattern.compile("(?i)(?:need|req|require|requiring|for)\\s+(\\d+)\\s+(?:of\\s+)?(?:A|B|AB|O)");
            Matcher numMatcher = standaloneNum.matcher(rawText);
            if (numMatcher.find()) {
                try {
                    units = Integer.parseInt(numMatcher.group(1));
                    fieldConfidences.put("units", 0.80);
                } catch (NumberFormatException ignored) {
                }
            } else {
                units = 1;
                fieldConfidences.put("units", 0.50);
            }
        }

        // 5. Extract Zone / Pincode
        String zone = null;
        Matcher pinMatcher = PINCODE_PATTERN.matcher(rawText);
        if (pinMatcher.find()) {
            zone = pinMatcher.group();
            fieldConfidences.put("zone", 0.95);
        } else {
            Matcher hospMatcher = HOSPITAL_ZONE_PATTERN.matcher(rawText);
            if (hospMatcher.find()) {
                zone = hospMatcher.group();
                fieldConfidences.put("zone", 0.85);
            } else {
                // Check general location keywords (at, in, near)
                Pattern locPattern = Pattern.compile("(?i)(?:at|in|near)\\s+([A-Z][a-zA-Z0-9\\s]{2,20})");
                Matcher locMatcher = locPattern.matcher(rawText);
                if (locMatcher.find()) {
                    zone = locMatcher.group(1).trim();
                    fieldConfidences.put("zone", 0.70);
                }
            }
        }

        // 6. Extract Contact
        String contact = null;
        Matcher contactMatcher = CONTACT_PATTERN.matcher(rawText);
        if (contactMatcher.find()) {
            contact = contactMatcher.group().replaceAll("[^0-9]", "");
            if (contact.startsWith("91") && contact.length() == 12) {
                contact = contact.substring(2);
            } else if (contact.startsWith("0") && contact.length() == 11) {
                contact = contact.substring(1);
            }
            fieldConfidences.put("contact", 0.90);
        }

        // 7. Validate and Normalize
        return schemaValidator.validateAndNormalize(
            bloodGroup,
            urgency,
            zone,
            units,
            contact,
            null, // Let validator aggregate confidence
            rawText,
            fieldConfidences
        );
    }
}
