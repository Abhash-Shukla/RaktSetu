package lifeline.core.compatibility;

import lifeline.core.domain.BloodGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CompatibilityGoldenTest {

    /**
     * Independently written reference truth table for all 8x8 blood compatibility pairs.
     * This reference map is written independently inside the test file and is NOT derived
     * from the production CompatibilityTable code.
     *
     * Clinical ground truth (donor -> set of allowed recipients):
     * O-  -> O-, O+, A-, A+, B-, B+, AB-, AB+ (Universal donor)
     * O+  -> O+, A+, B+, AB+
     * A-  -> A-, A+, AB-, AB+
     * A+  -> A+, AB+
     * B-  -> B-, B+, AB-, AB+
     * B+  -> B+, AB+
     * AB- -> AB-, AB+
     * AB+ -> AB+ (Can only donate to AB+)
     */
    private static final Map<BloodGroup, Set<BloodGroup>> REFERENCE_DONATIONS = new EnumMap<>(BloodGroup.class);

    static {
        REFERENCE_DONATIONS.put(BloodGroup.O_NEG, EnumSet.allOf(BloodGroup.class));
        REFERENCE_DONATIONS.put(BloodGroup.O_POS, EnumSet.of(BloodGroup.O_POS, BloodGroup.A_POS, BloodGroup.B_POS, BloodGroup.AB_POS));
        REFERENCE_DONATIONS.put(BloodGroup.A_NEG, EnumSet.of(BloodGroup.A_NEG, BloodGroup.A_POS, BloodGroup.AB_NEG, BloodGroup.AB_POS));
        REFERENCE_DONATIONS.put(BloodGroup.A_POS, EnumSet.of(BloodGroup.A_POS, BloodGroup.AB_POS));
        REFERENCE_DONATIONS.put(BloodGroup.B_NEG, EnumSet.of(BloodGroup.B_NEG, BloodGroup.B_POS, BloodGroup.AB_NEG, BloodGroup.AB_POS));
        REFERENCE_DONATIONS.put(BloodGroup.B_POS, EnumSet.of(BloodGroup.B_POS, BloodGroup.AB_POS));
        REFERENCE_DONATIONS.put(BloodGroup.AB_NEG, EnumSet.of(BloodGroup.AB_NEG, BloodGroup.AB_POS));
        REFERENCE_DONATIONS.put(BloodGroup.AB_POS, EnumSet.of(BloodGroup.AB_POS));
    }

    @Test
    @DisplayName("Golden test: verify all 64 (donor, recipient) pairs against the independent reference matrix")
    void verifyAll64DonorRecipientPairs() {
        int pairsChecked = 0;

        for (BloodGroup donor : BloodGroup.values()) {
            Set<BloodGroup> allowedRecipients = REFERENCE_DONATIONS.get(donor);

            for (BloodGroup recipient : BloodGroup.values()) {
                boolean expected = allowedRecipients.contains(recipient);
                boolean actual = CompatibilityTable.isCompatible(donor, recipient);

                assertThat(actual)
                    .as("Compatibility mismatch for donor %s -> recipient %s", donor, recipient)
                    .isEqualTo(expected);

                pairsChecked++;
            }
        }

        assertThat(pairsChecked)
            .as("Exactly 64 pairs must be evaluated")
            .isEqualTo(64);
    }
}
