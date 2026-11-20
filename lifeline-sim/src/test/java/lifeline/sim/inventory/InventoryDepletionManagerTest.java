package lifeline.sim.inventory;

import lifeline.core.domain.BloodGroup;
import lifeline.core.inventory.BankInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryDepletionManagerTest {

    private Map<BloodGroup, Double> freqs;

    @BeforeEach
    void setUp() {
        freqs = new EnumMap<>(BloodGroup.class);
        freqs.put(BloodGroup.O_POS, 0.325313);
        freqs.put(BloodGroup.B_POS, 0.320983);
        freqs.put(BloodGroup.A_POS, 0.218005);
        freqs.put(BloodGroup.AB_POS, 0.076998);
        freqs.put(BloodGroup.O_NEG, 0.020287);
        freqs.put(BloodGroup.B_NEG, 0.020017);
        freqs.put(BloodGroup.A_NEG, 0.013595);
        freqs.put(BloodGroup.AB_NEG, 0.004802);
    }

    private BankInventory createBank(Map<BloodGroup, Integer> stock) {
        return new BankInventory("BANK-01", "Central Bank", "Z01", stock, "+91-22-1234");
    }

    @Test
    @DisplayName("Exact match priority: consumes recipient blood group first when available")
    void exactMatchConsumedFirst() {
        BankInventory bank = createBank(Map.of(
            BloodGroup.B_POS, 3,
            BloodGroup.O_POS, 2,
            BloodGroup.O_NEG, 1
        ));

        // Request 2 units of B+
        BankInventory depleted = InventoryDepletionManager.deplete(bank, BloodGroup.B_POS, 2, freqs);

        assertThat(depleted.unitsOf(BloodGroup.B_POS)).isEqualTo(1); // 3 - 2 = 1
        assertThat(depleted.unitsOf(BloodGroup.O_POS)).isEqualTo(2); // untouched
        assertThat(depleted.unitsOf(BloodGroup.O_NEG)).isEqualTo(1); // untouched
    }

    @Test
    @DisplayName("Fallback to common compatible: consumes exact match then common compatible, preserving O-")
    void fallbackToCommonCompatiblePreservesONeg() {
        BankInventory bank = createBank(Map.of(
            BloodGroup.B_POS, 1,
            BloodGroup.O_POS, 2,
            BloodGroup.O_NEG, 1
        ));

        // Request 2 units of B+: 1 B+ exact consumed, 1 O+ common compatible consumed
        BankInventory depleted = InventoryDepletionManager.deplete(bank, BloodGroup.B_POS, 2, freqs);

        assertThat(depleted.unitsOf(BloodGroup.B_POS)).isEqualTo(0); // 1 - 1 = 0
        assertThat(depleted.unitsOf(BloodGroup.O_POS)).isEqualTo(1); // 2 - 1 = 1
        assertThat(depleted.unitsOf(BloodGroup.O_NEG)).isEqualTo(1); // O- is strictly preserved!
    }

    @Test
    @DisplayName("Universal donor O- consumed last: only when all other compatible groups are exhausted")
    void universalONegConsumedOnlyWhenAllElseExhausted() {
        BankInventory bank = createBank(Map.of(
            BloodGroup.B_POS, 1,
            BloodGroup.O_POS, 1,
            BloodGroup.O_NEG, 1
        ));

        // Request 3 units of B+: consumes 1 B+, 1 O+, and finally 1 O-
        BankInventory depleted = InventoryDepletionManager.deplete(bank, BloodGroup.B_POS, 3, freqs);

        assertThat(depleted.unitsOf(BloodGroup.B_POS)).isEqualTo(0);
        assertThat(depleted.unitsOf(BloodGroup.O_POS)).isEqualTo(0);
        assertThat(depleted.unitsOf(BloodGroup.O_NEG)).isEqualTo(0);
    }

    @Test
    @DisplayName("O- recipient consumes O- blood directly")
    void oNegRecipientConsumesONeg() {
        BankInventory bank = createBank(Map.of(
            BloodGroup.O_POS, 5,
            BloodGroup.O_NEG, 2
        ));

        // Request 1 unit of O-
        BankInventory depleted = InventoryDepletionManager.deplete(bank, BloodGroup.O_NEG, 1, freqs);

        assertThat(depleted.unitsOf(BloodGroup.O_NEG)).isEqualTo(1); // 2 - 1 = 1
        assertThat(depleted.unitsOf(BloodGroup.O_POS)).isEqualTo(5); // untouched
    }

    @Test
    @DisplayName("Insufficient stock throws IllegalArgumentException without partial decrement")
    void insufficientStockThrowsException() {
        BankInventory bank = createBank(Map.of(
            BloodGroup.B_POS, 1,
            BloodGroup.O_POS, 1
        ));

        // Request 3 units of B+ (only 2 compatible available)
        assertThatThrownBy(() -> InventoryDepletionManager.deplete(bank, BloodGroup.B_POS, 3, freqs))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Insufficient compatible stock");
    }

    @Test
    @DisplayName("Depletion priority order orders exact match first and reserves O- last for non-O- recipients")
    void priorityOrderVerification() {
        List<BloodGroup> bPosOrder = InventoryDepletionManager.getDepletionPriorityOrder(BloodGroup.B_POS, freqs);
        // Compatible with B+: B+, O+, B-, O-
        assertThat(bPosOrder).containsExactly(
            BloodGroup.B_POS, // exact match
            BloodGroup.O_POS, // common positive (freq ~0.325)
            BloodGroup.B_NEG, // rare negative (freq ~0.0200)
            BloodGroup.O_NEG  // universal rare donor reserved last
        );

        List<BloodGroup> abPosOrder = InventoryDepletionManager.getDepletionPriorityOrder(BloodGroup.AB_POS, freqs);
        // Compatible with AB+: all 8
        // First AB+, then common positives (O+, B+, A+), then rare negatives (B-, A-, AB-), and O- last!
        assertThat(abPosOrder.get(0)).isEqualTo(BloodGroup.AB_POS);
        assertThat(abPosOrder.get(abPosOrder.size() - 1)).isEqualTo(BloodGroup.O_NEG);
    }
}

