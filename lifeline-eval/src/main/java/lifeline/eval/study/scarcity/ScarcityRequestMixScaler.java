package lifeline.eval.study.scarcity;

import lifeline.core.domain.BloodGroup;
import lifeline.eval.metric.MetricCalculator;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Scales synthetic blood request arrival distributions across scarcity levels
 * according to pre-registered human decision D-E3-2 for Experiment E3 (Scarcity Sweep).
 * <p>
 * Mathematical Formula:
 * For rare blood groups (O-, B-, A-, AB-):
 *   P'(g) = alpha * P(g)
 * For common blood groups (O+, B+, A+, AB+):
 *   P'(g) = P(g) * (1.0 - alpha * f_rare) / f_common
 * where:
 *   f_rare   = sum_{g in rare} P(g)
 *   f_common = sum_{g in common} P(g)
 */
public final class ScarcityRequestMixScaler {

    private static final double SUM_TOLERANCE = 1e-5;

    private ScarcityRequestMixScaler() {
    }

    /**
     * Scales base blood group frequencies by a scarcity multiplier alpha.
     *
     * @param baseFrequencies baseline blood group frequency map (must cover all 8 groups and sum to 1.0)
     * @param alpha           scarcity multiplier (must be positive, e.g. 1.0, 1.5, 2.0, 3.0)
     * @return unmodifiable map of normalized scaled blood group frequencies
     * @throws IllegalArgumentException if inputs are invalid, non-normalized, or violate probability axioms
     */
    public static Map<BloodGroup, Double> scale(Map<BloodGroup, Double> baseFrequencies, double alpha) {
        Objects.requireNonNull(baseFrequencies, "baseFrequencies cannot be null");

        if (Double.isNaN(alpha) || Double.isInfinite(alpha) || alpha <= 0.0) {
            throw new IllegalArgumentException("Scarcity multiplier alpha must be positive and finite, got: " + alpha);
        }

        for (BloodGroup bg : BloodGroup.values()) {
            if (!baseFrequencies.containsKey(bg)) {
                throw new IllegalArgumentException("Missing base frequency for blood group: " + bg);
            }
            Double freq = baseFrequencies.get(bg);
            if (freq == null || Double.isNaN(freq) || Double.isInfinite(freq) || freq < 0.0) {
                throw new IllegalArgumentException("Invalid frequency for blood group " + bg + ": " + freq);
            }
        }

        double fRare = 0.0;
        double fCommon = 0.0;

        for (Map.Entry<BloodGroup, Double> entry : baseFrequencies.entrySet()) {
            if (MetricCalculator.isRareGroup(entry.getKey())) {
                fRare += entry.getValue();
            } else {
                fCommon += entry.getValue();
            }
        }

        double totalBase = fRare + fCommon;
        if (Math.abs(totalBase - 1.0) > SUM_TOLERANCE) {
            throw new IllegalArgumentException(String.format(
                "Base blood group frequencies must sum to 1.0 +/- %e, but summed to %.8f",
                SUM_TOLERANCE, totalBase));
        }

        if (fRare <= 0.0) {
            throw new IllegalArgumentException("Base rare blood group frequency mass must be strictly positive, got: " + fRare);
        }
        if (fCommon <= 0.0) {
            throw new IllegalArgumentException("Base common blood group frequency mass must be strictly positive, got: " + fCommon);
        }

        double scaledRareMass = alpha * fRare;
        if (scaledRareMass >= 1.0) {
            throw new IllegalArgumentException(String.format(
                "Scarcity multiplier alpha=%.4f inflates rare mass to %.4f >= 1.0; common groups cannot be non-negative.",
                alpha, scaledRareMass));
        }

        double commonScaleFactor = (1.0 - scaledRareMass) / fCommon;
        if (commonScaleFactor < 0.0) {
            throw new IllegalArgumentException("Derived common scale factor is negative: " + commonScaleFactor);
        }

        Map<BloodGroup, Double> scaled = new EnumMap<>(BloodGroup.class);
        double scaledTotal = 0.0;

        for (BloodGroup bg : BloodGroup.values()) {
            double baseP = baseFrequencies.get(bg);
            double scaledP;
            if (MetricCalculator.isRareGroup(bg)) {
                scaledP = alpha * baseP;
            } else {
                scaledP = baseP * commonScaleFactor;
            }

            if (Double.isNaN(scaledP) || Double.isInfinite(scaledP) || scaledP < 0.0) {
                throw new IllegalArgumentException("Scaled probability for " + bg + " is invalid: " + scaledP);
            }

            scaled.put(bg, scaledP);
            scaledTotal += scaledP;
        }

        if (Math.abs(scaledTotal - 1.0) > SUM_TOLERANCE) {
            throw new IllegalArgumentException(String.format(
                "Scaled blood group frequencies must sum to 1.0 +/- %e, but summed to %.8f",
                SUM_TOLERANCE, scaledTotal));
        }

        return Collections.unmodifiableMap(scaled);
    }
}

