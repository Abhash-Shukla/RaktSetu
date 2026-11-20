package lifeline.sim.inventory;

import lifeline.core.domain.BloodGroup;
import lifeline.core.inventory.BankInventory;

import java.util.*;

/**
 * Generates and scales blood bank inventory across zones per Parameter P-09.
 * Sourced from E4 design audit and human-approved scaling formula:
 * Stock(z, g, beta) = round(beta * 30.0 * P(g))
 */
public final class InventoryScaler {

    public static final double BASE_HORIZON_DEMAND_UNITS = 30.0;

    private InventoryScaler() {
    }

    /**
     * Generates a list of BankInventory instances (one per zone) scaled to the specified inventory level.
     *
     * @param populationFrequencies baseline population frequencies for all 8 blood groups
     * @param inventoryLevel        sweep level multiplier (e.g. 0.0, 0.25, 0.50, 1.00)
     * @param zoneList              list of zone identifiers in the synthetic world
     * @return unmodifiable list of initialized BankInventory objects
     */
    public static List<BankInventory> scale(
        Map<BloodGroup, Double> populationFrequencies,
        double inventoryLevel,
        List<String> zoneList
    ) {
        Objects.requireNonNull(populationFrequencies, "populationFrequencies cannot be null");
        Objects.requireNonNull(zoneList, "zoneList cannot be null");
        if (inventoryLevel < 0.0) {
            throw new IllegalArgumentException("inventoryLevel cannot be negative, got: " + inventoryLevel);
        }
        if (zoneList.isEmpty()) {
            throw new IllegalArgumentException("zoneList cannot be empty");
        }

        for (BloodGroup bg : BloodGroup.values()) {
            if (!populationFrequencies.containsKey(bg)) {
                throw new IllegalArgumentException("Missing frequency for blood group: " + bg);
            }
        }

        List<BankInventory> banks = new ArrayList<>(zoneList.size());
        for (int i = 0; i < zoneList.size(); i++) {
            String zoneId = zoneList.get(i);
            String bankId = String.format("BANK-%02d", i + 1);
            String bankName = "Blood Bank " + zoneId;

            Map<BloodGroup, Integer> stock = new EnumMap<>(BloodGroup.class);
            for (BloodGroup bg : BloodGroup.values()) {
                double freq = populationFrequencies.get(bg);
                int units = (int) Math.round(inventoryLevel * BASE_HORIZON_DEMAND_UNITS * freq);
                stock.put(bg, Math.max(0, units));
            }

            banks.add(new BankInventory(
                bankId,
                bankName,
                zoneId,
                stock,
                String.format("+91-22-%04d-%04d", 2000 + i, 5000 + i)
            ));
        }

        return Collections.unmodifiableList(banks);
    }
}

