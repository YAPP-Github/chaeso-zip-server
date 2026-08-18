package chaeso.zip.server.recommendation.domain.repository;

import chaeso.zip.server.recommendation.domain.entity.ChannelRecommendationResult;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChannelRecommendationResultRepository
    extends JpaRepository<ChannelRecommendationResult, UUID> {

  Page<ChannelRecommendationResult> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId,
      Pageable pageable);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("delete from ChannelRecommendationResult r where r.onboardingId = :onboardingId")
  int deleteByOnboardingId(@Param("onboardingId") UUID onboardingId);
}
