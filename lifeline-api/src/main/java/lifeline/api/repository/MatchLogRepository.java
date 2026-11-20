package lifeline.api.repository;

import lifeline.api.model.MatchLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchLogRepository extends JpaRepository<MatchLogEntity, Long> {
    List<MatchLogEntity> findByRequestIdOrderByStepNumberDescRankPositionAsc(Long requestId);
    List<MatchLogEntity> findByDonorIdOrderByNotifiedAtDesc(Long donorId);
    long countByRequestId(Long requestId);
}
