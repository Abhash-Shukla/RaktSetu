package lifeline.api.repository;

import lifeline.api.model.EngineConfigVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EngineConfigVersionRepository extends JpaRepository<EngineConfigVersionEntity, Long> {
    Optional<EngineConfigVersionEntity> findByActiveTrue();
    Optional<EngineConfigVersionEntity> findByVersionNumber(int versionNumber);
    List<EngineConfigVersionEntity> findAllByOrderByVersionNumberDesc();
}
