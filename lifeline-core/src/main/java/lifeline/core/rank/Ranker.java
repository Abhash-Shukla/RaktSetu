package lifeline.core.rank;

import lifeline.core.domain.BloodRequest;
import lifeline.core.domain.DonorSnapshot;
import lifeline.core.explain.Explanation;
import lifeline.core.explain.ExplanationGenerator;
import lifeline.core.factor.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Greedy Top-N donor ranker implementing traceable, explainable multi-factor scoring.
 * <p>
 * Under AGENTS.md Section 1:
 * <ul>
 *   <li>Greedy Top-N selection with deterministic tie-breaking by donor ID.</li>
 *   <li>Score is the exact sum of individual factor contributions.</li>
 *   <li>Every match includes a per-factor breakdown and deterministic explanation.</li>
 * </ul>
 */
public final class Ranker {

    private static final Logger log = LoggerFactory.getLogger(Ranker.class);

    private final List<ScoreFactor> factors;

    /**
     * Constructs a Ranker with custom scoring factors (e.g. for ablation experiments).
     *
     * @param factors list of scoring factors to apply
     */
    public Ranker(List<ScoreFactor> factors) {
        Objects.requireNonNull(factors, "factors cannot be null");
        this.factors = List.copyOf(factors);
    }

    /**
     * Constructs a Ranker with standard default factors (Proximity, Rarity, Responsiveness).
     */
    public Ranker() {
        this(List.of(
            new ProximityFactor(),
            new RarityFactor(),
            new ResponsivenessFactor()
        ));
    }

    /**
     * Ranks eligible candidate donors and returns the top N matches.
     *
     * @param eligibleDonors candidates who have passed the CandidateFilter
     * @param request        active blood requirement
     * @param ctx            scoring context (topology, freshness, weights, active factors)
     * @param topN           maximum number of matches to return (&gt;= 1)
     * @return sorted list of top-N ranked matches
     */
    public List<RankedMatch> rank(
        Collection<DonorSnapshot> eligibleDonors,
        BloodRequest request,
        Context ctx,
        int topN
    ) {
        Objects.requireNonNull(eligibleDonors, "eligibleDonors cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        Objects.requireNonNull(ctx, "ctx cannot be null");
        if (topN <= 0) {
            throw new IllegalArgumentException("topN must be greater than 0, got " + topN);
        }

        log.debug("[request={}] Scoring {} candidate donors (topN={})", request.id(), eligibleDonors.size(), topN);

        List<RankedMatch> scoredMatches = new ArrayList<>();

        for (DonorSnapshot donor : eligibleDonors) {
            List<FactorContribution> contributions = new ArrayList<>();
            double totalScore = 0.0;

            for (ScoreFactor factor : factors) {
                // If factor is disabled in active factors set (ablation), skip its contribution
                if (!ctx.isFactorActive(factor.name())) {
                    continue;
                }

                FactorContribution fc = factor.score(donor, request, ctx);
                contributions.add(fc);
                totalScore += fc.contribution();
            }

            Explanation explanation = ExplanationGenerator.generate(donor, request, contributions);
            scoredMatches.add(new RankedMatch(donor.id(), totalScore, contributions, explanation));
        }

        // Sort descending by score, breaking ties deterministically by donor ID ascending
        Comparator<RankedMatch> comparator = (m1, m2) -> {
            int cmp = Double.compare(m2.score(), m1.score());
            if (cmp != 0) {
                return cmp;
            }
            return m1.donorId().compareTo(m2.donorId());
        };

        scoredMatches.sort(comparator);

        List<RankedMatch> topMatches = scoredMatches.stream()
            .limit(topN)
            .toList();

        log.debug("[request={}] Ranking complete: produced {} top matches", request.id(), topMatches.size());

        return topMatches;
    }
}
