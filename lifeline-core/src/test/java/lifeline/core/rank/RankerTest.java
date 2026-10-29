package lifeline.core.rank;

import lifeline.core.config.WeightProfile;
import lifeline.core.domain.*;
import lifeline.core.factor.Context;
import lifeline.core.factor.FactorContribution;
import lifeline.core.factor.ProximityFactor;
import lifeline.core.factor.RarityFactor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class RankerTest {

    private Ranker ranker;
    private ZoneMap zoneMap;
    private Instant now;

    @BeforeEach
    void setUp() {
        ranker = new Ranker();
        now = Instant.parse("2026-10-15T12:00:00Z");

        // Mumbai topology: Z1 (South), Z2 (Central ~10km away), Z3 (North ~25km away)
        zoneMap = new ZoneMap(
            Map.of(
                "Z1", new GeoCoordinate(18.9220, 72.8347),
                "Z2", new GeoCoordinate(19.0176, 72.8561),
                "Z3", new GeoCoordinate(19.1663, 72.8526)
            ),
            1.3
        );
    }

    private Context createContext(WeightProfile weights, Set<String> activeFactors) {
        return new Context(
            zoneMap,
            Duration.ofHours(24),
            now,
            PopulationFrequencyTable.defaultIndianPopulation(),
            10.0,
            1.0,
            2.0,
            weights,
            activeFactors
        );
    }

    private DonorSnapshot createDonor(
        String id,
        BloodGroup group,
        String homeZone,
        CurrentLocation currentLocation
    ) {
        return new DonorSnapshot(
            id,
            group,
            Sex.MALE,
            homeZone,
            LocalDate.of(2026, 1, 1),
            true,
            false,
            Optional.ofNullable(currentLocation),
            HistoryStats.empty()
        );
    }

    @Test
    @DisplayName("Determinism: same input and config produce identical ranked output")
    void determinismTest() {
        BloodRequest req = new BloodRequest(
            "REQ-DET",
            BloodGroup.B_POS,
            1,
            Urgency.URGENT,
            "Z1",
            now,
            now.plus(Duration.ofHours(6))
        );

        DonorSnapshot d1 = createDonor("D-1", BloodGroup.B_POS, "Z1", null);
        DonorSnapshot d2 = createDonor("D-2", BloodGroup.O_POS, "Z2", null);
        DonorSnapshot d3 = createDonor("D-3", BloodGroup.O_NEG, "Z3", null);

        Context ctx = createContext(new WeightProfile(0.65, 0.35, 0.0), Context.DEFAULT_ACTIVE_FACTORS);

        List<RankedMatch> run1 = ranker.rank(List.of(d1, d2, d3), req, ctx, 3);
        List<RankedMatch> run2 = ranker.rank(List.of(d3, d1, d2), req, ctx, 3);

        assertThat(run1).hasSize(3);
        assertThat(run2).hasSize(3);

        for (int i = 0; i < 3; i++) {
            assertThat(run1.get(i).donorId()).isEqualTo(run2.get(i).donorId());
            assertThat(run1.get(i).score()).isEqualTo(run2.get(i).score());
            assertThat(run1.get(i).explanation().summary()).isEqualTo(run2.get(i).explanation().summary());
        }
    }

    @Test
    @DisplayName("Deterministic tie-breaking: identical scores broken by donor ID ascending")
    void deterministicTieBreaking() {
        BloodRequest req = new BloodRequest(
            "REQ-TIE",
            BloodGroup.B_POS,
            1,
            Urgency.ROUTINE,
            "Z1",
            now,
            now.plus(Duration.ofHours(24))
        );

        // Identical donors with different IDs
        DonorSnapshot dZ = createDonor("DONOR-Z", BloodGroup.B_POS, "Z1", null);
        DonorSnapshot dA = createDonor("DONOR-A", BloodGroup.B_POS, "Z1", null);
        DonorSnapshot dM = createDonor("DONOR-M", BloodGroup.B_POS, "Z1", null);

        Context ctx = createContext(new WeightProfile(0.40, 0.60, 0.0), Context.DEFAULT_ACTIVE_FACTORS);

        List<RankedMatch> matches = ranker.rank(List.of(dZ, dA, dM), req, ctx, 3);

        assertThat(matches.get(0).score()).isEqualTo(matches.get(1).score());
        assertThat(matches.get(1).score()).isEqualTo(matches.get(2).score());

        assertThat(matches).extracting(RankedMatch::donorId)
            .containsExactly("DONOR-A", "DONOR-M", "DONOR-Z");
    }

    @Test
    @DisplayName("Rarity: O- donor ranks below an otherwise-equal common-type donor for a non-O- request")
    void rarityDownranksRareDonorForCommonRequest() {
        // Request is B+ in zone Z1
        BloodRequest req = new BloodRequest(
            "REQ-RARITY",
            BloodGroup.B_POS,
            2,
            Urgency.URGENT,
            "Z1",
            now,
            now.plus(Duration.ofHours(6))
        );

        // All 3 donors are in the exact same zone Z1 (same proximity score)
        DonorSnapshot bPos = createDonor("D-BPOS", BloodGroup.B_POS, "Z1", null); // exact match
        DonorSnapshot oPos = createDonor("D-OPOS", BloodGroup.O_POS, "Z1", null); // common compatible
        DonorSnapshot oNeg = createDonor("D-ONEG", BloodGroup.O_NEG, "Z1", null); // rare compatible

        Context ctx = createContext(new WeightProfile(0.65, 0.35, 0.0), Context.DEFAULT_ACTIVE_FACTORS);

        List<RankedMatch> matches = ranker.rank(List.of(oNeg, bPos, oPos), req, ctx, 3);

        assertThat(matches).extracting(RankedMatch::donorId)
            .containsExactly("D-BPOS", "D-OPOS", "D-ONEG");

        // Verify O- donor was flagged with RARE_TYPE_CONSERVED
        RankedMatch oNegMatch = matches.stream().filter(m -> m.donorId().equals("D-ONEG")).findFirst().orElseThrow();
        assertThat(oNegMatch.explanation().rareTypeConservedFlag()).isTrue();
        assertThat(oNegMatch.contributions()).anyMatch(c ->
            c.factorName().equals(RarityFactor.NAME) && c.reasonCode().equals(RarityFactor.REASON_RARE_TYPE_CONSERVED)
        );

        // Verify B+ donor (exact match) got no rarity penalty (rawValue == 0.0)
        RankedMatch bPosMatch = matches.stream().filter(m -> m.donorId().equals("D-BPOS")).findFirst().orElseThrow();
        assertThat(bPosMatch.explanation().rareTypeConservedFlag()).isFalse();
        FactorContribution bPosRarity = bPosMatch.contributions().stream()
            .filter(c -> c.factorName().equals(RarityFactor.NAME)).findFirst().orElseThrow();
        assertThat(bPosRarity.rawValue()).isEqualTo(0.0);
        assertThat(bPosRarity.contribution()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Rarity: O- donor is NOT penalized for an O- request (exact match)")
    void rarityNoPenaltyForExactRareRequest() {
        // Request is O-
        BloodRequest req = new BloodRequest(
            "REQ-ONEG",
            BloodGroup.O_NEG,
            1,
            Urgency.CRITICAL,
            "Z1",
            now,
            now.plus(Duration.ofHours(2))
        );

        DonorSnapshot oNeg = createDonor("D-ONEG", BloodGroup.O_NEG, "Z1", null);
        Context ctx = createContext(new WeightProfile(0.85, 0.15, 0.0), Context.DEFAULT_ACTIVE_FACTORS);

        List<RankedMatch> matches = ranker.rank(List.of(oNeg), req, ctx, 1);

        RankedMatch match = matches.get(0);
        assertThat(match.explanation().rareTypeConservedFlag()).isFalse();

        FactorContribution rarity = match.contributions().stream()
            .filter(c -> c.factorName().equals(RarityFactor.NAME)).findFirst().orElseThrow();
        assertThat(rarity.rawValue()).isEqualTo(0.0);
        assertThat(rarity.contribution()).isEqualTo(0.0);
        assertThat(rarity.reasonCode()).isEqualTo(RarityFactor.REASON_EXACT_MATCH);
    }

    @Test
    @DisplayName("Stale location: flagged in explanation and scored from home zone")
    void staleLocationFlaggedAndScoredFromHomeZone() {
        // Request in Z1
        BloodRequest req = new BloodRequest(
            "REQ-STALE",
            BloodGroup.B_POS,
            1,
            Urgency.URGENT,
            "Z1",
            now,
            now.plus(Duration.ofHours(6))
        );

        // Donor with homeZone Z1, but reported currentLocation Z3 48 hours ago (freshness is 24h)
        CurrentLocation staleLocation = new CurrentLocation("Z3", now.minus(Duration.ofHours(48)));
        DonorSnapshot donor = createDonor("D-STALE", BloodGroup.B_POS, "Z1", staleLocation);

        Context ctx = createContext(new WeightProfile(0.65, 0.35, 0.0), Context.DEFAULT_ACTIVE_FACTORS);

        List<RankedMatch> matches = ranker.rank(List.of(donor), req, ctx, 1);
        RankedMatch match = matches.get(0);

        // Location flag must be true
        assertThat(match.explanation().staleLocationFlag()).isTrue();
        assertThat(match.explanation().summary()).contains("Location is stale").contains("Z1");

        // Proximity contribution must have STALE_LOCATION reasonCode and evaluated from homeZone Z1
        // Distance from Z1 to Z1 is 0.0 km -> decay rawValue == 1.0
        FactorContribution prox = match.contributions().stream()
            .filter(c -> c.factorName().equals(ProximityFactor.NAME)).findFirst().orElseThrow();

        assertThat(prox.reasonCode()).isEqualTo(ProximityFactor.REASON_STALE_LOCATION);
        assertThat(prox.rawValue()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Factor ablation: removing a factor via config removes its contribution everywhere")
    void factorAblationRemovesContribution() {
        BloodRequest req = new BloodRequest(
            "REQ-ABLATION",
            BloodGroup.B_POS,
            1,
            Urgency.URGENT,
            "Z1",
            now,
            now.plus(Duration.ofHours(6))
        );

        DonorSnapshot donor = createDonor("D-ABL", BloodGroup.O_POS, "Z1", null);

        // Only PROXIMITY active (RARITY removed for ablation)
        Context ctxProximityOnly = createContext(
            new WeightProfile(0.65, 0.35, 0.0),
            Set.of(ProximityFactor.NAME)
        );

        List<RankedMatch> matches = ranker.rank(List.of(donor), req, ctxProximityOnly, 1);
        RankedMatch match = matches.get(0);

        assertThat(match.contributions()).hasSize(1);
        assertThat(match.contributions().get(0).factorName()).isEqualTo(ProximityFactor.NAME);

        // Score must equal solely the proximity contribution
        assertThat(match.score()).isEqualTo(match.contributions().get(0).contribution());
    }

    @Test
    @DisplayName("Explanation mentions the top contributing factor by name")
    void explanationMentionsTopContributingFactor() {
        BloodRequest req = new BloodRequest(
            "REQ-EXP",
            BloodGroup.B_POS,
            1,
            Urgency.CRITICAL,
            "Z1",
            now,
            now.plus(Duration.ofHours(2))
        );

        // Critical weights: proximity 0.85, rarity 0.15
        DonorSnapshot donor = createDonor("D-TOP", BloodGroup.B_POS, "Z1", null);
        Context ctx = createContext(new WeightProfile(0.85, 0.15, 0.0), Context.DEFAULT_ACTIVE_FACTORS);

        List<RankedMatch> matches = ranker.rank(List.of(donor), req, ctx, 1);
        RankedMatch match = matches.get(0);

        assertThat(match.explanation().topContributingFactor()).isEqualTo(ProximityFactor.NAME);
        assertThat(match.explanation().summary()).contains("Top factor: PROXIMITY");
    }
}
