package lifeline.core.domain;

import lifeline.core.state.DonorState;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable snapshot of observable donor state seen by the matching engine.
 *
 * @param id               unique donor identifier
 * @param bloodGroup       ABO/Rh blood phenotype
 * @param sex              biological sex (used exclusively to select cooldown window)
 * @param homeZone         registered home geographical zone
 * @param lastDonationDate date of most recent donation, or null if never donated
 * @param state            current donor lifecycle state (e.g. AVAILABLE, NOTIFIED, PAUSED)
 * @param currentLocation  optional recent location with confirmation timestamp
 * @param historyStats     observable outreach history
 */
public record DonorSnapshot(
    String id,
    BloodGroup bloodGroup,
    Sex sex,
    String homeZone,
    LocalDate lastDonationDate,
    DonorState state,
    Optional<CurrentLocation> currentLocation,
    HistoryStats historyStats
) {
    public DonorSnapshot {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(bloodGroup, "bloodGroup cannot be null");
        Objects.requireNonNull(sex, "sex cannot be null");
        Objects.requireNonNull(homeZone, "homeZone cannot be null");
        Objects.requireNonNull(state, "state cannot be null");
        Objects.requireNonNull(currentLocation, "currentLocation cannot be null (use Optional.empty())");
        Objects.requireNonNull(historyStats, "historyStats cannot be null");
    }

    /**
     * Backward-compatible constructor accepting boolean flags.
     */
    public DonorSnapshot(
        String id,
        BloodGroup bloodGroup,
        Sex sex,
        String homeZone,
        LocalDate lastDonationDate,
        boolean availability,
        boolean paused,
        Optional<CurrentLocation> currentLocation,
        HistoryStats historyStats
    ) {
        this(
            id,
            bloodGroup,
            sex,
            homeZone,
            lastDonationDate,
            paused ? DonorState.PAUSED : (availability ? DonorState.AVAILABLE : DonorState.DECLINED),
            currentLocation,
            historyStats
        );
    }

    /**
     * Convenience constructor accepting nullable CurrentLocation and defaulting paused to false.
     */
    public DonorSnapshot(
        String id,
        BloodGroup bloodGroup,
        Sex sex,
        String homeZone,
        LocalDate lastDonationDate,
        boolean availability,
        CurrentLocation currentLocation,
        HistoryStats historyStats
    ) {
        this(
            id,
            bloodGroup,
            sex,
            homeZone,
            lastDonationDate,
            availability,
            false,
            Optional.ofNullable(currentLocation),
            historyStats != null ? historyStats : HistoryStats.empty()
        );
    }

    /**
     * Convenience constructor accepting nullable CurrentLocation with explicit paused flag.
     */
    public DonorSnapshot(
        String id,
        BloodGroup bloodGroup,
        Sex sex,
        String homeZone,
        LocalDate lastDonationDate,
        boolean availability,
        boolean paused,
        CurrentLocation currentLocation,
        HistoryStats historyStats
    ) {
        this(
            id,
            bloodGroup,
            sex,
            homeZone,
            lastDonationDate,
            paused,
            availability,
            Optional.ofNullable(currentLocation),
            historyStats != null ? historyStats : HistoryStats.empty()
        );
    }

    /**
     * Returns true if the donor is in the AVAILABLE state.
     */
    public boolean availability() {
        return state == DonorState.AVAILABLE;
    }

    /**
     * Returns true if the donor is in the PAUSED state.
     */
    public boolean paused() {
        return state == DonorState.PAUSED;
    }

    /**
     * Returns true if the donor has an active outstanding notification.
     */
    public boolean isNotified() {
        return state == DonorState.NOTIFIED;
    }

    /**
     * Returns a new snapshot with an updated donor state.
     */
    public DonorSnapshot withState(DonorState newState) {
        return new DonorSnapshot(id, bloodGroup, sex, homeZone, lastDonationDate, newState, currentLocation, historyStats);
    }

    /**
     * Automatically pauses the donor (e.g. when donor becomes a patient).
     */
    public DonorSnapshot pause() {
        return withState(state.canTransitionTo(DonorState.PAUSED) ? state.transitionTo(DonorState.PAUSED) : DonorState.PAUSED);
    }
}
