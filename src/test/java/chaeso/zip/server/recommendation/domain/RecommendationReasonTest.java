package chaeso.zip.server.recommendation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import chaeso.zip.server.channel.domain.vo.Category;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecommendationReasonTest {

  private static final Category INDUSTRY = Category.MEDICAL_HEALTHCARE;

  @Test
  @DisplayName("맞은 축을 업종 → 목적 → 연령 순서로 한 문장에 담는다")
  void composesMatchedAxesInFixedOrder() {
    String reason = RecommendationReason.of(
        new MatchScore(Set.of(MatchAxis.AGE_BAND, MatchAxis.OBJECTIVE, MatchAxis.CATEGORY)),
        INDUSTRY, null, true);

    assertThat(reason)
        .isEqualTo("의료·헬스케어 업종, 설정한 광고 목적, 타깃 연령대에 적합하고 예산 내 집행이 가능해요");
  }

  @Test
  @DisplayName("업종 축이 맞으면 업종명을 문장에 넣는다")
  void namesMatchedIndustry() {
    String reason = RecommendationReason.of(new MatchScore(Set.of(MatchAxis.CATEGORY)),
        Category.FOOD_BEVERAGE, null, true);

    assertThat(reason).startsWith("음식·음료 업종에 적합하고");
  }

  @Test
  @DisplayName("집행 불가면 부족액을 천 단위로 끊어 알려준다")
  void reportsShortfall() {
    String reason = RecommendationReason.of(new MatchScore(Set.of(MatchAxis.OBJECTIVE)),
        INDUSTRY, 1_500_000L, true);

    assertThat(reason).isEqualTo("설정한 광고 목적에 적합하지만 집행에는 1,500,000원이 더 필요해요");
  }

  @Test
  @DisplayName("등록된 단가가 없으면 집행 가능 여부를 말하지 않고 문의로 안내한다")
  void guidesToQuoteWithoutPricing() {
    String reason = RecommendationReason.of(new MatchScore(Set.of(MatchAxis.CATEGORY)),
        INDUSTRY, null, false);

    assertThat(reason).isEqualTo("의료·헬스케어 업종에 적합해요. 등록된 단가가 없어 집행 금액은 문의가 필요해요");
  }

  @Test
  @DisplayName("어떤 조합에서도 최상급으로 단정하지 않는다")
  void neverClaimsSuperlative() {
    // "가장 적합한 매체" 는 점수 1위라는 뜻이 되는데 동점 채널이 있을 수 있어 보장할 수 없다
    List<Long> shortfalls = List.of(0L, 500_000L);
    for (MatchAxis axis : MatchAxis.values()) {
      for (Long shortfall : shortfalls) {
        for (boolean quoted : List.of(true, false)) {
          String reason = RecommendationReason.of(new MatchScore(Set.of(axis)), INDUSTRY,
              shortfall == 0 ? null : shortfall, quoted);

          assertThat(reason).doesNotContain("가장", "최고", "최적", "1위");
        }
      }
    }
  }
}
