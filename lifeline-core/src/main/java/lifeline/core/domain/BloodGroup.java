package lifeline.core.domain;

import java.util.Arrays;
import java.util.Objects;

/**
 * Standard human blood groups under the ABO and Rh(D) systems.
 * Supports the 8 clinically recognized phenotypes.
 */
public enum BloodGroup {
    O_NEG("O-", "O", false),
    O_POS("O+", "O", true),
    A_NEG("A-", "A", false),
    A_POS("A+", "A", true),
    B_NEG("B-", "B", false),
    B_POS("B+", "B", true),
    AB_NEG("AB-", "AB", false),
    AB_POS("AB+", "AB", true);

    private final String code;
    private final String abo;
    private final boolean rhPositive;

    BloodGroup(String code, String abo, boolean rhPositive) {
        this.code = code;
        this.abo = abo;
        this.rhPositive = rhPositive;
    }

    /**
     * Standard clinical code, e.g., "O+", "AB-".
     */
    public String code() {
        return code;
    }

    /**
     * ABO component: "A", "B", "AB", or "O".
     */
    public String abo() {
        return abo;
    }

    /**
     * True if Rh(D) positive, false if Rh(D) negative.
     */
    public boolean isRhPositive() {
        return rhPositive;
    }

    /**
     * Parses a string representation (e.g., "O+", "o-", "AB_POS") into a BloodGroup.
     *
     * @param input raw blood group string
     * @return matching BloodGroup
     * @throws IllegalArgumentException if no match is found
     */
    public static BloodGroup fromCode(String input) {
        Objects.requireNonNull(input, "input cannot be null");
        String trimmed = input.trim().toUpperCase();
        for (BloodGroup bg : values()) {
            if (bg.code.equalsIgnoreCase(trimmed) || bg.name().equalsIgnoreCase(trimmed)) {
                return bg;
            }
        }
        throw new IllegalArgumentException("Unknown blood group code: " + input);
    }

    @Override
    public String toString() {
        return code;
    }
}
