package lifeline.core.patient;

import lifeline.core.config.CooldownConfig;
import lifeline.core.config.WeightProfile;
import lifeline.core.domain.*;
import lifeline.core.factor.Context;
import lifeline.core.filter.CandidateFilter;
import lifeline.core.filter.ExclusionReason;
import lifeline.core.filter.FilterResult;
import lifeline.core.rank.RankedMatch;
import lifeline.core.rank.Ranker;
import lifeline.core.state.DonorState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DonorPatientTest {

    private CandidateFilter filter;
    private Ranker ranker;
    private Instant now;
    private Context ctx;

    @BeforeEach
    void setUp() {
        filter = new CandidateFilter(CooldownConfig.defaults());
        ranker = new Ranker();
        now = Instant.parse("2026-10-15T12:00:00Z");

        ZoneMap zoneMap = new ZoneMap(
            Map.of("Z1", new GeoCoordinate(19.0000, 72.8000)),
            1.3
        );

        ctx = new Context(
            zoneMap,
            Duration.ofHours(24),
            now,
            PopulationFrequencyTable.defaultIndianPopulation(),
            10.0,
            1.0,
            2.0,
            new WeightProfile(0.65, 0.35, 0.0),
            Context.DEFAULT_ACTIVE_FACTORS
        );
    }

    @Test
    @DisplayName("Donor becomes patient: creating a request for a registered donor pauses them, and paused donor is NEVER ranked")
    void donorBecomesPatientAndNeverRanked() {
        // Active registered donor
        DonorSnapshot registeredDonor = new DonorSnapshot(
            "D-PATIENT",
            BloodGroup.B_POS,
            Sex.MALE,
            "Z1",
            LocalDate.of(2026, 1, 1),
            DonorState.AVAILABLE,
            Optional.empty(),
            HistoryStats.empty()
        );

        // Another healthy candidate donor
        DonorSnapshot healthyDonor = new DonorSnapshot(
            "D-HEALTHY",
            BloodGroup.B_POS,
            Sex.MALE,
            "Z1",
            LocalDate.of(2026, 1, 1),
            DonorState.AVAILABLE,
            Optional.empty(),
            HistoryStats.empty()
        );

        // A blood request is created for registeredDonor (they became a patient)
        BloodRequest patientRequest = new BloodRequest(
            "REQ-PATIENT-1",
            BloodGroup.B_POS,
            2,
            Urgency.CRITICAL,
            "Z1",
            now,
            now.plus(Duration.ofHours(2))
        );

        // Step 1: DonorPatientHandler pauses the donor
        DonorSnapshot pausedDonor = DonorPatientHandler.pauseDonorForRequest(registeredDonor);
        assertThat(pausedDonor.state()).isEqualTo(DonorState.PAUSED);
        assertThat(pausedDonor.paused()).isTrue();

        // Step 2: CandidateFilter evaluates pool containing the paused donor and healthy donor
        FilterResult filterResult = filter.filter(List.of(pausedDonor, healthyDonor), patientRequest);

        assertThat(filterResult.isEligible("D-PATIENT")).isFalse();
        assertThat(filterResult.findExclusion("D-PATIENT").orElseThrow().reason())
            .isEqualTo(ExclusionReason.PAUSED);

        assertThat(filterResult.isEligible("D-HEALTHY")).isTrue();

        // Step 3: Matching engine ranks only the eligible candidates
        List<RankedMatch> matches = ranker.rank(filterResult.eligibleDonors(), patientRequest, ctx, 10);

        assertThat(matches).extracting(RankedMatch::donorId)
            .containsExactly("D-HEALTHY")
            .doesNotContain("D-PATIENT");
    }
}
