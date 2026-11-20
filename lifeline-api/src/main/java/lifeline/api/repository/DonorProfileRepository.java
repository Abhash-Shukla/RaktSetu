package lifeline.api.repository;

import lifeline.api.model.DonorProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DonorProfileRepository extends JpaRepository<DonorProfileEntity, Long> {
    Optional<DonorProfileEntity> findByUserId(Long userId);
    Optional<DonorProfileEntity> findByUserUsername(String username);

    @Query("SELECT d FROM DonorProfileEntity d WHERE d.available = true AND d.paused = false")
    List<DonorProfileEntity> findAllActiveAndAvailable();
}
