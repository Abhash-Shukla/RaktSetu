package lifeline.sim.engine;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable discrete simulation event in the audit and evaluation event log.
 *
 * @param timestamp     simulation time at which the event occurred
 * @param type          classification of the simulation event
 * @param requestId     target blood request identifier
 * @param donorId       donor involved in this event, or null if request-level event
 * @param attemptNumber current outreach attempt number (1-indexed), or 0 if not applicable
 * @param message       descriptive outcome or transition explanation
 */
public record SimulationEvent(
    Instant timestamp,
    SimulationEventType type,
    String requestId,
    String donorId,
    int attemptNumber,
    String message
) {
    public SimulationEvent {
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(requestId, "requestId cannot be null");
    }
}

