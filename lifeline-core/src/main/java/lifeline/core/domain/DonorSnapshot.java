package lifeline.core.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable snapshot of observable donor state seen by the matching engine.
 * <p>
 * Under AGENTS.md Section 3, latent simulation traits are strictly forbidden here;
 * only observable fields and derived history are exposed to the engine.
 *
 * @param id               unique donor identifier
 * @param bloodGroup       ABO/Rh blood phenotype
 * @param sex              biological sex (used exclusively to select cooldown window)
 * @param homeZone         registered home geographical zone
 * @param lastDonationDate date of most recent donation, or null if never donated
 * @param availability     whether donor has marked themselves available
 * @param paused           whether donor is temporarily paused (e.g. active patient)
 * @param currentLocation  optional recent location with confirmation timestamp
 * @param historyStats     observable outreach history
 */
public record DonorSnapshot(
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
    public DonorSnapshot {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(bloodGroup, "bloodGroup cannot be null");
        Objects.requireNonNull(sex, "sex cannot be null");
        Objects.requireNonNull(homeZone, "homeZone cannot be null");
        Objects.requireNonNull(currentLocation, "currentLocation cannot be null (use Optional.empty())");
        Objects.requireNonNull(historyStats, "historyStats cannot be null");
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
            availability,
            paused,
            Optional.ofNullable(currentLocation),
            historyStats != null ? historyStats : HistoryStats.empty()
        );
    }
}
