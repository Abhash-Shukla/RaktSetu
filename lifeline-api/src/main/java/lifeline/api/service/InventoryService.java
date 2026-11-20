package lifeline.api.service;

import lifeline.api.dto.BankInventoryDto;
import lifeline.api.model.BankEntity;
import lifeline.api.model.BankInventoryEntity;
import lifeline.api.repository.BankInventoryRepository;
import lifeline.api.repository.BankRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InventoryService {

    private final BankRepository bankRepository;
    private final BankInventoryRepository bankInventoryRepository;
    private final AuditService auditService;

    public InventoryService(
        BankRepository bankRepository,
        BankInventoryRepository bankInventoryRepository,
        AuditService auditService
    ) {
        this.bankRepository = bankRepository;
        this.bankInventoryRepository = bankInventoryRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<BankInventoryDto> getAllInventories() {
        return bankInventoryRepository.findAll()
            .stream()
            .map(this::mapToDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<BankInventoryDto> getInventoryByBank(Long bankId) {
        return bankInventoryRepository.findByBankId(bankId)
            .stream()
            .map(this::mapToDto)
            .toList();
    }

    @Transactional
    public BankInventoryDto updateInventory(
        Long bankId,
        String bloodGroup,
        int unitsAvailable,
        String staffUsername,
        String ipAddress
    ) {
        if (unitsAvailable < 0) {
            throw new IllegalArgumentException("unitsAvailable cannot be negative: " + unitsAvailable);
        }

        BankEntity bank = bankRepository.findById(bankId)
            .orElseThrow(() -> new IllegalArgumentException("Bank not found with id: " + bankId));

        BankInventoryEntity entity = bankInventoryRepository.findByBankIdAndBloodGroup(bankId, bloodGroup)
            .orElseGet(() -> new BankInventoryEntity(bank, bloodGroup, 0));

        int previousUnits = entity.getUnitsAvailable();
        entity.setUnitsAvailable(unitsAvailable);
        entity = bankInventoryRepository.save(entity);

        auditService.log(staffUsername, "BANK_STAFF", "INVENTORY_UPDATED", "BANK_INVENTORY",
            entity.getId().toString(),
            String.format("Updated bank #%d (%s) group %s from %d to %d units", bankId, bank.getName(), bloodGroup, previousUnits, unitsAvailable),
            ipAddress);

        return mapToDto(entity);
    }

    private BankInventoryDto mapToDto(BankInventoryEntity entity) {
        return new BankInventoryDto(
            entity.getId(),
            entity.getBank().getId(),
            entity.getBank().getName(),
            entity.getBank().getZone(),
            entity.getBloodGroup(),
            entity.getUnitsAvailable(),
            entity.getUpdatedAt()
        );
    }
}
