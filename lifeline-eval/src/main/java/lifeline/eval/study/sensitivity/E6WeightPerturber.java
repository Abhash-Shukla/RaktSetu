package lifeline.eval.study.sensitivity;

import lifeline.core.config.WeightProfile;
import lifeline.core.domain.Urgency;

import java.util.*;
import java.util.random.RandomGenerator;

/**
 * Deterministic generator of bounded uniform weight perturbations for Experiment E6.
 * Generates K = 20 configurations using master sampling seed 20261117L.
 * Perturbations are drawn from Uniform(-0.10, +0.10), rejected if negative, and normalized to sum to 1.0.
 */
public final class E6WeightPerturber {

    public static final long DEFAULT_MASTER_SEED = 20261117L;
    public static final int DEFAULT_K = 20;
    public static final double PERTURBATION_BOUND = 0.10;

    private E6WeightPerturber() {
    }

    public static List<WeightConfiguration> generateConfigurations(long masterSeed, int count) {
        RandomGenerator rng = new SplittableRandom(masterSeed);
        List<WeightConfiguration> configs = new ArrayList<>();

        // Baseline reference configuration (P-28 locked defaults)
        Map<Urgency, WeightProfile> defaultMap = new EnumMap<>(Urgency.class);
        defaultMap.put(Urgency.CRITICAL, new WeightProfile(0.85, 0.15, 0.0));
        defaultMap.put(Urgency.URGENT, new WeightProfile(0.65, 0.35, 0.0));
        defaultMap.put(Urgency.ROUTINE, new WeightProfile(0.40, 0.60, 0.0));
        configs.add(new WeightConfiguration("S6_DEFAULT", "Approved P-28 baseline urgency weights", defaultMap));

        // Generate K alternative perturbed configurations
        for (int k = 1; k <= count; k++) {
            String id = String.format(Locale.ROOT, "CFG_%02d", k);
            Map<Urgency, WeightProfile> map = new EnumMap<>(Urgency.class);

            for (Urgency u : Urgency.values()) {
                WeightProfile base = defaultMap.get(u);
                double wProx, wRar;

                while (true) {
                    double deltaProx = rng.nextDouble(-PERTURBATION_BOUND, PERTURBATION_BOUND);
                    double deltaRar = rng.nextDouble(-PERTURBATION_BOUND, PERTURBATION_BOUND);

                    double rawProx = base.proximity() + deltaProx;
                    double rawRar = base.rarity() + deltaRar;

                    if (rawProx > 0.0 && rawRar > 0.0) {
                        double sum = rawProx + rawRar;
                        wProx = rawProx / sum;
                        wRar = rawRar / sum;
                        // Adjust precision so sum is strictly 1.0 within 1e-6
                        wRar = 1.0 - wProx;
                        if (wProx >= 0.0 && wRar >= 0.0) {
                            break;
                        }
                    }
                }

                map.put(u, new WeightProfile(wProx, wRar, 0.0));
            }

            configs.add(new WeightConfiguration(id, String.format(Locale.ROOT, "Sampled perturbation set #%d (seed=%d)", k, masterSeed), map));
        }

        return Collections.unmodifiableList(configs);
    }
}
