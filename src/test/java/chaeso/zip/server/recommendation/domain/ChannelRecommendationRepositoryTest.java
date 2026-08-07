package chaeso.zip.server.recommendation.domain;

import static chaeso.zip.server.support.ChannelCatalogFixture.persistableChannel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import chaeso.zip.server.channel.domain.vo.PricingModel;
import chaeso.zip.server.recommendation.domain.entity.ChannelRecommendation;
import chaeso.zip.server.recommendation.domain.repository.ChannelRecommendationRepository;
import chaeso.zip.server.support.OnboardingFixture;
import chaeso.zip.server.support.PostgresDataJpaTest;
import chaeso.zip.server.support.UserFixture;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@PostgresDataJpaTest
class ChannelRecommendationRepositoryTest {

  @Autowired
  private ChannelRecommendationRepository channelRecommendationRepository;

  @Autowired
  private TestEntityManager entityManager;

  private UUID userId;
  private UUID onboardingId;
  private UUID channelId;

  @BeforeEach
  void persistReferences() {
    userId = entityManager.persistAndFlush(
        UserFixture.user(UUID.randomUUID() + "@example.com")).getId();
    onboardingId = entityManager.persistAndFlush(
        OnboardingFixture.onboarding(userId)).getId();
    channelId = entityManager.persistAndFlush(persistableChannel("11번가 광고")).getId();
  }

  @Test
  @DisplayName("근거 태그와 과금 방식 배열을 PostgreSQL text[]로 저장하고 다시 읽어온다")
  void savesArrayColumns() {
    channelRecommendationRepository.saveAndFlush(recommendation(1));
    entityManager.clear();

    ChannelRecommendation found =
        channelRecommendationRepository.findByOnboardingIdOrderByRankAsc(onboardingId).getFirst();

    assertThat(found.getReasonTags()).containsExactly("CATEGORY", "OBJECTIVE");
    assertThat(found.getPricingModelsAll()).containsExactly("CPM", "CPC");
    assertThat(found.getEstUnitPrice()).isEqualByComparingTo("3000");
    assertThat(found.getEstPricingModel()).isEqualTo(PricingModel.CPM);
    assertThat(found.getCreatedAt()).isNotNull();
  }

  @Test
  @DisplayName("같은 온보딩에 같은 채널을 두 번 저장하면 유니크 제약으로 막는다")
  void rejectsDuplicateChannelInOneRecommendation() {
    channelRecommendationRepository.saveAndFlush(recommendation(1));

    assertThatThrownBy(() -> channelRecommendationRepository.saveAndFlush(recommendation(2)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("한 트랜잭션에서 지우고 다시 넣어도 유니크 제약에 걸리지 않는다")
  void deleteThenInsertInSameTransaction() {
    // 파생 삭제였다면 INSERT 가 DELETE 보다 먼저 나가 여기서 유니크 제약에 걸린다
    channelRecommendationRepository.saveAndFlush(recommendation(1));

    int deleted = channelRecommendationRepository.deleteByOnboardingId(onboardingId);
    channelRecommendationRepository.saveAndFlush(recommendation(1));
    entityManager.clear();

    assertThat(deleted).isEqualTo(1);
    assertThat(channelRecommendationRepository.findByOnboardingIdOrderByRankAsc(onboardingId))
        .hasSize(1);
  }

  private ChannelRecommendation recommendation(int rank) {
    return ChannelRecommendation.builder()
        .userId(userId)
        .onboardingId(onboardingId)
        .channelId(channelId)
        .rank(rank)
        .score(78)
        .reason("쇼핑·커머스 업종, 설정한 광고 목적에 적합하고 예산 내 집행이 가능해요")
        .reasonTags(List.of("CATEGORY", "OBJECTIVE"))
        .channelName("11번가 광고")
        .estPricingModel(PricingModel.CPM)
        .estUnitPrice(new BigDecimal("3000"))
        .estImpressionsMin(850_000L)
        .estImpressionsMax(1_150_000L)
        .estClicksMin(21_250L)
        .estClicksMax(28_750L)
        .cpcWon(new BigDecimal("120"))
        .pricingModelsAll(List.of("CPM", "CPC"))
        .minBudgetWonSnap(3_000L)
        .audienceSummarySnap("30대 여성")
        .executable(true)
        .shortfallWon(null)
        .build();
  }
}
