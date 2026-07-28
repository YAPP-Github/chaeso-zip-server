package chaeso.zip.server.onboarding.domain.repository;

import chaeso.zip.server.onboarding.domain.entity.Onboarding;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 온보딩 응답 리포지토리 인터페이스.
 */
public interface OnboardingRepository extends JpaRepository<Onboarding, UUID> {

  List<Onboarding> findByUserIdAndIsActiveTrue(UUID userId);
}
