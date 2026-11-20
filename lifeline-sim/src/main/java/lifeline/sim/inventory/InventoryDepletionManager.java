package lifeline.sim.inventory;

import lifeline.core.compatibility.CompatibilityTable;
import lifeline.core.domain.BloodGroup;
import lifeline.core.inventory.BankInventory;

import java.util.*;

/**
 * Manages dynamic depletion of blood bank inventory upon successful Tier-0 routing.
 * <p>
 * Under approved Decision D-E4-3:
 * <ul>
 *   <li>Exact blood-group match is consumed first.</li>
 *   <li>Universal donor O- is protected and placed last among compatible groups (unless recipient is O-).</li>
 *   <li>Other compatible blood groups are consumed in descending population frequency order.</li>
 *   <li>If compatible stock is insufficient, no partial depletion occurs; the entire request falls through.</li>
 * </ul>
 */
public final class InventoryDepletionManager {

    private InventoryDepletionManager() {
    }

    /**
     * Determines the prioritized list of compatible donor blood groups for consumption.
     *
     * @param recipientGroup        blood group requested by recipient
     * @param populationFrequencies baseline population frequencies
     * @return ordered list of compatible donor groups
     */
    public static List<BloodGroup> getDepletionPriorityOrder(
        BloodGroup recipientGroup,
        Map<BloodGroup, Double> populationFrequencies
    ) {
        Objects.requireNonNull(recipientGroup, "recipientGroup cannot be null");
        Objects.requireNonNull(populationFrequencies, "populationFrequencies cannot be null");

        return Arrays.stream(BloodGroup.values())
            .filter(donorGroup -> CompatibilityTable.isCompatible(donorGroup, recipientGroup))
            .sorted(Comparator
                // 1. Exact match first
                .comparing((BloodGroup g) -> g == recipientGroup ? 0 : 1)
                // 2. Reserve universal donor O- last for non-O- recipients
                .thenComparing((BloodGroup g) -> (g == BloodGroup.O_NEG && recipientGroup != BloodGroup.O_NEG) ? 1 : 0)
                // 3. Descending population frequency (higher frequency consumed before lower frequency)
                .thenComparing((BloodGroup g) -> -populationFrequencies.getOrDefault(g, 0.0))
                // 4. Deterministic tie-breaker
                .thenComparing(Enum::name)
            )
            .toList();
    }

    /**
     * Depletes the requested units from the bank inventory according to the priority order.
     *
     * @param bank                  bank inventory with current stock
     * @param recipientGroup        recipient's requested blood group
     * @param requestedUnits        total units required
     * @param populationFrequencies baseline population frequencies
     * @return updated BankInventory with decremented stock
     * @throws IllegalArgumentException if total compatible stock is less than requestedUnits
     */
    public static BankInventory deplete(
        BankInventory bank,
        BloodGroup recipientGroup,
        int requestedUnits,
        Map<BloodGroup, Double> populationFrequencies
    ) {
        Objects.requireNonNull(bank, "bank cannot be null");
        Objects.requireNonNull(recipientGroup, "recipientGroup cannot be null");
        Objects.requireNonNull(populationFrequencies, "populationFrequencies cannot be null");
        if (requestedUnits <= 0) {
            throw new IllegalArgumentException("requestedUnits must be positive, got: " + requestedUnits);
        }

        int availableCompatible = bank.compatibleUnitsFor(recipientGroup);
        if (availableCompatible < requestedUnits) {
            throw new IllegalArgumentException(String.format(
                "Insufficient compatible stock at %s for %s (%d available < %d requested)",
                bank.bankId(), recipientGroup, availableCompatible, requestedUnits
            ));
        }

        List<BloodGroup> priorityOrder = getDepletionPriorityOrder(recipientGroup, populationFrequencies);
        Map<BloodGroup, Integer> updatedStock = new EnumMap<>(bank.stock());
        int remainingToDeplete = requestedUnits;

        for (BloodGroup bg : priorityOrder) {
            int currentUnits = updatedStock.getOrDefault(bg, 0);
            if (currentUnits > 0) {
                int take = Math.min(remainingToDeplete, currentUnits);
                updatedStock.put(bg, currentUnits - take);
                remainingToDeplete -= take;
                if (remainingToDeplete == 0) {
                    break;
                }
            }
        }

        return new BankInventory(
            bank.bankId(),
            bank.bankName(),
            bank.zone(),
            updatedStock,
            bank.contactPhone()
        );
    }
}

