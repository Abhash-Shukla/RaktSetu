package lifeline.api.repository;

import lifeline.api.model.BankInventoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BankInventoryRepository extends JpaRepository<BankInventoryEntity, Long> {
    List<BankInventoryEntity> findByBankId(Long bankId);
    Optional<BankInventoryEntity> findByBankIdAndBloodGroup(Long bankId, String bloodGroup);
}
