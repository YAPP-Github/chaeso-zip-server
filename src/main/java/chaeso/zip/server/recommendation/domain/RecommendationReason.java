package chaeso.zip.server.recommendation.domain;

import chaeso.zip.server.channel.domain.vo.Category;
import java.util.Locale;
import java.util.StringJoiner;

public final class RecommendationReason {

  private static final String CATEGORY_SUBJECT = "%s 업종";
  private static final String OBJECTIVE_SUBJECT = "설정한 광고 목적";
  private static final String AGE_BAND_SUBJECT = "타깃 연령대";

  /** 업종 코드에 이름이 없을 때. 코드값을 그대로 노출하지 않는다. */
  private static final String UNNAMED_CATEGORY_SUBJECT = "설정한 업종";

  /** 맞은 축을 알 수 없을 때의 주어. 적합도 0인 채널은 추천하지 않으므로 실제로는 쓰이지 않는다. */
  private static final String FALLBACK_SUBJECT = "설정한 조건";

  private static final String SUBJECT_SEPARATOR = ", ";

  private static final String EXECUTABLE = "에 적합하고 예산 내 집행이 가능해요";
  private static final String SHORTFALL = "에 적합하지만 집행에는 %s원이 더 필요해요";
  private static final String QUOTE_REQUIRED = "에 적합해요. 등록된 단가가 없어 집행 금액은 문의가 필요해요";

  private RecommendationReason() {
  }

  /**
   * 추천 근거 문장.
   *
   * @param score        채널 적합도
   * @param industry     온보딩 업종. 업종 축이 맞았을 때 문장에 쓴다
   * @param shortfallWon 집행에 부족한 금액(원). 집행 가능하면 {@code null}
   * @param quoted       대표 단가를 알 수 있는지. 단가가 없으면 집행 가능 여부를 말하지 않는다
   */
  public static String of(MatchScore score, Category industry, Long shortfallWon, boolean quoted) {
    return subjects(score, industry) + predicate(shortfallWon, quoted);
  }

  private static String subjects(MatchScore score, Category industry) {
    StringJoiner joiner = new StringJoiner(SUBJECT_SEPARATOR);
    for (MatchAxis axis : score.matchedAxes()) {
      joiner.add(switch (axis) {
        case CATEGORY -> CATEGORY_SUBJECT.formatted(categoryName(industry));
        case OBJECTIVE -> OBJECTIVE_SUBJECT;
        case AGE_BAND -> AGE_BAND_SUBJECT;
      });
    }
    return score.isMatched() ? joiner.toString() : FALLBACK_SUBJECT;
  }

  private static String categoryName(Category industry) {
    if (industry == null || industry.getDescription() == null) {
      return UNNAMED_CATEGORY_SUBJECT;
    }
    return industry.getDescription();
  }

  private static String predicate(Long shortfallWon, boolean quoted) {
    if (!quoted) {
      return QUOTE_REQUIRED;
    }
    if (shortfallWon == null) {
      return EXECUTABLE;
    }
    return SHORTFALL.formatted(String.format(Locale.KOREA, "%,d", shortfallWon));
  }
}
