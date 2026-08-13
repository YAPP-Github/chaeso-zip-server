package chaeso.zip.server.onboarding.domain.repository;

import chaeso.zip.server.onboarding.domain.entity.Onboarding;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 온보딩 응답 리포지토리 인터페이스.
 */
public interface OnboardingRepository extends JpaRepository<Onboarding, UUID> {

  List<Onboarding> findByUserIdAndIsActiveTrue(UUID userId);

  @Query("select o from Onboarding o where o.userId = :userId and o.isActive = true")
  Optional<Onboarding> findActiveByUserId(@Param("userId") UUID userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from Onboarding o where o.userId = :userId and o.isActive = true")
  Optional<Onboarding> findActiveByUserIdForUpdate(@Param("userId") UUID userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from Onboarding o where o.id = :onboardingId")
  Optional<Onboarding> findByIdForUpdate(@Param("onboardingId") UUID onboardingId);
}
