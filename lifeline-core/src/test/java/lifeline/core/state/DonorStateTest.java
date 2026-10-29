package lifeline.core.state;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DonorStateTest {

    /**
     * Exhaustive truth table defining all legal transitions.
     * Every other combination among the 7x7=49 pairs is strictly illegal.
     */
    private static final Map<DonorState, Set<DonorState>> LEGAL_TRANSITIONS = new EnumMap<>(DonorState.class);

    static {
        LEGAL_TRANSITIONS.put(DonorState.AVAILABLE, EnumSet.of(DonorState.NOTIFIED, DonorState.PAUSED));
        LEGAL_TRANSITIONS.put(DonorState.NOTIFIED, EnumSet.of(DonorState.ACCEPTED, DonorState.DECLINED, DonorState.EXPIRED, DonorState.PAUSED));
        LEGAL_TRANSITIONS.put(DonorState.ACCEPTED, EnumSet.of(DonorState.DONATED, DonorState.PAUSED));
        LEGAL_TRANSITIONS.put(DonorState.DECLINED, EnumSet.of(DonorState.AVAILABLE, DonorState.PAUSED));
        LEGAL_TRANSITIONS.put(DonorState.EXPIRED, EnumSet.of(DonorState.AVAILABLE, DonorState.PAUSED));
        LEGAL_TRANSITIONS.put(DonorState.DONATED, EnumSet.of(DonorState.AVAILABLE, DonorState.PAUSED));
        LEGAL_TRANSITIONS.put(DonorState.PAUSED, EnumSet.of(DonorState.AVAILABLE));
    }

    @Test
    @DisplayName("Exhaustive check of all 49 (from, to) state transitions: legal succeed, illegal throw")
    void exhaustiveLegalAndIllegalTransitions() {
        int checked = 0;

        for (DonorState from : DonorState.values()) {
            Set<DonorState> allowedTargets = LEGAL_TRANSITIONS.get(from);

            for (DonorState to : DonorState.values()) {
                boolean isExpectedLegal = allowedTargets.contains(to);
                boolean actualCanTransition = from.canTransitionTo(to);

                assertThat(actualCanTransition)
                    .as("Mismatch in canTransitionTo from %s to %s", from, to)
                    .isEqualTo(isExpectedLegal);

                if (isExpectedLegal) {
                    DonorState result = from.transitionTo(to);
                    assertThat(result).isEqualTo(to);
                } else {
                    assertThatThrownBy(() -> from.transitionTo(to))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("Illegal donor state transition");
                }

                checked++;
            }
        }

        assertThat(checked).as("Exactly 49 state pairs must be verified").isEqualTo(49);
    }
}
