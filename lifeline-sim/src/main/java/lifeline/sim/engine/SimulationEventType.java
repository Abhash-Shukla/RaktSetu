package lifeline.sim.engine;

/**
 * Types of discrete simulation events emitted by {@link SimulationRun}.
 */
public enum SimulationEventType {
    REQUEST_ARRIVED,
    DONOR_NOTIFIED,
    DONOR_ACCEPTED,
    DONOR_DECLINED,
    NOTIFICATION_EXPIRED,
    REQUEST_FULFILLED,
    REQUEST_EXHAUSTED
}
