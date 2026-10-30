package lifeline.eval.metric;

import lifeline.core.domain.BloodGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Encapsulates all protocol Section 4 evaluation metrics for a single simulation run.
 */
public record EvaluationMetrics(
    double fulfillmentWithinDeadline,
    double contactsPerFulfilledRequest,
    double timeToFulfillMedianMinutes,
    double timeToFulfillP90Minutes,
    int timeToFulfillCensoredCount,
    Map<BloodGroup, Double> unfulfilledRateByGroup,
    double rareTypeWasteRate,
    double perDonorContactGini,
    int perDonorContactMax,
    double rankingRegret,
    double ndcgGap
) {
    public EvaluationMetrics {
        Objects.requireNonNull(unfulfilledRateByGroup, "unfulfilledRateByGroup cannot be null");
        unfulfilledRateByGroup = Collections.unmodifiableMap(unfulfilledRateByGroup);
    }

    /**
     * Converts these evaluation metrics to a list of tidy CSV rows.
     */
    public List<TidyMetricRow> toTidyRows(String experiment, String system, String responseModel, long seed) {
        List<TidyMetricRow> rows = new ArrayList<>();
        rows.add(new TidyMetricRow(experiment, system, responseModel, seed, "fulfillment_within_deadline", fulfillmentWithinDeadline));
        rows.add(new TidyMetricRow(experiment, system, responseModel, seed, "contacts_per_fulfilled_request", contactsPerFulfilledRequest));
        rows.add(new TidyMetricRow(experiment, system, responseModel, seed, "time_to_fulfill_median_minutes", timeToFulfillMedianMinutes));
        rows.add(new TidyMetricRow(experiment, system, responseModel, seed, "time_to_fulfill_p90_minutes", timeToFulfillP90Minutes));
        rows.add(new TidyMetricRow(experiment, system, responseModel, seed, "time_to_fulfill_censored_count", timeToFulfillCensoredCount));

        for (BloodGroup bg : BloodGroup.values()) {
            double rate = unfulfilledRateByGroup.getOrDefault(bg, 0.0);
            rows.add(new TidyMetricRow(experiment, system, responseModel, seed, "unfulfilled_rate_" + bg.name(), rate));
        }

        rows.add(new TidyMetricRow(experiment, system, responseModel, seed, "rare_type_waste_rate", rareTypeWasteRate));
        rows.add(new TidyMetricRow(experiment, system, responseModel, seed, "per_donor_contact_gini", perDonorContactGini));
        rows.add(new TidyMetricRow(experiment, system, responseModel, seed, "per_donor_contact_max", perDonorContactMax));
        rows.add(new TidyMetricRow(experiment, system, responseModel, seed, "ranking_regret", rankingRegret));
        rows.add(new TidyMetricRow(experiment, system, responseModel, seed, "ndcg_gap", ndcgGap));

        return rows;
    }
}
