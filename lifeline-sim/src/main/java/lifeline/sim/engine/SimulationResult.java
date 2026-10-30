package lifeline.sim.engine;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Summary metrics and complete event log produced by a {@link SimulationRun}.
 */
public record SimulationResult(
    List<SimulationEvent> eventLog,
    int totalRequests,
    int fulfilledRequests,
    int unfulfilledRequests,
    int totalNotificationsSent,
    int totalAccepted,
    int totalDeclined,
    int totalExpired,
    Duration totalSimulationSpan
) {
    public SimulationResult {
        Objects.requireNonNull(eventLog, "eventLog cannot be null");
        Objects.requireNonNull(totalSimulationSpan, "totalSimulationSpan cannot be null");
        eventLog = Collections.unmodifiableList(eventLog);
    }

    /**
     * Proportion of requests completely fulfilled within their deadline.
     */
    public double fulfillmentRate() {
        return totalRequests == 0 ? 0.0 : (double) fulfilledRequests / totalRequests;
    }

    /**
     * Average number of donor contacts dispatched per fulfilled request.
     */
    public double contactsPerFulfilledRequest() {
        return fulfilledRequests == 0 ? 0.0 : (double) totalNotificationsSent / fulfilledRequests;
    }
}
