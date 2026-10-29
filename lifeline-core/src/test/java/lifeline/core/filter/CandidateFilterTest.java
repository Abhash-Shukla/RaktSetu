package lifeline.core.filter;

import lifeline.core.config.CooldownConfig;
import lifeline.core.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateFilterTest {

    private CandidateFilter filter;
    private final LocalDate referenceDate = LocalDate.of(2026, 10, 15);
    private BloodRequest oPosRequest;

    @BeforeEach
    void setUp() {
        filter = new CandidateFilter(new CooldownConfig(90, 120));
        oPosRequest = new BloodRequest(
            "REQ-1",
            BloodGroup.O_POS,
            2,
            Urgency.URGENT,
            "Z1",
            Instant.parse("2026-10-15T10:00:00Z"),
            Instant.parse("2026-10-15T16:00:00Z")
        );
    }

    private DonorSnapshot createDonor(
        String id,
        BloodGroup bloodGroup,
        Sex sex,
        LocalDate lastDonationDate,
        boolean availability,
        boolean paused
    ) {
        return new DonorSnapshot(
            id,
            bloodGroup,
            sex,
            "Z1",
            lastDonationDate,
            availability,
            paused,
            Optional.empty(),
            HistoryStats.empty()
        );
    }

    @Test
    @DisplayName("Male cooldown boundary: day 89 excluded, day 90 eligible")
    void maleCooldownBoundary() {
        // Day 89: referenceDate minus 89 days
        DonorSnapshot maleDay89 = createDonor("M-89", BloodGroup.O_POS, Sex.MALE, referenceDate.minusDays(89), true, false);
        // Day 90: referenceDate minus 90 days
        DonorSnapshot maleDay90 = createDonor("M-90", BloodGroup.O_POS, Sex.MALE, referenceDate.minusDays(90), true, false);

        FilterResult result = filter.filter(List.of(maleDay89, maleDay90), oPosRequest, referenceDate);

        assertThat(result.isEligible("M-89")).isFalse();
        assertThat(result.isEligible("M-90")).isTrue();

        ExcludedDonor exclusion = result.findExclusion("M-89").orElseThrow();
        assertThat(exclusion.reason()).isEqualTo(ExclusionReason.IN_COOLDOWN);
        assertThat(exclusion.detail()).contains("89 days ago").contains("90 days");
    }

    @Test
    @DisplayName("Female cooldown boundary: day 119 excluded, day 120 eligible")
    void femaleCooldownBoundary() {
        // Day 119: referenceDate minus 119 days
        DonorSnapshot femaleDay119 = createDonor("F-119", BloodGroup.O_POS, Sex.FEMALE, referenceDate.minusDays(119), true, false);
        // Day 120: referenceDate minus 120 days
        DonorSnapshot femaleDay120 = createDonor("F-120", BloodGroup.O_POS, Sex.FEMALE, referenceDate.minusDays(120), true, false);

        FilterResult result = filter.filter(List.of(femaleDay119, femaleDay120), oPosRequest, referenceDate);

        assertThat(result.isEligible("F-119")).isFalse();
        assertThat(result.isEligible("F-120")).isTrue();

        ExcludedDonor exclusion = result.findExclusion("F-119").orElseThrow();
        assertThat(exclusion.reason()).isEqualTo(ExclusionReason.IN_COOLDOWN);
        assertThat(exclusion.detail()).contains("119 days ago").contains("120 days");
    }

    @Test
    @DisplayName("First-time donor with null lastDonationDate is outside cooldown (eligible)")
    void firstTimeDonorEligible() {
        DonorSnapshot firstTimer = createDonor("FT-1", BloodGroup.O_POS, Sex.MALE, null, true, false);

        FilterResult result = filter.filter(List.of(firstTimer), oPosRequest, referenceDate);

        assertThat(result.isEligible("FT-1")).isTrue();
    }

    @Test
    @DisplayName("Unavailable donor is excluded with UNAVAILABLE reason")
    void unavailableDonorExcluded() {
        DonorSnapshot unavailable = createDonor("U-1", BloodGroup.O_POS, Sex.MALE, referenceDate.minusDays(200), false, false);

        FilterResult result = filter.filter(List.of(unavailable), oPosRequest, referenceDate);

        assertThat(result.isEligible("U-1")).isFalse();
        ExcludedDonor exclusion = result.findExclusion("U-1").orElseThrow();
        assertThat(exclusion.reason()).isEqualTo(ExclusionReason.UNAVAILABLE);
    }

    @Test
    @DisplayName("Paused donor is excluded with PAUSED reason")
    void pausedDonorExcluded() {
        DonorSnapshot paused = createDonor("P-1", BloodGroup.O_POS, Sex.MALE, referenceDate.minusDays(200), true, true);

        FilterResult result = filter.filter(List.of(paused), oPosRequest, referenceDate);

        assertThat(result.isEligible("P-1")).isFalse();
        ExcludedDonor exclusion = result.findExclusion("P-1").orElseThrow();
        assertThat(exclusion.reason()).isEqualTo(ExclusionReason.PAUSED);
    }

    @Test
    @DisplayName("Incompatible blood group donor is excluded with INCOMPATIBLE_BLOOD_GROUP reason")
    void incompatibleDonorExcluded() {
        // A+ donor cannot donate to O+ recipient
        DonorSnapshot aPosDonor = createDonor("INC-1", BloodGroup.A_POS, Sex.MALE, referenceDate.minusDays(200), true, false);

        FilterResult result = filter.filter(List.of(aPosDonor), oPosRequest, referenceDate);

        assertThat(result.isEligible("INC-1")).isFalse();
        ExcludedDonor exclusion = result.findExclusion("INC-1").orElseThrow();
        assertThat(exclusion.reason()).isEqualTo(ExclusionReason.INCOMPATIBLE_BLOOD_GROUP);
    }

    @Test
    @DisplayName("Mixed candidate pool properly partitions into eligible and excluded sets")
    void mixedPoolPartitioning() {
        DonorSnapshot eligible1 = createDonor("E-1", BloodGroup.O_POS, Sex.MALE, referenceDate.minusDays(100), true, false);
        DonorSnapshot eligible2 = createDonor("E-2", BloodGroup.O_NEG, Sex.FEMALE, referenceDate.minusDays(150), true, false);
        DonorSnapshot inCooldown = createDonor("X-1", BloodGroup.O_POS, Sex.MALE, referenceDate.minusDays(50), true, false);
        DonorSnapshot unavailable = createDonor("X-2", BloodGroup.O_POS, Sex.FEMALE, referenceDate.minusDays(200), false, false);
        DonorSnapshot incompatible = createDonor("X-3", BloodGroup.B_POS, Sex.MALE, referenceDate.minusDays(200), true, false);

        FilterResult result = filter.filter(
            List.of(eligible1, eligible2, inCooldown, unavailable, incompatible),
            oPosRequest,
            referenceDate
        );

        assertThat(result.eligibleDonors()).extracting(DonorSnapshot::id)
            .containsExactlyInAnyOrder("E-1", "E-2");

        assertThat(result.excludedDonors()).hasSize(3);
        assertThat(result.findExclusion("X-1").orElseThrow().reason()).isEqualTo(ExclusionReason.IN_COOLDOWN);
        assertThat(result.findExclusion("X-2").orElseThrow().reason()).isEqualTo(ExclusionReason.UNAVAILABLE);
        assertThat(result.findExclusion("X-3").orElseThrow().reason()).isEqualTo(ExclusionReason.INCOMPATIBLE_BLOOD_GROUP);
    }
}
