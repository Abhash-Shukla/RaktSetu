package lifeline.core.state;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * State machine governing donor availability, notification, and response lifecycles.
 * <p>
 * Under SRS Section 7.8 (FB-1, FB-3) &amp; Section 8.7:
 * <ul>
 *   <li>AVAILABLE -&gt; NOTIFIED -&gt; {ACCEPTED, DECLINED, EXPIRED}</li>
 *   <li>ACCEPTED -&gt; DONATED</li>
 *   <li>DECLINED / EXPIRED -&gt; AVAILABLE (returns to pool for later requests)</li>
 *   <li>DONATED -&gt; AVAILABLE (cooldown exclusion window prevents immediate re-ranking)</li>
 *   <li>Any state -&gt; PAUSED (when donor becomes an active patient)</li>
 *   <li>PAUSED -&gt; AVAILABLE (only via explicit staff reactivation)</li>
 *   <li>A donor in NOTIFIED state has an outstanding notification and cannot be notified for another request.</li>
 * </ul>
 */
public enum DonorState {
    AVAILABLE,
    NOTIFIED,
    ACCEPTED,
    DECLINED,
    EXPIRED,
    DONATED,
    PAUSED;

    private static final Set<DonorState> FROM_AVAILABLE = EnumSet.of(NOTIFIED, PAUSED);
    private static final Set<DonorState> FROM_NOTIFIED = EnumSet.of(ACCEPTED, DECLINED, EXPIRED, PAUSED);
    private static final Set<DonorState> FROM_ACCEPTED = EnumSet.of(DONATED, PAUSED);
    private static final Set<DonorState> FROM_DECLINED = EnumSet.of(AVAILABLE, PAUSED);
    private static final Set<DonorState> FROM_EXPIRED = EnumSet.of(AVAILABLE, PAUSED);
    private static final Set<DonorState> FROM_DONATED = EnumSet.of(AVAILABLE, PAUSED);
    private static final Set<DonorState> FROM_PAUSED = EnumSet.of(AVAILABLE);

    /**
     * Checks if transitioning from this state to the target state is clinically and operationally legal.
     *
     * @param target proposed new state
     * @return true if legal, false otherwise
     */
    public boolean canTransitionTo(DonorState target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case AVAILABLE -> FROM_AVAILABLE.contains(target);
            case NOTIFIED -> FROM_NOTIFIED.contains(target);
            case ACCEPTED -> FROM_ACCEPTED.contains(target);
            case DECLINED -> FROM_DECLINED.contains(target);
            case EXPIRED -> FROM_EXPIRED.contains(target);
            case DONATED -> FROM_DONATED.contains(target);
            case PAUSED -> FROM_PAUSED.contains(target);
        };
    }

    /**
     * Executes the state transition, returning the new state.
     *
     * @param target proposed new state
     * @return target state if legal
     * @throws IllegalStateException if the transition is illegal
     */
    public DonorState transitionTo(DonorState target) {
        Objects.requireNonNull(target, "target state cannot be null");
        if (!canTransitionTo(target)) {
            throw new IllegalStateException(
                String.format("Illegal donor state transition from %s to %s", this, target)
            );
        }
        return target;
    }
}
