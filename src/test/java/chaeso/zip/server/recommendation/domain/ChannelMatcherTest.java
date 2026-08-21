package chaeso.zip.server.recommendation.domain;

import static chaeso.zip.server.support.ChannelCatalogFixture.channel;
import static chaeso.zip.server.support.ChannelCatalogFixture.product;
import static chaeso.zip.server.support.ChannelCatalogFixture.withObjectives;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import chaeso.zip.server.channel.domain.entity.Channel;
import chaeso.zip.server.channel.domain.entity.ChannelProduct;
import chaeso.zip.server.channel.domain.vo.AgeBand;
import chaeso.zip.server.channel.domain.vo.CampaignObjective;
import chaeso.zip.server.channel.domain.vo.Category;
import chaeso.zip.server.channel.domain.vo.Gender;
import chaeso.zip.server.onboarding.domain.entity.Onboarding;
import chaeso.zip.server.onboarding.domain.vo.CampaignPeriod;
import chaeso.zip.server.support.OnboardingFixture;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ChannelMatcherTest {

  private static final Category INDUSTRY = Category.MEDICAL_HEALTHCARE;
  private static final CampaignObjective OBJECTIVE = CampaignObjective.AWARENESS;
  private static final List<AgeBand> TARGET_AGE_BANDS =
      List.of(AgeBand.AGE_20S, AgeBand.AGE_30S);

  private static final List<AgeBand> ALL_AGE_BANDS = List.of(AgeBand.AGE_10S, AgeBand.AGE_20S,
      AgeBand.AGE_30S, AgeBand.AGE_40S, AgeBand.AGE_50S_PLUS);

  /** 소수점 오차만 허용한다. */
  private static final double TOLERANCE = 0.001;

  private static final Onboarding ONBOARDING = OnboardingFixture.onboarding(
      INDUSTRY, OBJECTIVE, TARGET_AGE_BANDS, 1_000_000L, 3_000_000L, CampaignPeriod.M1);

  @Nested
  @DisplayName("업종 축")
  class CategoryAxis {

    @Test
    @DisplayName("매체 대표 업종과 같으면 만점이다")
    void scoresFullOnPrimaryCategory() {
      MatchScore score =
          match(primaryChannel(INDUSTRY, List.of(INDUSTRY), TARGET_AGE_BANDS), OBJECTIVE);

      assertThat(score.fitOf(MatchAxis.CATEGORY)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("적합 업종 목록에만 들어 있으면 부분 점수만 준다")
    void scoresPartialOnSuitableCategory() {
      MatchScore score = match(suitableChannel(List.of(INDUSTRY)), OBJECTIVE);

      assertThat(score.fitOf(MatchAxis.CATEGORY)).isEqualTo(0.8);
    }

    @Test
    @DisplayName("적합 업종을 넓게 잡은 매체일수록 업종 점수가 낮다")
    void discountsBroadSuitableCategories() {
      double focused = match(suitableChannel(List.of(INDUSTRY)), OBJECTIVE)
          .fitOf(MatchAxis.CATEGORY);
      double broad = match(
          suitableChannel(List.of(INDUSTRY, Category.GAME, Category.EDUCATION)), OBJECTIVE)
          .fitOf(MatchAxis.CATEGORY);

      assertThat(broad).isLessThan(focused);
      assertThat(broad).isEqualTo(0.6);
    }

    @Test
    @DisplayName("적합 업종에 없으면 0 점이다")
    void scoresZeroWhenIndustryAbsent() {
      MatchScore score = match(suitableChannel(List.of(Category.GAME)), OBJECTIVE);

      assertThat(score.fitOf(MatchAxis.CATEGORY)).isZero();
      assertThat(score.appliedAxes()).contains(MatchAxis.CATEGORY);
    }

    @Test
    @DisplayName("대표 업종도 적합 업종도 없으면 판정하지 않고 신뢰도를 낮춘다")
    void marksUnknownWithoutCategoryData() {
      MatchScore score = match(primaryChannel(null, null, TARGET_AGE_BANDS), OBJECTIVE);

      assertThat(score.appliedAxes()).doesNotContain(MatchAxis.CATEGORY);
      assertThat(score.unknownAxes()).containsExactly(MatchAxis.CATEGORY);
      assertThat(score.confidence()).isEqualTo(0.85);   // 1 - 0.5 * 30/100
    }
  }

  @Nested
  @DisplayName("광고 목적 축")
  class ObjectiveAxis {

    @Test
    @DisplayName("목적을 명시한 상품이 모두 지원하면 만점이다")
    void scoresFullWhenEveryProductSupports() {
      MatchScore score = ChannelMatcher.match(ONBOARDING, suitableChannel(List.of(INDUSTRY)),
          List.of(products(OBJECTIVE), products(OBJECTIVE, CampaignObjective.TRAFFIC)));

      assertThat(score.fitOf(MatchAxis.OBJECTIVE)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("상품 일부만 지원하면 지원 비율만큼 낮아진다")
    void discountsPartialProductSupport() {
      MatchScore score = ChannelMatcher.match(ONBOARDING, suitableChannel(List.of(INDUSTRY)),
          List.of(products(OBJECTIVE), products(CampaignObjective.CONVERSION)));

      // 지원 비율 1/2 → 0.55 + 0.45 * 0.5
      assertThat(score.fitOf(MatchAxis.OBJECTIVE)).isEqualTo(0.775);
    }

    @Test
    @DisplayName("같은 퍼널 단계의 인접 목적만 지원하면 부분 점수를 주되 근거로는 말하지 않는다")
    void scoresAdjacentObjectiveWithoutClaimingMatch() {
      MatchScore score = ChannelMatcher.match(ONBOARDING, suitableChannel(List.of(INDUSTRY)),
          List.of(products(CampaignObjective.VIDEO_VIEW)));

      assertThat(score.fitOf(MatchAxis.OBJECTIVE)).isEqualTo(0.25);
      assertThat(score.matchedAxes()).doesNotContain(MatchAxis.OBJECTIVE);
    }

    @Test
    @DisplayName("퍼널이 먼 목적만 지원하면 0 점이다")
    void scoresZeroOnUnrelatedObjective() {
      MatchScore score = ChannelMatcher.match(ONBOARDING, suitableChannel(List.of(INDUSTRY)),
          List.of(products(CampaignObjective.CONVERSION)));

      assertThat(score.fitOf(MatchAxis.OBJECTIVE)).isZero();
    }

    @Test
    @DisplayName("지원 목적을 밝힌 상품이 하나도 없으면 판정하지 않고 신뢰도를 낮춘다")
    void marksUnknownWithoutDeclaredObjectives() {
      MatchScore score = ChannelMatcher.match(ONBOARDING, suitableChannel(List.of(INDUSTRY)),
          List.of(product(UUID.randomUUID(), UUID.randomUUID())));

      assertThat(score.unknownAxes()).containsExactly(MatchAxis.OBJECTIVE);
      assertThat(score.confidence()).isEqualTo(0.875);   // 1 - 0.5 * 25/100
    }
  }

  @Nested
  @DisplayName("연령 축")
  class AgeBandAxis {

    @Test
    @DisplayName("타깃 연령대와 정확히 같으면 만점이다")
    void scoresFullOnExactAgeBands() {
      MatchScore score = match(suitableChannel(List.of(INDUSTRY), TARGET_AGE_BANDS), OBJECTIVE);

      assertThat(score.fitOf(MatchAxis.AGE_BAND)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("전 연령을 덮는 매체는 타깃을 다 덮어도 오디언스가 흩어져 있어 깎인다")
    void discountsBroadAudience() {
      MatchScore score = match(suitableChannel(List.of(INDUSTRY), ALL_AGE_BANDS), OBJECTIVE);

      // 커버리지 2/2, 정밀도 2/5 의 조화평균
      assertThat(score.fitOf(MatchAxis.AGE_BAND)).isCloseTo(0.5714, within(TOLERANCE));
    }

    @Test
    @DisplayName("타깃 일부만 덮는 매체는 커버리지만큼 깎인다")
    void discountsPartialCoverage() {
      MatchScore score =
          match(suitableChannel(List.of(INDUSTRY), List.of(AgeBand.AGE_20S)), OBJECTIVE);

      // 커버리지 1/2, 정밀도 1/1 의 조화평균
      assertThat(score.fitOf(MatchAxis.AGE_BAND)).isCloseTo(0.6667, within(TOLERANCE));
    }

    @Test
    @DisplayName("겹치는 연령대가 없으면 0 점이다")
    void scoresZeroWithoutOverlap() {
      MatchScore score =
          match(suitableChannel(List.of(INDUSTRY), List.of(AgeBand.AGE_50S_PLUS)), OBJECTIVE);

      assertThat(score.fitOf(MatchAxis.AGE_BAND)).isZero();
    }

    @Test
    @DisplayName("채널 연령대 정보가 없으면 판정하지 않고 신뢰도를 낮춘다")
    void marksUnknownWithoutChannelAgeBands() {
      MatchScore score = match(suitableChannel(List.of(INDUSTRY), List.of()), OBJECTIVE);

      assertThat(score.unknownAxes()).containsExactly(MatchAxis.AGE_BAND);
      assertThat(score.confidence()).isEqualTo(0.9);   // 1 - 0.5 * 20/100
    }
  }

  @Nested
  @DisplayName("연령대를 모르는 온보딩")
  class UndecidedAgeBand {

    private static final Onboarding UNDECIDED_ONBOARDING = OnboardingFixture.onboarding(
        INDUSTRY, OBJECTIVE, List.of(AgeBand.UNDECIDED), 1_000_000L, 3_000_000L, CampaignPeriod.M1);

    @Test
    @DisplayName("연령 축을 빼되 사용자가 답하지 않은 축이므로 신뢰도는 깎지 않는다")
    void dropsAgeBandAxisWithoutPenalty() {
      MatchScore score = ChannelMatcher.match(UNDECIDED_ONBOARDING,
          suitableChannel(List.of(INDUSTRY), ALL_AGE_BANDS), List.of(products(OBJECTIVE)));

      assertThat(score.appliedAxes())
          .containsExactly(MatchAxis.CATEGORY, MatchAxis.OBJECTIVE);
      assertThat(score.maxScore()).isEqualTo(55);
      assertThat(score.confidence()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("채널 타깃 연령대와 무관하게 연령 배점을 주지 않는다")
    void neverScoresAgeBand() {
      MatchScore score = ChannelMatcher.match(UNDECIDED_ONBOARDING,
          suitableChannel(List.of(Category.GAME), ALL_AGE_BANDS),
          List.of(products(CampaignObjective.CONVERSION)));

      assertThat(score.fitOf(MatchAxis.AGE_BAND)).isZero();
      assertThat(score.isMatched()).isFalse();
    }
  }

  @Nested
  @DisplayName("적합도 종합")
  class Overall {

    @Test
    @DisplayName("모든 축이 만점일 때만 적합도가 100% 다")
    void reachesFullRateOnlyOnPerfectFit() {
      MatchScore score = ChannelMatcher.match(ONBOARDING,
          primaryChannel(INDUSTRY, List.of(INDUSTRY), TARGET_AGE_BANDS),
          List.of(products(OBJECTIVE)));

      assertThat(score.matchRate()).isEqualTo(100);
    }

    @Test
    @DisplayName("세 축이 다 겹쳐도 넓게 잡은 매체는 100% 가 되지 않는다")
    void doesNotGiveFullRateToBroadChannel() {
      // 적합 업종 3개 · 전 연령 · 상품 2개 중 1개만 목적 지원 — 이진 판정으로는 세 축 모두 '맞음'
      MatchScore score = ChannelMatcher.match(ONBOARDING,
          suitableChannel(List.of(INDUSTRY, Category.GAME, Category.EDUCATION), ALL_AGE_BANDS),
          List.of(products(OBJECTIVE), products(CampaignObjective.CONVERSION)));

      assertThat(score.matchedAxes())
          .containsExactly(MatchAxis.CATEGORY, MatchAxis.OBJECTIVE, MatchAxis.AGE_BAND);
      assertThat(score.matchRate()).isEqualTo(65);
    }

    @Test
    @DisplayName("만점은 적용된 축 배점의 합이라 배점을 조정해도 적합도 %가 어긋나지 않는다")
    void maxScoreIsSumOfAppliedWeights() {
      MatchScore score = ChannelMatcher.match(ONBOARDING,
          primaryChannel(INDUSTRY, List.of(INDUSTRY), TARGET_AGE_BANDS),
          List.of(products(OBJECTIVE)));

      assertThat(score.maxScore()).isEqualTo(MatchAxis.CATEGORY.getWeight()
          + MatchAxis.OBJECTIVE.getWeight() + MatchAxis.AGE_BAND.getWeight());
    }

    @Test
    @DisplayName("어느 축도 맞지 않으면 추천 후보가 아니다")
    void marksUnmatchedChannel() {
      MatchScore score = ChannelMatcher.match(ONBOARDING,
          suitableChannel(List.of(Category.GAME), List.of(AgeBand.AGE_50S_PLUS)),
          List.of(products(CampaignObjective.CONVERSION)));

      assertThat(score.isMatched()).isFalse();
      assertThat(score.score()).isZero();
      assertThat(score.matchRate()).isZero();
    }

    @Test
    @DisplayName("예산만 맞는 채널은 캠페인 조건이 맞은 것이 아니라 후보가 아니다")
    void doesNotTreatBudgetOnlyFitAsMatch() {
      MatchScore score = ChannelMatcher.match(ONBOARDING,
              suitableChannel(List.of(Category.GAME), List.of(AgeBand.AGE_50S_PLUS)),
              List.of(products(CampaignObjective.CONVERSION)))
          .with(MatchAxis.BUDGET, 1.0);

      assertThat(score.matchedAxes()).containsExactly(MatchAxis.BUDGET);
      assertThat(score.isMatched()).isFalse();
    }

    @Test
    @DisplayName("소개서에 값이 없는 축은 배점에서 빼고 예외를 내지 않는다")
    void treatsMissingChannelDataAsUnknown() {
      Channel channel = channel(UUID.randomUUID(), "정보 없는 채널");

      MatchScore score = ChannelMatcher.match(ONBOARDING, channel,
          List.of(product(UUID.randomUUID(), channel.getId())));

      assertThat(score.appliedAxes()).isEmpty();
      assertThat(score.unknownAxes())
          .containsExactly(MatchAxis.CATEGORY, MatchAxis.OBJECTIVE, MatchAxis.AGE_BAND);
      assertThat(score.matchRate()).isZero();
      assertThat(score.isMatched()).isFalse();
    }
  }

  private static MatchScore match(Channel channel, CampaignObjective supported) {
    return ChannelMatcher.match(ONBOARDING, channel, List.of(products(supported)));
  }

  private static Channel suitableChannel(List<Category> suitableCategories) {
    return suitableChannel(suitableCategories, TARGET_AGE_BANDS);
  }

  private static Channel suitableChannel(List<Category> suitableCategories,
      List<AgeBand> ageBandCodes) {
    return channel(UUID.randomUUID(), "테스트 채널", suitableCategories, ageBandCodes, "30대",
        Gender.FEMALE);
  }

  private static Channel primaryChannel(Category primaryCategory,
      List<Category> suitableCategories, List<AgeBand> ageBandCodes) {
    return channel(UUID.randomUUID(), "테스트 채널", primaryCategory, suitableCategories,
        ageBandCodes);
  }

  private static ChannelProduct products(CampaignObjective... objectives) {
    return withObjectives(product(UUID.randomUUID(), UUID.randomUUID()), objectives);
  }
}
