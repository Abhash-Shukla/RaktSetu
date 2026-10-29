package lifeline.core.inventory;

import lifeline.core.compatibility.CompatibilityTable;
import lifeline.core.domain.BloodGroup;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Simulated blood bank inventory.
 *
 * @param bankId       unique identifier for the blood bank
 * @param bankName     institution name
 * @param zone         geographical zone where the bank is located
 * @param stock        mapping of blood groups to available whole blood units
 * @param contactPhone contact phone number for hospital dispatch
 */
public record BankInventory(
    String bankId,
    String bankName,
    String zone,
    Map<BloodGroup, Integer> stock,
    String contactPhone
) {
    public BankInventory {
        Objects.requireNonNull(bankId, "bankId cannot be null");
        Objects.requireNonNull(bankName, "bankName cannot be null");
        Objects.requireNonNull(zone, "zone cannot be null");
        Objects.requireNonNull(stock, "stock cannot be null");
        Objects.requireNonNull(contactPhone, "contactPhone cannot be null");
        stock = Collections.unmodifiableMap(new EnumMap<>(stock));
    }

    /**
     * Returns available units of a specific blood group at this bank.
     */
    public int unitsOf(BloodGroup group) {
        return stock.getOrDefault(group, 0);
    }

    /**
     * Counts the total available units at this bank that are clinically compatible with the recipient.
     * <p>
     * Stock Compatibility Rule:
     * A bank stock unit of blood group G_stock is compatible with recipient group G_recipient
     * if and only if {@link CompatibilityTable#isCompatible(BloodGroup, BloodGroup)} returns true.
     */
    public int compatibleUnitsFor(BloodGroup recipientGroup) {
        Objects.requireNonNull(recipientGroup, "recipientGroup cannot be null");
        int total = 0;
        for (Map.Entry<BloodGroup, Integer> entry : stock.entrySet()) {
            if (CompatibilityTable.isCompatible(entry.getKey(), recipientGroup)) {
                total += entry.getValue();
            }
        }
        return total;
    }
}
