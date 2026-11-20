package lifeline.api.service;

import lifeline.api.dto.BankInventoryDto;
import lifeline.api.model.BankEntity;
import lifeline.api.model.BankInventoryEntity;
import lifeline.api.repository.BankInventoryRepository;
import lifeline.api.repository.BankRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private BankRepository bankRepository;

    @Mock
    private BankInventoryRepository bankInventoryRepository;

    @Mock
    private AuditService auditService;

    private InventoryService inventoryService;
    private BankEntity bank;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(bankRepository, bankInventoryRepository, auditService);

        bank = new BankEntity("Central Blood Bank", "Z1", "+91-111", "b@test.com", 28.6139, 77.2090);
        bank.setId(1L);
    }

    @Test
    @DisplayName("Get Inventory: Returns all stock across banks")
    void getAllInventories_returnsList() {
        BankInventoryEntity inv1 = new BankInventoryEntity(bank, "O_POS", 15);
        inv1.setId(10L);
        when(bankInventoryRepository.findAll()).thenReturn(List.of(inv1));

        List<BankInventoryDto> result = inventoryService.getAllInventories();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).bloodGroup()).isEqualTo("O_POS");
        assertThat(result.get(0).unitsAvailable()).isEqualTo(15);
    }

    @Test
    @DisplayName("Update Inventory: Updates units available with optimistic locking")
    void updateInventory_validUnits_updatesStock() {
        when(bankRepository.findById(1L)).thenReturn(Optional.of(bank));

        BankInventoryEntity existing = new BankInventoryEntity(bank, "A_POS", 5);
        existing.setId(20L);
        when(bankInventoryRepository.findByBankIdAndBloodGroup(1L, "A_POS")).thenReturn(Optional.of(existing));
        when(bankInventoryRepository.save(any(BankInventoryEntity.class))).thenAnswer(i -> i.getArgument(0));

        BankInventoryDto updated = inventoryService.updateInventory(1L, "A_POS", 12, "staff_central", "127.0.0.1");

        assertThat(updated.unitsAvailable()).isEqualTo(12);
        verify(bankInventoryRepository).save(existing);
        verify(auditService).log(eq("staff_central"), eq("BANK_STAFF"), eq("INVENTORY_UPDATED"), eq("BANK_INVENTORY"), any(), any(), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("Update Inventory: Negative units throws IllegalArgumentException")
    void updateInventory_negativeUnits_throwsIllegalArgument() {
        assertThatThrownBy(() -> inventoryService.updateInventory(1L, "A_POS", -5, "staff_central", "127.0.0.1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unitsAvailable cannot be negative");
    }
}
