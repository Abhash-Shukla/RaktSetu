package lifeline.sim.world;

import java.util.random.RandomGenerator;

/**
 * Sampling utilities for standard statistical distributions using an injected RandomGenerator.
 */
public final class RandomDistributionUtil {

    private RandomDistributionUtil() {
    }

    /**
     * Samples from a Beta(alpha, beta) distribution.
     * Uses the ratio of two independent Gamma random variables:
     * If X ~ Gamma(alpha, 1) and Y ~ Gamma(beta, 1), then X / (X + Y) ~ Beta(alpha, beta).
     */
    public static double sampleBeta(double alpha, double beta, RandomGenerator rng) {
        if (alpha <= 0.0 || beta <= 0.0) {
            throw new IllegalArgumentException("Alpha and beta must be positive");
        }
        double x = sampleGamma(alpha, 1.0, rng);
        double y = sampleGamma(beta, 1.0, rng);
        double sum = x + y;
        if (sum == 0.0) {
            return alpha / (alpha + beta);
        }
        return Math.min(1.0, Math.max(0.0, x / sum));
    }

    /**
     * Samples from a Gamma(shape, scale) distribution using the Marsaglia-Tsang transformation method.
     */
    public static double sampleGamma(double shape, double scale, RandomGenerator rng) {
        if (shape <= 0.0 || scale <= 0.0) {
            throw new IllegalArgumentException("Shape and scale must be positive");
        }

        if (shape < 1.0) {
            // Johnk's generator for shape < 1
            return sampleGamma(shape + 1.0, scale, rng) * Math.pow(rng.nextDouble(), 1.0 / shape);
        }

        double d = shape - 1.0 / 3.0;
        double c = 1.0 / Math.sqrt(9.0 * d);

        while (true) {
            double z = rng.nextGaussian();
            double v = 1.0 + c * z;
            if (v <= 0.0) {
                continue;
            }
            v = v * v * v;
            double u = rng.nextDouble();
            if (u < 1.0 - 0.0331 * z * z * z * z) {
                return d * v * scale;
            }
            if (Math.log(u) < 0.5 * z * z + d * (1.0 - v + Math.log(v))) {
                return d * v * scale;
            }
        }
    }
}
