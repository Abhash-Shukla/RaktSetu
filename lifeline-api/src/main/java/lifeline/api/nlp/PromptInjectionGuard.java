package lifeline.api.nlp;

import lifeline.api.dto.ParsedIntakeDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Prompt injection guard and input safety sanitizer (SRS NLP-4).
 * Ensures that pasted free-text is strictly treated as passive data.
 * Instructions, commands, or escape sequences inside text must NEVER change system behavior.
 */
@Component
public class PromptInjectionGuard {

    private static final Logger log = LoggerFactory.getLogger(PromptInjectionGuard.class);

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
        // Direct instruction override attempts
        Pattern.compile("(?i)ignore\\s+(all\\s+)?(previous|prior|above)\\s+instructions"),
        Pattern.compile("(?i)disregard\\s+(all\\s+)?instructions"),
        Pattern.compile("(?i)system\\s+prompt"),
        Pattern.compile("(?i)you\\s+are\\s+now"),
        Pattern.compile("(?i)dan\\s+mode"),
        Pattern.compile("(?i)jailbreak"),
        Pattern.compile("(?i)new\\s+rule\\s*:"),
        Pattern.compile("(?i)reset\\s+context"),

        // Privilege escalation and workflow bypass attempts
        Pattern.compile("(?i)status\\s*[:=]\\s*(open|approved|verified)"),
        Pattern.compile("(?i)role\\s*[:=]\\s*(admin|bank_staff)"),
        Pattern.compile("(?i)grant\\s+admin"),
        Pattern.compile("(?i)auto-?approve"),
        Pattern.compile("(?i)bypass\\s+(review|verification)"),
        Pattern.compile("(?i)skip\\s+(review|verification)"),
        Pattern.compile("(?i)treat\\s+as\\s+already\\s+reviewed"),

        // Code / SQL injection attempts
        Pattern.compile("(?i)';\\s*drop\\s+table"),
        Pattern.compile("(?i)delete\\s+from\\s+blood_requests"),
        Pattern.compile("(?i)<script[^>]*>"),
        Pattern.compile("(?i)exec\\s*\\(")
    );

    /**
     * Checks whether the input text contains recognizable prompt injection patterns.
     */
    public boolean containsSuspiciousInstruction(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return false;
        }
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(rawText).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a list of detected injection pattern tags.
     */
    public List<String> detectInjectionPatterns(String rawText) {
        List<String> detected = new ArrayList<>();
        if (rawText == null || rawText.isBlank()) {
            return detected;
        }
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(rawText).find()) {
                detected.add("PATTERN:" + pattern.pattern());
            }
        }
        return detected;
    }

    /**
     * Prepares unverified raw text for safe inclusion in an LLM prompt.
     * Encloses input inside explicit boundary XML tags and neutralizes tag escape attempts.
     */
    public String sanitizeForPrompt(String rawText) {
        if (rawText == null) {
            return "<raw_user_text></raw_user_text>";
        }
        // Neutralize tag breakout attempts
        String safeText = rawText
            .replace("</raw_user_text>", "[ESCAPED_TAG]")
            .replace("<raw_user_text>", "[ESCAPED_TAG]");

        return "<raw_user_text>\n" + safeText + "\n</raw_user_text>";
    }

    /**
     * Defense-in-depth output validation:
     * If prompt injection patterns were present, forces safety flags, caps confidence,
     * and guarantees that the request requires manual review or fallback.
     */
    public ParsedIntakeDto guardParsedOutput(ParsedIntakeDto candidate, String rawText) {
        if (!containsSuspiciousInstruction(rawText)) {
            return candidate;
        }

        log.warn("Prompt injection attempt detected in input text. Neutralizing output. Patterns: {}",
            detectInjectionPatterns(rawText));

        List<String> updatedFlags = new ArrayList<>(candidate.flags());
        if (!updatedFlags.contains("PROMPT_INJECTION_SUSPECTED")) {
            updatedFlags.add("PROMPT_INJECTION_SUSPECTED");
        }
        if (!updatedFlags.contains("MANUAL_VERIFICATION_REQUIRED")) {
            updatedFlags.add("MANUAL_VERIFICATION_REQUIRED");
        }

        // Prompt injection forces confidence to 0.0 and requires fallback / manual intervention
        return new ParsedIntakeDto(
            candidate.bloodGroup(),
            candidate.urgency(),
            candidate.zoneOrPincode(),
            candidate.units(),
            candidate.contact(),
            0.0,
            rawText,
            candidate.fieldConfidences(),
            updatedFlags,
            false, // isValid must be false
            true,  // fallbackRecommended must be true
            "Suspicious instructions detected in input text. Please use manual form."
        );
    }
}
