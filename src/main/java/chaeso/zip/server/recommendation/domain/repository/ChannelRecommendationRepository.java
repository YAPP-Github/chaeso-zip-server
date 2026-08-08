package chaeso.zip.server.recommendation.domain.repository;

import chaeso.zip.server.recommendation.domain.entity.ChannelRecommendation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChannelRecommendationRepository
    extends JpaRepository<ChannelRecommendation, UUID> {

  List<ChannelRecommendation> findByOnboardingIdOrderByRankAsc(UUID onboardingId);

  /**
   * 같은 온보딩으로 다시 저장할 때 이전 추천을 덮어씌운다.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("delete from ChannelRecommendation c where c.onboardingId = :onboardingId")
  int deleteByOnboardingId(@Param("onboardingId") UUID onboardingId);
}
