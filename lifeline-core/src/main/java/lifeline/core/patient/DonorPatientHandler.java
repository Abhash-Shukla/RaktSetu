package lifeline.core.patient;

import lifeline.core.domain.DonorSnapshot;
import lifeline.core.state.DonorState;

import java.util.Objects;

/**
 * Handles the donor-becomes-patient workflow.
 * <p>
 * Under SRS DON-5 and UF-8:
 * "If a registered donor is the subject of a request, the donor is automatically paused
 * and is never ranked until staff reactivates them."
 */
public final class DonorPatientHandler {

    private DonorPatientHandler() {
    }

    /**
     * Pauses a registered donor who is the subject of a blood request.
     * Transitions donor state to PAUSED.
     *
     * @param donor candidate donor snapshot
     * @return updated snapshot in PAUSED state
     */
    public static DonorSnapshot pauseDonorForRequest(DonorSnapshot donor) {
        Objects.requireNonNull(donor, "donor cannot be null");
        if (donor.state() == DonorState.PAUSED) {
            return donor;
        }
        return donor.pause();
    }
}
