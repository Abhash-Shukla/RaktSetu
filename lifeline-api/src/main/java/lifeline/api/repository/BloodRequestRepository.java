package lifeline.api.repository;

import lifeline.api.model.BloodRequestEntity;
import lifeline.api.model.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BloodRequestRepository extends JpaRepository<BloodRequestEntity, Long> {
    List<BloodRequestEntity> findByStatusOrderByCreatedAtDesc(RequestStatus status);
    List<BloodRequestEntity> findAllByOrderByCreatedAtDesc();
}
