package lifeline.sim.model;

import lifeline.core.domain.BloodGroup;
import lifeline.core.domain.DonorSnapshot;

import java.util.Objects;

/**
 * Simulator-only representation of a donor holding unobservable latent traits.
 * <p>
 * Under AGENTS.md Section 3:
 * "The engine sees observable donor fields only (DonorSnapshot).
 * Latent traits and true response probabilities live in sim and are visible only to eval."
 *
 * @param snapshot           observable donor snapshot exposed to the matching engine
 * @param willingness        baseline willingness to donate (Beta distributed, P-14/P-15)
 * @param reliability        intention-behaviour gap factor (P-16)
 * @param responseDelayScale median response delay in minutes (P-17)
 * @param travelToleranceKm  distance at which acceptance probability halves (P-18)
 * @param fatigueSensitivity fatigue multiplier per recent contact event (P-19)
 * @param hiddenZoneAffinity hidden unobservable zone affinity term for M3 (P-22)
 */
public record LatentDonor(
    DonorSnapshot snapshot,
    double willingness,
    double reliability,
    double responseDelayScale,
    double travelToleranceKm,
    double fatigueSensitivity,
    double hiddenZoneAffinity
) {
    public LatentDonor {
        Objects.requireNonNull(snapshot, "snapshot cannot be null");
    }

    public String id() {
        return snapshot.id();
    }

    public BloodGroup bloodGroup() {
        return snapshot.bloodGroup();
    }

    public String homeZone() {
        return snapshot.homeZone();
    }

    /**
     * Creates a new LatentDonor with an updated observable snapshot.
     */
    public LatentDonor withSnapshot(DonorSnapshot updatedSnapshot) {
        return new LatentDonor(
            updatedSnapshot,
            willingness,
            reliability,
            responseDelayScale,
            travelToleranceKm,
            fatigueSensitivity,
            hiddenZoneAffinity
        );
    }
}
