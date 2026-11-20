package lifeline.api.nlp;

import lifeline.api.dto.ParsedIntakeDto;

/**
 * Port interface for parsing informal/free-text blood request messages into structured schema.
 * Enforces SRS NLP-1 through NLP-5.
 */
public interface IntakeParser {

    /**
     * Parses unverified free-text into a validated, structured blood donation request.
     * Never trusts raw input directly; flags invalid or low-confidence fields.
     *
     * @param rawText the unstructured message text
     * @return structured ParsedIntakeDto with field-level confidences, validation flags, and fallback indicators
     */
    ParsedIntakeDto parse(String rawText);
}
