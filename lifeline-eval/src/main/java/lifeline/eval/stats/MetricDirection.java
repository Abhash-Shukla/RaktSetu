package lifeline.eval.stats;

/**
 * Indicates whether a higher or lower metric value represents superior performance.
 */
public enum MetricDirection {
    HIGHER_IS_BETTER,
    LOWER_IS_BETTER;

    /**
     * Infers the direction of evaluation metrics specified in EVALUATION_PROTOCOL.md Section 4.
     */
    public static MetricDirection forMetric(String metricName) {
        if (metricName == null) {
            return HIGHER_IS_BETTER;
        }
        String m = metricName.toLowerCase().trim();
        if (m.equals("fulfillment_within_deadline")
            || m.equals("rare_request_fulfillment")
            || m.equals("common_request_fulfillment")
            || m.equals("tier0_resolved_share")
            || m.equals("donor_contacts_avoided")
            || m.equals("fallback_resolved_count")) {
            return HIGHER_IS_BETTER;
        }
        // All others in protocol are costs/errors/waste/load where lower is preferred:
        // contacts_per_fulfilled_request, time_to_fulfill_*, unfulfilled_rate_*,
        // rare_type_waste_rate, per_donor_contact_gini, per_donor_contact_max, ranking_regret, ndcg_gap
        return LOWER_IS_BETTER;
    }
}

