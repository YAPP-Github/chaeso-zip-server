package chaeso.zip.server.recommendation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import chaeso.zip.server.channel.domain.vo.Category;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RecommendationReasonTest {

  private static final Category INDUSTRY = Category.MEDICAL_HEALTHCARE;

  /** 강도 단계별 대표 적합 정도 */
  private static final double EXACT = 1.0;
  private static final double STRONG = 0.85;
  private static final double PARTIAL = 0.6;
  private static final double WEAK = 0.3;

  @Nested
  @DisplayName("잘 맞은 축을 주어로 세운다")
  class Subjects {

    @Test
    @DisplayName("맞은 축을 업종 → 목적 → 연령 순서로 한 문장에 담는다")
    void composesMatchedAxesInFixedOrder() {
      String reason = of(fits(STRONG, STRONG, STRONG), null, true);

      assertThat(reason)
          .isEqualTo("의료·헬스케어 업종, 설정한 광고 목적, 타깃 연령대에 적합하고 예산 내 집행이 가능해요");
    }

    @Test
    @DisplayName("타깃 연령대와 정확히 포개지는 매체는 대체로 겹치는 매체와 다르게 말한다")
    void distinguishesExactAgeBandOverlap() {
      assertThat(of(fits(STRONG, STRONG, EXACT), null, true))
          .isEqualTo("의료·헬스케어 업종, 설정한 광고 목적, 타깃 연령대 전체에 적합하고 예산 내 집행이 가능해요");
      assertThat(of(fits(STRONG, STRONG, STRONG), null, true))
          .isEqualTo("의료·헬스케어 업종, 설정한 광고 목적, 타깃 연령대에 적합하고 예산 내 집행이 가능해요");
    }

    @Test
    @DisplayName("업종 축을 말할 때 업종명을 문장에 넣는다")
    void namesMatchedIndustry() {
      String reason = RecommendationReason.of(fits(STRONG, null, null), Category.FOOD_BEVERAGE,
          null, true);

      assertThat(reason).startsWith("음식·음료 업종에 적합하고");
    }

    @Test
    @DisplayName("잘 맞은 축이 없으면 서술을 낮춰 단정하지 않는다")
    void softensPredicateWithoutStrongAxis() {
      String reason = of(fits(PARTIAL, PARTIAL, null), null, true);

      assertThat(reason)
          .isEqualTo("의료·헬스케어 업종, 설정한 광고 목적에 대체로 맞고 예산 내 집행이 가능해요");
    }
  }

  @Nested
  @DisplayName("덜 맞은 축은 문장에 넣지 않는다")
  class LessFitAxes {

    @Test
    @DisplayName("일부만 맞은 축은 주어에서 빼고 단서로도 남기지 않는다")
    void omitsPartialAxis() {
      String reason = of(fits(STRONG, STRONG, PARTIAL), null, true);

      assertThat(reason)
          .isEqualTo("의료·헬스케어 업종, 설정한 광고 목적에 적합하고 예산 내 집행이 가능해요")
          .doesNotContain("연령대");
    }

    @Test
    @DisplayName("사실상 맞지 않는 축은 말하지 않는다")
    void omitsWeakAxis() {
      String reason = of(fits(STRONG, STRONG, WEAK), null, true);

      assertThat(reason)
          .isEqualTo("의료·헬스케어 업종, 설정한 광고 목적에 적합하고 예산 내 집행이 가능해요")
          .doesNotContain("연령대");
    }

    @Test
    @DisplayName("가장 잘 맞은 단계의 축만 주어로 남긴다")
    void keepsOnlyLeadTierAxes() {
      assertThat(of(fits(PARTIAL, STRONG, WEAK), null, true))
          .isEqualTo("설정한 광고 목적에 적합하고 예산 내 집행이 가능해요");
      assertThat(of(fits(PARTIAL, PARTIAL, STRONG), null, true))
          .isEqualTo("타깃 연령대에 적합하고 예산 내 집행이 가능해요");
    }

    @Test
    @DisplayName("어떤 조합에서도 근거를 뒤집는 단서를 붙이지 않는다")
    void neverAppendsAdversativeCaveat() {
      List<Double> levels = List.of(EXACT, STRONG, PARTIAL, WEAK);
      for (Double category : levels) {
        for (Double objective : levels) {
          for (Double ageBand : levels) {
            String reason = of(fits(category, objective, ageBand), null, true);

            assertThat(reason).doesNotContain("다만", "일부만 맞아요");
          }
        }
      }
    }

    @Test
    @DisplayName("판정하지 못한 축은 문장에 쓰지 않는다")
    void ignoresAxesWithoutBasis() {
      String reason = of(fits(STRONG, null, null), null, true);

      assertThat(reason)
          .isEqualTo("의료·헬스케어 업종에 적합하고 예산 내 집행이 가능해요")
          .doesNotContain("광고 목적", "연령대");
    }
  }

  @Nested
  @DisplayName("집행 가능 여부")
  class Executability {

    @Test
    @DisplayName("집행 불가면 부족액을 천 단위로 끊어 알려준다")
    void reportsShortfall() {
      String reason = of(fits(null, STRONG, null), 1_500_000L, true);

      assertThat(reason).isEqualTo("설정한 광고 목적에 적합하지만 집행에는 1,500,000원이 더 필요해요");
    }

    @Test
    @DisplayName("등록된 단가가 없으면 집행 가능 여부를 말하지 않고 문의로 안내한다")
    void guidesToQuoteWithoutPricing() {
      String reason = of(fits(STRONG, null, null), null, false);

      assertThat(reason).isEqualTo("의료·헬스케어 업종에 적합해요. 등록된 단가가 없어 집행 금액은 문의가 필요해요");
    }

    @Test
    @DisplayName("집행 부족액을 알려도 덜 맞은 축을 덧붙이지 않는다")
    void keepsSingleSentenceOnShortfall() {
      assertThat(of(fits(PARTIAL, STRONG, null), 500_000L, true))
          .isEqualTo("설정한 광고 목적에 적합하지만 집행에는 500,000원이 더 필요해요");
      assertThat(of(fits(PARTIAL, STRONG, null), null, false))
          .isEqualTo("설정한 광고 목적에 적합해요. 등록된 단가가 없어 집행 금액은 문의가 필요해요");
    }

    @Test
    @DisplayName("어떤 조합에서도 단가 문의 안내를 빼면 한 문장으로 끝난다")
    void neverExceedsTwoSentences() {
      List<Double> levels = List.of(EXACT, STRONG, PARTIAL, WEAK);
      for (Double category : levels) {
        for (Double objective : levels) {
          for (Double ageBand : levels) {
            for (Long shortfall : new Long[] {null, 500_000L}) {
              for (boolean quoted : List.of(true, false)) {
                String reason = of(fits(category, objective, ageBand), shortfall, quoted);

                assertThat(reason).doesNotContain("잘 맞지 않아요");
                // 단가 문의 안내가 붙는 경우에만 두 문장이 된다
                assertThat(reason.split("\\. ", -1)).hasSize(quoted ? 1 : 2);
              }
            }
          }
        }
      }
    }

    @Test
    @DisplayName("서술을 낮춰도 집행 문구와 자연스럽게 이어진다")
    void keepsClosingReadableOnSoftenedPredicate() {
      assertThat(of(fits(PARTIAL, null, null), null, true))
          .isEqualTo("의료·헬스케어 업종에 대체로 맞고 예산 내 집행이 가능해요");
      assertThat(of(fits(PARTIAL, null, null), 500_000L, true))
          .isEqualTo("의료·헬스케어 업종에 대체로 맞지만 집행에는 500,000원이 더 필요해요");
      assertThat(of(fits(PARTIAL, null, null), null, false))
          .isEqualTo("의료·헬스케어 업종에 대체로 맞아요. 등록된 단가가 없어 집행 금액은 문의가 필요해요");
    }
  }

  @Test
  @DisplayName("어떤 조합에서도 최상급으로 단정하지 않는다")
  void neverClaimsSuperlative() {
    // "가장 적합한 매체" 는 점수 1위라는 뜻이 되는데 동점 채널이 있을 수 있어 보장할 수 없다
    List<Double> levels = List.of(STRONG, PARTIAL, WEAK);
    for (Double category : levels) {
      for (Double objective : levels) {
        for (Double ageBand : levels) {
          for (Long shortfall : List.of(0L, 500_000L)) {
            for (boolean quoted : List.of(true, false)) {
              String reason = of(fits(category, objective, ageBand),
                  shortfall == 0 ? null : shortfall, quoted);

              assertThat(reason).doesNotContain("가장", "최고", "최적", "1위");
            }
          }
        }
      }
    }
  }

  private static String of(MatchScore score, Long shortfallWon, boolean quoted) {
    return RecommendationReason.of(score, INDUSTRY, shortfallWon, quoted);
  }

  /** 축별 적합 정도를 직접 지정한 적합도. {@code null} 인 축은 판정 근거가 없는 축이다. */
  private static MatchScore fits(Double category, Double objective, Double ageBand) {
    Map<MatchAxis, Double> fits = new EnumMap<>(MatchAxis.class);
    put(fits, MatchAxis.CATEGORY, category);
    put(fits, MatchAxis.OBJECTIVE, objective);
    put(fits, MatchAxis.AGE_BAND, ageBand);
    fits.put(MatchAxis.BUDGET, STRONG);
    return new MatchScore(fits, Set.of());
  }

  private static void put(Map<MatchAxis, Double> fits, MatchAxis axis, Double fit) {
    if (fit != null) {
      fits.put(axis, fit);
    }
  }
}
