package lifeline.eval.study.explanation;

import lifeline.core.config.WeightProfile;
import lifeline.core.domain.*;
import lifeline.core.explain.Explanation;
import lifeline.core.explain.ExplanationGenerator;
import lifeline.core.factor.Context;
import lifeline.core.factor.FactorContribution;
import lifeline.core.rank.RankedMatch;
import lifeline.core.rank.Ranker;
import net.jqwik.api.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class ExplanationFaithfulnessPropertyTest {

    private final Ranker ranker = new Ranker();

    @Property(tries = 100)
    void scoreAdditivityErrorStrictlyBoundedWithinDoubleEpsilon(
        @ForAll("randomCandidates") List<DonorSnapshot> candidates,
        @ForAll("randomRequest") BloodRequest request,
        @ForAll("randomWeightProfile") WeightProfile weightProfile
    ) {
        Context ctx = createContext(request, weightProfile);
        List<RankedMatch> matches = ranker.rank(candidates, request, ctx, candidates.size());

        for (RankedMatch match : matches) {
            double sum = match.contributions().stream()
                .mapToDouble(FactorContribution::contribution)
                .sum();

            assertThat(match.score())
                .as("Match score must strictly equal sum of factor contributions for %s", match.donorId())
                .isCloseTo(sum, offset(1.0e-12));
        }
    }

    @Property(tries = 100)
    void counterfactualTopFactorAblationMonotonicallyLowersOrPreservesScoreAndRank(
        @ForAll("randomCandidates") List<DonorSnapshot> candidates,
        @ForAll("randomRequest") BloodRequest request,
        @ForAll("randomWeightProfile") WeightProfile weightProfile
    ) {
        Context ctx = createContext(request, weightProfile);
        List<RankedMatch> matches = ranker.rank(candidates, request, ctx, candidates.size());

        for (int origRank = 1; origRank <= matches.size(); origRank++) {
            RankedMatch match = matches.get(origRank - 1);
            FactorContribution topFactor = match.contributions().stream()
                .max(Comparator.comparingDouble(FactorContribution::contribution))
                .orElse(null);

            if (topFactor != null && topFactor.contribution() > 0.0) {
                double counterfactualScore = match.score() - topFactor.contribution();
                assertThat(counterfactualScore)
                    .as("Counterfactual score must be strictly lower than original score")
                    .isLessThan(match.score());

                // Calculate counterfactual rank under deterministic tie-breaking
                int counterfactualRank = 1;
                for (RankedMatch other : matches) {
                    if (other.donorId().equals(match.donorId())) {
                        continue;
                    }
                    int cmp = Double.compare(other.score(), counterfactualScore);
                    if (cmp > 0 || (cmp == 0 && other.donorId().compareTo(match.donorId()) < 0)) {
                        counterfactualRank++;
                    }
                }

                assertThat(counterfactualRank)
                    .as("Ablating positive top factor must not improve rank position (rank cannot decrease)")
                    .isGreaterThanOrEqualTo(origRank);
            }
        }
    }

    @Property(tries = 100)
    void generatedReasonCodesAreAlwaysRecognizedInCatalog(
        @ForAll("randomCandidates") List<DonorSnapshot> candidates,
        @ForAll("randomRequest") BloodRequest request,
        @ForAll("randomWeightProfile") WeightProfile weightProfile
    ) {
        Context ctx = createContext(request, weightProfile);
        List<RankedMatch> matches = ranker.rank(candidates, request, ctx, candidates.size());

        for (RankedMatch m : matches) {
            for (FactorContribution fc : m.contributions()) {
                assertThat(ReasonCodeCatalog.isKnown(fc.reasonCode()))
                    .as("Reason code '%s' must be defined in ReasonCodeCatalog", fc.reasonCode())
                    .isTrue();
            }
        }
    }

    private Context createContext(BloodRequest req, WeightProfile wp) {
        ZoneMap zoneMap = new ZoneMap(
            Map.of(
                "Z1", new GeoCoordinate(19.0760, 72.8777),
                "Z2", new GeoCoordinate(19.1200, 72.8500),
                "Z3", new GeoCoordinate(19.2000, 72.9000)
            ),
            1.3
        );

        return new Context(
            zoneMap,
            Duration.ofHours(24),
            req.createdAt(),
            PopulationFrequencyTable.defaultIndianPopulation(),
            10.0,
            1.0,
            2.0,
            wp,
            Set.of("PROXIMITY", "RARITY", "RESPONSIVENESS")
        );
    }

    @Provide
    Arbitrary<List<DonorSnapshot>> randomCandidates() {
        return Arbitraries.integers().between(2, 6).flatMap(count -> {
            List<Arbitrary<DonorSnapshot>> arbs = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String id = "D-" + (char) ('A' + i);
                arbs.add(Arbitraries.of(BloodGroup.values()).flatMap(bg ->
                    Arbitraries.of(Sex.values()).flatMap(sex ->
                        Arbitraries.of("Z1", "Z2", "Z3").flatMap(zone ->
                            Arbitraries.of(true, false).flatMap(hasCurLoc ->
                                Arbitraries.of(true, false).map(isStale -> {
                                    Optional<CurrentLocation> loc = Optional.empty();
                                    Instant now = Instant.parse("2026-11-06T12:00:00Z");
                                    if (hasCurLoc) {
                                        Instant confTime = isStale ? now.minus(Duration.ofHours(30)) : now.minus(Duration.ofHours(2));
                                        loc = Optional.of(new CurrentLocation(zone, confTime));
                                    }
                                    return new DonorSnapshot(
                                        id,
                                        bg,
                                        sex,
                                        zone,
                                        LocalDate.of(2026, 1, 1),
                                        true,
                                        false,
                                        loc,
                                        new HistoryStats(4, 2, 0, 0, now.minus(Duration.ofDays(15)))
                                    );
                                })
                            )
                        )
                    )
                ));
            }
            return Combinators.combine(arbs).as(list -> list);
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
                        Instant.parse("2026-11-06T12:00:00Z"),
                        Instant.parse("2026-11-06T16:00:00Z")
                    )
                )
            )
        );
    }

    @Provide
    Arbitrary<WeightProfile> randomWeightProfile() {
        return Arbitraries.doubles().between(0.05, 10.0).flatMap(w1 ->
            Arbitraries.doubles().between(0.05, 10.0).flatMap(w2 ->
                Arbitraries.doubles().between(0.0, 10.0).map(w3 -> {
                    double total = w1 + w2 + w3;
                    double n1 = w1 / total;
                    double n2 = w2 / total;
                    double n3 = Math.max(0.0, 1.0 - n1 - n2);
                    return new WeightProfile(n1, n2, n3);
                })
            )
        );
    }
}

