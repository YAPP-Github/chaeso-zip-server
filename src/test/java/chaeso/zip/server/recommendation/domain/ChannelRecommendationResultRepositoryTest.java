package chaeso.zip.server.recommendation.domain;

import static chaeso.zip.server.support.ChannelCatalogFixture.persistableChannel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import chaeso.zip.server.channel.domain.vo.PricingModel;
import chaeso.zip.server.onboarding.domain.entity.Onboarding;
import chaeso.zip.server.recommendation.domain.entity.ChannelRecommendation;
import chaeso.zip.server.recommendation.domain.entity.ChannelRecommendationResult;
import chaeso.zip.server.recommendation.domain.repository.ChannelRecommendationRepository;
import chaeso.zip.server.recommendation.domain.repository.ChannelRecommendationResultRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@PostgresDataJpaTest
class ChannelRecommendationResultRepositoryTest {

  @Autowired
  private ChannelRecommendationResultRepository channelRecommendationResultRepository;

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
  @DisplayName("저장하면 uuid 식별자와 저장 시각이 채워진다")
  void savesWithGeneratedIdAndCreatedAt() {
    ChannelRecommendationResult saved =
        channelRecommendationResultRepository.saveAndFlush(result(onboardingId));

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getServiceName()).isEqualTo("채소집");
  }

  @Test
  @DisplayName("같은 온보딩으로 추천 1건을 두 번 만들면 유니크 제약으로 막는다")
  void rejectsSecondResultForSameOnboarding() {
    channelRecommendationResultRepository.saveAndFlush(result(onboardingId));

    assertThatThrownBy(() -> channelRecommendationResultRepository.saveAndFlush(
        result(onboardingId))).isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("사용자 기준으로 최신순 페이지로 조회한다")
  void pagesResultsOfUserFromLatest() {
    // 활성 온보딩은 사용자당 하나뿐이라, 이전 온보딩은 비활성으로 내린 뒤 새로 제출한 상황을 만든다
    Onboarding previousOnboarding = entityManager.find(Onboarding.class, onboardingId);
    previousOnboarding.deactivate();
    entityManager.flush();
    UUID otherOnboardingId = entityManager.persistAndFlush(
        OnboardingFixture.onboarding(userId)).getId();
    UUID othersUserId = entityManager.persistAndFlush(
        UserFixture.user(UUID.randomUUID() + "@example.com")).getId();
    UUID othersOnboardingId = entityManager.persistAndFlush(
        OnboardingFixture.onboarding(othersUserId)).getId();

    UUID older = channelRecommendationResultRepository.saveAndFlush(result(onboardingId)).getId();
    UUID newer =
        channelRecommendationResultRepository.saveAndFlush(result(otherOnboardingId)).getId();
    channelRecommendationResultRepository.saveAndFlush(
        result(othersOnboardingId, othersUserId, "남의 서비스"));
    entityManager.clear();

    Page<ChannelRecommendationResult> page = channelRecommendationResultRepository
        .findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(0, 5));

    // 같은 저장 시각이면 id 내림차순으로 순서가 정해진다
    assertThat(page.getTotalElements()).isEqualTo(2);
    assertThat(page.getContent()).extracting(ChannelRecommendationResult::getId)
        .containsExactlyInAnyOrder(older, newer);
  }

  @Test
  @DisplayName("추천 1건을 지우면 그 건의 채널별 행도 함께 사라진다")
  void deletesItemsWithResult() {
    UUID resultId = channelRecommendationResultRepository.saveAndFlush(result(onboardingId)).getId();
    channelRecommendationRepository.saveAndFlush(recommendation(resultId));

    int deleted = channelRecommendationResultRepository.deleteByOnboardingId(onboardingId);
    entityManager.clear();

    assertThat(deleted).isEqualTo(1);
    assertThat(channelRecommendationRepository.findByResultIdInOrderByRankAsc(List.of(resultId)))
        .isEmpty();
  }

  private ChannelRecommendationResult result(UUID onboarding) {
    return result(onboarding, userId, "채소집");
  }

  private ChannelRecommendationResult result(UUID onboarding, UUID user, String serviceName) {
    return ChannelRecommendationResult.builder()
        .userId(user)
        .onboardingId(onboarding)
        .serviceName(serviceName)
        .build();
  }

  private ChannelRecommendation recommendation(UUID resultId) {
    return ChannelRecommendation.builder()
        .userId(userId)
        .onboardingId(onboardingId)
        .resultId(resultId)
        .channelId(channelId)
        .rank(1)
        .score(78)
        .reason("쇼핑·커머스 업종, 설정한 광고 목적에 적합하고 예산 내 집행이 가능해요")
        .channelName("11번가 광고")
        .estPricingModel(PricingModel.CPM)
        .estUnitPrice(new BigDecimal("3000"))
        .executable(true)
        .build();
  }
}
