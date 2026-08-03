package chaeso.zip.server.recommendation.domain;

import static chaeso.zip.server.support.ChannelCatalogFixture.channel;
import static chaeso.zip.server.support.ChannelCatalogFixture.product;
import static chaeso.zip.server.support.ChannelCatalogFixture.withObjectives;
import static org.assertj.core.api.Assertions.assertThat;

import chaeso.zip.server.channel.domain.entity.Channel;
import chaeso.zip.server.channel.domain.entity.ChannelProduct;
import chaeso.zip.server.channel.domain.vo.AgeBand;
import chaeso.zip.server.channel.domain.vo.CampaignObjective;
import chaeso.zip.server.channel.domain.vo.Category;
import chaeso.zip.server.channel.domain.vo.Gender;
import chaeso.zip.server.onboarding.domain.entity.Onboarding;
import chaeso.zip.server.onboarding.domain.vo.CampaignPeriod;
import chaeso.zip.server.support.OnboardingFixture;
import java.util.Arrays;
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

  private static final Onboarding ONBOARDING = OnboardingFixture.onboarding(
      INDUSTRY, OBJECTIVE, TARGET_AGE_BANDS, 1_000_000L, 3_000_000L, CampaignPeriod.M1);

  @Nested
  @DisplayName("축별 배점")
  class Axes {

    @Test
    @DisplayName("업종만 맞으면 업종 배점만 얻는다")
    void scoresCategoryOnly() {
      MatchScore score = ChannelMatcher.match(ONBOARDING,
          matchingChannel(List.of(INDUSTRY), List.of(AgeBand.AGE_50S_PLUS)),
          List.of(products(CampaignObjective.CONVERSION)));

      assertThat(score.matchedAxes()).containsExactly(MatchAxis.CATEGORY);
      assertThat(score.score()).isEqualTo(40);
      assertThat(score.matchRate()).isEqualTo(44);   // 40 / 90
    }

    @Test
    @DisplayName("광고 목적만 맞으면 목적 배점만 얻는다")
    void scoresObjectiveOnly() {
      MatchScore score = ChannelMatcher.match(ONBOARDING,
          matchingChannel(List.of(Category.GAME), List.of(AgeBand.AGE_50S_PLUS)),
          List.of(products(OBJECTIVE)));

      assertThat(score.matchedAxes()).containsExactly(MatchAxis.OBJECTIVE);
      assertThat(score.score()).isEqualTo(30);
      assertThat(score.matchRate()).isEqualTo(33);
    }

    @Test
    @DisplayName("타깃 연령대가 하나라도 겹치면 연령 배점을 얻는다")
    void scoresAgeBandOnOverlap() {
      MatchScore score = ChannelMatcher.match(ONBOARDING,
          matchingChannel(List.of(Category.GAME), List.of(AgeBand.AGE_30S, AgeBand.AGE_50S_PLUS)),
          List.of(products(CampaignObjective.CONVERSION)));

      assertThat(score.matchedAxes()).containsExactly(MatchAxis.AGE_BAND);
      assertThat(score.score()).isEqualTo(20);
      assertThat(score.matchRate()).isEqualTo(22);
    }

    @Test
    @DisplayName("일부 축만 맞아도 적합도가 남아 추천 후보가 된다")
    void keepsPartialMatchAsCandidate() {
      MatchScore score = ChannelMatcher.match(ONBOARDING,
          matchingChannel(List.of(INDUSTRY), List.of(AgeBand.AGE_50S_PLUS)),
          List.of(products(OBJECTIVE)));

      assertThat(score.matchedAxes())
          .containsExactly(MatchAxis.CATEGORY, MatchAxis.OBJECTIVE);
      assertThat(score.score()).isEqualTo(70);
      assertThat(score.matchRate()).isEqualTo(78);   // 70 / 90
      assertThat(score.isMatched()).isTrue();
    }

    @Test
    @DisplayName("세 축이 모두 맞으면 적합도가 100% 다")
    void scoresFullMatch() {
      MatchScore score = ChannelMatcher.match(ONBOARDING,
          matchingChannel(List.of(Category.GAME, INDUSTRY), List.of(AgeBand.AGE_20S)),
          List.of(products(OBJECTIVE)));

      assertThat(score.score()).isEqualTo(MatchAxis.MAX_SCORE);
      assertThat(score.matchRate()).isEqualTo(100);
    }

    @Test
    @DisplayName("만점은 축 배점의 합이라 배점을 조정해도 적합도 %가 어긋나지 않는다")
    void maxScoreIsSumOfWeights() {
      assertThat(MatchAxis.MAX_SCORE)
          .isEqualTo(Arrays.stream(MatchAxis.values()).mapToInt(MatchAxis::getWeight).sum());
    }
  }

  @Nested
  @DisplayName("매칭 판정")
  class Matching {

    @Test
    @DisplayName("어느 축도 맞지 않으면 추천 후보가 아니다")
    void marksUnmatchedChannel() {
      MatchScore score = ChannelMatcher.match(ONBOARDING,
          matchingChannel(List.of(Category.GAME), List.of(AgeBand.AGE_50S_PLUS)),
          List.of(products(CampaignObjective.CONVERSION)));

      assertThat(score.isMatched()).isFalse();
      assertThat(score.score()).isZero();
      assertThat(score.matchRate()).isZero();
    }

    @Test
    @DisplayName("광고 목적은 상품 하나가 아니라 채널 상품 전체의 합집합으로 판단한다")
    void matchesObjectiveAcrossProducts() {
      MatchScore score = ChannelMatcher.match(ONBOARDING,
          matchingChannel(List.of(Category.GAME), List.of(AgeBand.AGE_50S_PLUS)),
          List.of(products(CampaignObjective.CONVERSION), products(OBJECTIVE)));

      assertThat(score.matchedAxes()).containsExactly(MatchAxis.OBJECTIVE);
    }

    @Test
    @DisplayName("상품이 없는 채널은 목적을 지원하지 않는 것으로 본다")
    void doesNotMatchObjectiveWithoutProducts() {
      MatchScore score = ChannelMatcher.match(ONBOARDING,
          matchingChannel(List.of(INDUSTRY), List.of(AgeBand.AGE_20S)), List.of());

      assertThat(score.matchedAxes())
          .containsExactly(MatchAxis.CATEGORY, MatchAxis.AGE_BAND);
    }

    @Test
    @DisplayName("소개서에 값이 없는 축은 매칭 실패로 두고 예외를 내지 않는다")
    void treatsMissingChannelDataAsUnmatched() {
      // 적합 업종·연령대·지원 목적이 모두 비어 있는 채널
      Channel channel = channel(UUID.randomUUID(), "정보 없는 채널");

      MatchScore score = ChannelMatcher.match(ONBOARDING, channel,
          List.of(product(UUID.randomUUID(), channel.getId())));

      assertThat(score.isMatched()).isFalse();
    }
  }

  private static Channel matchingChannel(List<Category> suitableCategories,
      List<AgeBand> ageBandCodes) {
    return channel(UUID.randomUUID(), "테스트 채널", suitableCategories, ageBandCodes,
        "30대", Gender.FEMALE);
  }

  private static ChannelProduct products(CampaignObjective... objectives) {
    return withObjectives(product(UUID.randomUUID(), UUID.randomUUID()), objectives);
  }
}
