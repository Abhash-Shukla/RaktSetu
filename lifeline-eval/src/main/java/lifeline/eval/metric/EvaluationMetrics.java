package lifeline.eval.metric;

import lifeline.core.domain.BloodGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Encapsulates all protocol Section 4 evaluation metrics for a single simulation run,
 * plus study-specific extensions for E3 and E4.
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
    double ndcgGap,
    double rareRequestFulfillment,
    double commonRequestFulfillment,
    double tier0ResolvedShare,
    int totalNotificationsSent,
    int fallbackAttemptsCount,
    int fallbackResolvedCount
) {
    public EvaluationMetrics {
        Objects.requireNonNull(unfulfilledRateByGroup, "unfulfilledRateByGroup cannot be null");
        unfulfilledRateByGroup = Collections.unmodifiableMap(unfulfilledRateByGroup);
    }

    /**
     * Backward-compatible constructor defaulting E3, E4, and E5 metrics.
     */
    public EvaluationMetrics(
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
        this(
            fulfillmentWithinDeadline,
            contactsPerFulfilledRequest,
            timeToFulfillMedianMinutes,
            timeToFulfillP90Minutes,
            timeToFulfillCensoredCount,
            unfulfilledRateByGroup,
            rareTypeWasteRate,
            perDonorContactGini,
            perDonorContactMax,
            rankingRegret,
            ndcgGap,
            0.0,
            0.0,
            0.0,
            0,
            0,
            0
        );
    }

    /**
     * Backward-compatible constructor defaulting E4 and E5 metrics.
     */
    public EvaluationMetrics(
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
        double ndcgGap,
        double rareRequestFulfillment,
        double commonRequestFulfillment
    ) {
        this(
            fulfillmentWithinDeadline,
            contactsPerFulfilledRequest,
            timeToFulfillMedianMinutes,
            timeToFulfillP90Minutes,
            timeToFulfillCensoredCount,
            unfulfilledRateByGroup,
            rareTypeWasteRate,
            perDonorContactGini,
            perDonorContactMax,
            rankingRegret,
            ndcgGap,
            rareRequestFulfillment,
            commonRequestFulfillment,
            0.0,
            0,
            0,
            0
        );
    }

    /**
     * Backward-compatible constructor defaulting E5 metrics.
     */
    public EvaluationMetrics(
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
        double ndcgGap,
        double rareRequestFulfillment,
        double commonRequestFulfillment,
        double tier0ResolvedShare,
        int totalNotificationsSent
    ) {
        this(
            fulfillmentWithinDeadline,
            contactsPerFulfilledRequest,
            timeToFulfillMedianMinutes,
            timeToFulfillP90Minutes,
            timeToFulfillCensoredCount,
            unfulfilledRateByGroup,
            rareTypeWasteRate,
            perDonorContactGini,
            perDonorContactMax,
            rankingRegret,
            ndcgGap,
            rareRequestFulfillment,
            commonRequestFulfillment,
            tier0ResolvedShare,
            totalNotificationsSent,
            0,
            0
        );
    }

    /**
     * Converts these evaluation metrics to a list of tidy CSV rows.
     */
    public List<TidyMetricRow> toTidyRows(
        String experiment,
        String system,
        String responseModel,
        long seed,
        boolean includeE3Metrics,
        boolean includeE4Metrics,
        boolean includeE5Metrics
    ) {
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

        if (includeE3Metrics) {
            rows.add(new TidyMetricRow(experiment, system, responseModel, seed, "rare_request_fulfillment", rareRequestFulfillment));
            rows.add(new TidyMetricRow(experiment, system, responseModel, seed, "common_request_fulfillment", commonRequestFulfillment));
        }

        if (includeE4Metrics) {
            rows.add(new TidyMetricRow(experiment, system, responseModel, seed, "tier0_resolved_share", tier0ResolvedShare));
            rows.add(new TidyMetricRow(experiment, system, responseModel, seed, "total_notifications_sent", totalNotificationsSent));
        }

        if (includeE5Metrics) {
            rows.add(new TidyMetricRow(experiment, system, responseModel, seed, "fallback_attempts_count", fallbackAttemptsCount));
            rows.add(new TidyMetricRow(experiment, system, responseModel, seed, "fallback_resolved_count", fallbackResolvedCount));
        }

        return rows;
    }

    public List<TidyMetricRow> toTidyRows(
        String experiment,
        String system,
        String responseModel,
        long seed,
        boolean includeE3Metrics,
        boolean includeE4Metrics
    ) {
        boolean isE5 = experiment != null && (experiment.equalsIgnoreCase("E5") || experiment.toUpperCase().startsWith("E5_") || experiment.toUpperCase().startsWith("E5-"));
        return toTidyRows(experiment, system, responseModel, seed, includeE3Metrics, includeE4Metrics, isE5);
    }

    public List<TidyMetricRow> toTidyRows(String experiment, String system, String responseModel, long seed, boolean includeE3Metrics) {
        boolean isE4 = experiment != null && (experiment.equalsIgnoreCase("E4") || experiment.toUpperCase().startsWith("E4_") || experiment.toUpperCase().startsWith("E4-"));
        boolean isE5 = experiment != null && (experiment.equalsIgnoreCase("E5") || experiment.toUpperCase().startsWith("E5_") || experiment.toUpperCase().startsWith("E5-"));
        return toTidyRows(experiment, system, responseModel, seed, includeE3Metrics, isE4, isE5);
    }

    public List<TidyMetricRow> toTidyRows(String experiment, String system, String responseModel, long seed) {
        boolean isE3 = experiment != null && (experiment.equalsIgnoreCase("E3") || experiment.toUpperCase().startsWith("E3_") || experiment.toUpperCase().startsWith("E3-"));
        boolean isE4 = experiment != null && (experiment.equalsIgnoreCase("E4") || experiment.toUpperCase().startsWith("E4_") || experiment.toUpperCase().startsWith("E4-"));
        boolean isE5 = experiment != null && (experiment.equalsIgnoreCase("E5") || experiment.toUpperCase().startsWith("E5_") || experiment.toUpperCase().startsWith("E5-"));
        return toTidyRows(experiment, system, responseModel, seed, isE3, isE4, isE5);
    }
}
