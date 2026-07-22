package chaeso.zip.server.performance.domain.repository;

import chaeso.zip.server.performance.domain.entity.AdPerformance;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 광고 집행 실적 리포지토리 인터페이스.
 */
public interface AdPerformanceRepository extends JpaRepository<AdPerformance, UUID> {

  List<AdPerformance> findByUserId(UUID userId);

  void deleteByUserId(UUID userId);
}
