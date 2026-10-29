package lifeline.core.compatibility;

import lifeline.core.domain.BloodGroup;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

import static org.assertj.core.api.Assertions.assertThat;

class CompatibilityPropertyTest {

    @Property
    void oNegIsCompatibleWithEveryRecipient(@ForAll BloodGroup recipient) {
        assertThat(CompatibilityTable.isCompatible(BloodGroup.O_NEG, recipient))
            .as("O- (universal donor) must be compatible with recipient %s", recipient)
            .isTrue();
    }

    @Property
    void abPosReceivesFromEveryDonor(@ForAll BloodGroup donor) {
        assertThat(CompatibilityTable.isCompatible(donor, BloodGroup.AB_POS))
            .as("AB+ (universal recipient) must receive from donor %s", donor)
            .isTrue();
    }

    @Property
    void noPairViolatesAboRhRules(@ForAll BloodGroup donor, @ForAll BloodGroup recipient) {
        boolean actual = CompatibilityTable.isCompatible(donor, recipient);

        // Clinical ABO antigen rule:
        // O: no A/B antigens -> compatible with O, A, B, AB
        // A: A antigen -> compatible with A, AB
        // B: B antigen -> compatible with B, AB
        // AB: both A and B antigens -> compatible with AB only
        boolean aboCompatible = switch (donor.abo()) {
            case "O" -> true;
            case "A" -> recipient.abo().equals("A") || recipient.abo().equals("AB");
            case "B" -> recipient.abo().equals("B") || recipient.abo().equals("AB");
            case "AB" -> recipient.abo().equals("AB");
            default -> throw new IllegalStateException("Unknown ABO: " + donor.abo());
        };

        // Clinical Rh(D) rule:
        // Rh- donor has no D antigen -> compatible with Rh- and Rh+
        // Rh+ donor carries D antigen -> compatible only with Rh+
        boolean rhCompatible = !donor.isRhPositive() || recipient.isRhPositive();

        boolean expected = aboCompatible && rhCompatible;

        assertThat(actual)
            .as("Compatibility rule violated for %s -> %s (aboCompatible=%s, rhCompatible=%s)",
                donor, recipient, aboCompatible, rhCompatible)
            .isEqualTo(expected);
    }
}
