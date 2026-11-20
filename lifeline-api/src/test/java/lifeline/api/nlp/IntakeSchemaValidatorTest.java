package lifeline.api.nlp;

import lifeline.api.dto.ParsedIntakeDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IntakeSchemaValidatorTest {

    private IntakeSchemaValidator validator;

    @BeforeEach
    void setUp() {
        validator = new IntakeSchemaValidator(0.60);
    }

    @ParameterizedTest
    @CsvSource({
        "A+, A_POS",
        "A positive, A_POS",
        "a_pos, A_POS",
        "A POS, A_POS",
        "B-, B_NEG",
        "B negative, B_NEG",
        "b_neg, B_NEG",
        "AB+, AB_POS",
        "AB positive, AB_POS",
        "ab_pos, AB_POS",
        "O+, O_POS",
        "O positive, O_POS",
        "O-, O_NEG",
        "O negative, O_NEG"
    })
    @DisplayName("Blood Group: Canonical normalizer handles abbreviations and informal text forms")
    void normalizesBloodGroups(String raw, String expectedCanonical) {
        assertThat(IntakeSchemaValidator.normalizeBloodGroup(raw)).isEqualTo(expectedCanonical);
    }

    @Test
    @DisplayName("Blood Group: Rejects invalid or nonexistent blood group notations")
    void rejectsInvalidBloodGroups() {
        assertThat(IntakeSchemaValidator.normalizeBloodGroup("XYZ")).isNull();
        assertThat(IntakeSchemaValidator.normalizeBloodGroup("C+")).isNull();
        assertThat(IntakeSchemaValidator.normalizeBloodGroup("")).isNull();
        assertThat(IntakeSchemaValidator.normalizeBloodGroup(null)).isNull();
    }

    @ParameterizedTest
    @CsvSource({
        "critical, CRITICAL",
        "EMERGENCY, CRITICAL",
        "immediate need, CRITICAL",
        "urgent, URGENT",
        "asap, URGENT",
        "routine, ROUTINE",
        "planned elective, ROUTINE"
    })
    @DisplayName("Urgency: Normalizes urgency keywords to CRITICAL, URGENT, ROUTINE")
    void normalizesUrgency(String raw, String expectedCanonical) {
        assertThat(IntakeSchemaValidator.normalizeUrgency(raw)).isEqualTo(expectedCanonical);
    }

    @Test
    @DisplayName("Units: Out of range or negative units are flagged INVALID_UNITS")
    void flagsInvalidUnits() {
        ParsedIntakeDto result = validator.validateAndNormalize(
            "A+", "URGENT", "Zone-1", 0, "9876543210", 0.9, "text", Map.of()
        );
        assertThat(result.flags()).contains("INVALID_UNITS");
        assertThat(result.isValid()).isFalse();
        assertThat(result.fallbackRecommended()).isTrue();

        ParsedIntakeDto highUnits = validator.validateAndNormalize(
            "A+", "URGENT", "Zone-1", 100, "9876543210", 0.9, "text", Map.of()
        );
        assertThat(highUnits.flags()).contains("INVALID_UNITS");
    }

    @Test
    @DisplayName("Units: Missing units defaults to 1 with DEFAULT_UNITS_APPLIED flag")
    void missingUnitsDefaultsToOne() {
        ParsedIntakeDto result = validator.validateAndNormalize(
            "A+", "URGENT", "Zone-1", null, "9876543210", 0.8, "text", Map.of()
        );
        assertThat(result.units()).isEqualTo(1);
        assertThat(result.flags()).contains("DEFAULT_UNITS_APPLIED");
    }

    @Test
    @DisplayName("Zone: Missing zone flags MISSING_ZONE and triggers fallback")
    void missingZoneTriggersFallback() {
        ParsedIntakeDto result = validator.validateAndNormalize(
            "A+", "URGENT", null, 2, "9876543210", 0.9, "text", Map.of()
        );
        assertThat(result.flags()).contains("MISSING_ZONE");
        assertThat(result.isValid()).isFalse();
        assertThat(result.fallbackRecommended()).isTrue();
    }

    @Test
    @DisplayName("Confidence: Low confidence triggers LOW_CONFIDENCE flag and recommends manual form")
    void lowConfidenceTriggersFlag() {
        ParsedIntakeDto result = validator.validateAndNormalize(
            "A+", "URGENT", "AIIMS", 2, "9876543210", 0.45, "text", Map.of()
        );
        assertThat(result.flags()).contains("LOW_CONFIDENCE");
        assertThat(result.isValid()).isFalse();
        assertThat(result.fallbackRecommended()).isTrue();
        assertThat(result.fallbackReason()).contains("below confidence threshold");
    }

    @Test
    @DisplayName("Valid Complete: Correct fields with high confidence pass validation cleanly")
    void validCompleteExtractionPasses() {
        ParsedIntakeDto result = validator.validateAndNormalize(
            "O+", "CRITICAL", "AIIMS 110029", 2, "9876543210", 0.92, "text", Map.of()
        );
        assertThat(result.isValid()).isTrue();
        assertThat(result.fallbackRecommended()).isFalse();
        assertThat(result.bloodGroup()).isEqualTo("O_POS");
        assertThat(result.urgency()).isEqualTo("CRITICAL");
        assertThat(result.units()).isEqualTo(2);
        assertThat(result.zoneOrPincode()).isEqualTo("AIIMS 110029");
    }
}
