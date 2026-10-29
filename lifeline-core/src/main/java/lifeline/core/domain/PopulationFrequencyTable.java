package lifeline.core.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Population frequency table for ABO/Rh blood groups.
 * Values sourced from docs/population.yaml (Patidar & Dhiman 2021).
 */
public record PopulationFrequencyTable(Map<BloodGroup, Double> frequencies) {

    public PopulationFrequencyTable {
        Objects.requireNonNull(frequencies, "frequencies cannot be null");
        frequencies = Collections.unmodifiableMap(new EnumMap<>(frequencies));
    }

    /**
     * Default Indian population frequencies per docs/population.yaml.
     */
    public static PopulationFrequencyTable defaultIndianPopulation() {
        Map<BloodGroup, Double> map = new EnumMap<>(BloodGroup.class);
        map.put(BloodGroup.O_POS, 0.325313);
        map.put(BloodGroup.B_POS, 0.320983);
        map.put(BloodGroup.A_POS, 0.218005);
        map.put(BloodGroup.AB_POS, 0.076998);
        map.put(BloodGroup.O_NEG, 0.020287);
        map.put(BloodGroup.B_NEG, 0.020017);
        map.put(BloodGroup.A_NEG, 0.013595);
        map.put(BloodGroup.AB_NEG, 0.004802);
        return new PopulationFrequencyTable(map);
    }

    /**
     * Returns the frequency of a blood group in the population (between 0.0 and 1.0).
     */
    public double frequencyOf(BloodGroup group) {
        Objects.requireNonNull(group, "group cannot be null");
        return frequencies.getOrDefault(group, 0.0);
    }

    /**
     * Computes a rarity score where rarer blood groups have a higher score.
     * Normalized as (1.0 - frequency).
     */
    public double rarityScoreOf(BloodGroup group) {
        return 1.0 - frequencyOf(group);
    }
}
