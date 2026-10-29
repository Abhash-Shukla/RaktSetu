package lifeline.core.rank;

import lifeline.core.config.WeightProfile;
import lifeline.core.domain.*;
import lifeline.core.factor.Context;
import net.jqwik.api.*;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class RankerPropertyTest {

    private final Ranker ranker = new Ranker();

    @Property(tries = 100)
    void sumOfContributionsEqualsScoreWithinTolerance(
        @ForAll("randomDonors") List<DonorSnapshot> donors,
        @ForAll("randomRequest") BloodRequest request,
        @ForAll("randomWeightProfile") WeightProfile weightProfile
    ) {
        ZoneMap zoneMap = new ZoneMap(
            Map.of(
                "Z1", new GeoCoordinate(19.0760, 72.8777),
                "Z2", new GeoCoordinate(19.1200, 72.8500),
                "Z3", new GeoCoordinate(19.2000, 72.9000)
            ),
            1.3
        );

        Context ctx = new Context(
            zoneMap,
            Duration.ofHours(24),
            request.createdAt(),
            PopulationFrequencyTable.defaultIndianPopulation(),
            10.0,
            1.0,
            2.0,
            weightProfile,
            Set.of("PROXIMITY", "RARITY", "RESPONSIVENESS")
        );

        List<RankedMatch> matches = ranker.rank(donors, request, ctx, donors.size());

        for (RankedMatch match : matches) {
            double expectedSum = match.contributions().stream()
                .mapToDouble(c -> c.contribution())
                .sum();

            assertThat(match.score())
                .as("Match score must equal sum of factor contributions for donor %s", match.donorId())
                .isCloseTo(expectedSum, offset(1e-12));
        }
    }

    @Provide
    Arbitrary<List<DonorSnapshot>> randomDonors() {
        return Arbitraries.integers().between(1, 10).flatMap(count -> {
            List<Arbitrary<DonorSnapshot>> donorArbs = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String id = "D-" + i;
                donorArbs.add(Arbitraries.of(BloodGroup.values()).flatMap(bg ->
                    Arbitraries.of(Sex.values()).flatMap(sex ->
                        Arbitraries.of("Z1", "Z2", "Z3").map(zone ->
                            new DonorSnapshot(
                                id,
                                bg,
                                sex,
                                zone,
                                LocalDate.of(2026, 1, 1),
                                true,
                                false,
                                Optional.empty(),
                                new HistoryStats(5, 3, 1, 1, Instant.now())
                            )
                        )
                    )
                ));
            }
            return Combinators.combine(donorArbs).as(donors -> donors);
        });
    }

    @Provide
    Arbitrary<BloodRequest> randomRequest() {
        return Arbitraries.of(BloodGroup.values()).flatMap(bg ->
            Arbitraries.of(Urgency.values()).flatMap(urgency ->
                Arbitraries.of("Z1", "Z2", "Z3").map(zone ->
                    new BloodRequest(
                        "REQ-PROP",
                        bg,
                        1,
                        urgency,
                        zone,
                        Instant.parse("2026-10-15T10:00:00Z"),
                        Instant.parse("2026-10-15T18:00:00Z")
                    )
                )
            )
        );
    }

    @Provide
    Arbitrary<WeightProfile> randomWeightProfile() {
        return Arbitraries.integers().between(1, 100).tuple3().map(t -> {
            double w1 = t.get1();
            double w2 = t.get2();
            double w3 = t.get3();
            double sum = w1 + w2 + w3;
            return new WeightProfile(w1 / sum, w2 / sum, w3 / sum);
        });
    }
}
