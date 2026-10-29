package lifeline.core.compatibility;

import lifeline.core.domain.BloodGroup;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Static 8x8 compatibility table for whole blood donation routing.
 * <p>
 * Standard clinical compatibility rules (donor -&gt; recipient):
 * <ul>
 *   <li>O-  -&gt; All 8 groups</li>
 *   <li>O+  -&gt; O+, A+, B+, AB+</li>
 *   <li>A-  -&gt; A-, A+, AB-, AB+</li>
 *   <li>A+  -&gt; A+, AB+</li>
 *   <li>B-  -&gt; B-, B+, AB-, AB+</li>
 *   <li>B+  -&gt; B+, AB+</li>
 *   <li>AB- -&gt; AB-, AB+</li>
 *   <li>AB+ -&gt; AB+</li>
 * </ul>
 * <p>
 * Note: LifeLine uses this table for algorithmic candidate filtering and routing only.
 * Final clinical cross-matching and eligibility must always be confirmed by qualified medical staff.
 */
public final class CompatibilityTable {

    private static final int GROUP_COUNT = BloodGroup.values().length;
    private static final boolean[][] MATRIX = new boolean[GROUP_COUNT][GROUP_COUNT];
    private static final Map<BloodGroup, Set<BloodGroup>> COMPATIBLE_RECIPIENTS = new EnumMap<>(BloodGroup.class);
    private static final Map<BloodGroup, Set<BloodGroup>> COMPATIBLE_DONORS = new EnumMap<>(BloodGroup.class);

    static {
        // Initialize rules per specification:
        allow(BloodGroup.O_NEG, BloodGroup.O_NEG, BloodGroup.O_POS, BloodGroup.A_NEG, BloodGroup.A_POS,
                BloodGroup.B_NEG, BloodGroup.B_POS, BloodGroup.AB_NEG, BloodGroup.AB_POS);
        allow(BloodGroup.O_POS, BloodGroup.O_POS, BloodGroup.A_POS, BloodGroup.B_POS, BloodGroup.AB_POS);
        allow(BloodGroup.A_NEG, BloodGroup.A_NEG, BloodGroup.A_POS, BloodGroup.AB_NEG, BloodGroup.AB_POS);
        allow(BloodGroup.A_POS, BloodGroup.A_POS, BloodGroup.AB_POS);
        allow(BloodGroup.B_NEG, BloodGroup.B_NEG, BloodGroup.B_POS, BloodGroup.AB_NEG, BloodGroup.AB_POS);
        allow(BloodGroup.B_POS, BloodGroup.B_POS, BloodGroup.AB_POS);
        allow(BloodGroup.AB_NEG, BloodGroup.AB_NEG, BloodGroup.AB_POS);
        allow(BloodGroup.AB_POS, BloodGroup.AB_POS);

        // Precompute immutable sets for fast set queries
        for (BloodGroup donor : BloodGroup.values()) {
            EnumSet<BloodGroup> recipients = EnumSet.noneOf(BloodGroup.class);
            for (BloodGroup recipient : BloodGroup.values()) {
                if (MATRIX[donor.ordinal()][recipient.ordinal()]) {
                    recipients.add(recipient);
                }
            }
            COMPATIBLE_RECIPIENTS.put(donor, Collections.unmodifiableSet(recipients));
        }

        for (BloodGroup recipient : BloodGroup.values()) {
            EnumSet<BloodGroup> donors = EnumSet.noneOf(BloodGroup.class);
            for (BloodGroup donor : BloodGroup.values()) {
                if (MATRIX[donor.ordinal()][recipient.ordinal()]) {
                    donors.add(donor);
                }
            }
            COMPATIBLE_DONORS.put(recipient, Collections.unmodifiableSet(donors));
        }
    }

    private static void allow(BloodGroup donor, BloodGroup... recipients) {
        for (BloodGroup recipient : recipients) {
            MATRIX[donor.ordinal()][recipient.ordinal()] = true;
        }
    }

    private CompatibilityTable() {
    }

    /**
     * Determines whether blood from the donor is compatible with the recipient.
     *
     * @param donor     blood group of candidate donor
     * @param recipient blood group of request recipient
     * @return true if clinically compatible for routing, false otherwise
     */
    public static boolean isCompatible(BloodGroup donor, BloodGroup recipient) {
        Objects.requireNonNull(donor, "donor cannot be null");
        Objects.requireNonNull(recipient, "recipient cannot be null");
        return MATRIX[donor.ordinal()][recipient.ordinal()];
    }

    /**
     * Returns an unmodifiable set of recipient blood groups that the given donor can donate to.
     */
    public static Set<BloodGroup> compatibleRecipients(BloodGroup donor) {
        Objects.requireNonNull(donor, "donor cannot be null");
        return COMPATIBLE_RECIPIENTS.get(donor);
    }

    /**
     * Returns an unmodifiable set of donor blood groups that the given recipient can receive from.
     */
    public static Set<BloodGroup> compatibleDonors(BloodGroup recipient) {
        Objects.requireNonNull(recipient, "recipient cannot be null");
        return COMPATIBLE_DONORS.get(recipient);
    }
}
