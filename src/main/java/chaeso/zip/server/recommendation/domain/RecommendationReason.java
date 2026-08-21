package chaeso.zip.server.recommendation.domain;

import chaeso.zip.server.channel.domain.vo.Category;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 추천 근거 문장.
 *
 * <p>가장 잘 맞은 단계의 축만 주어로 내세우고, 그보다 덜 맞은 축은 말하지 않는다. 세 축이
 * "맞았다"로만 뭉뚱그려지면 적합도가 96%인 매체와 72%인 매체가 같은 문장을 받는다.
 */
public final class RecommendationReason {

  private static final String CATEGORY_SUBJECT = "%s 업종";
  private static final String OBJECTIVE_SUBJECT = "설정한 광고 목적";
  private static final String AGE_BAND_SUBJECT = "타깃 연령대";

  /** 타깃 연령대를 남김없이 덮으면서 그 밖으로 새지 않는 매체에만 쓰는 주어 */
  private static final String EXACT_AGE_BAND_SUBJECT = "타깃 연령대 전체";

  /** 완전 일치로 볼 적합 정도. 조화평균의 소수점 오차를 감안한다. */
  private static final double EXACT_FIT = 0.999;

  /** 업종 코드에 이름이 없을 때. 코드값을 그대로 노출하지 않는다. */
  private static final String UNNAMED_CATEGORY_SUBJECT = "설정한 업종";

  /** 맞은 축을 알 수 없을 때의 주어. 적합도 0인 채널은 추천하지 않으므로 실제로는 쓰이지 않는다. */
  private static final String FALLBACK_SUBJECT = "설정한 조건";

  private static final String SUBJECT_SEPARATOR = ", ";

  /** 강도별 서술. 예산 서술과 이어 붙일 어간과, 문장을 끝낼 종결형을 함께 둔다. */
  private static final Map<FitTier, Predicate> PREDICATES = new EnumMap<>(Map.of(
      FitTier.STRONG, new Predicate("에 적합하", "에 적합해요"),
      FitTier.PARTIAL, new Predicate("에 대체로 맞", "에 대체로 맞아요"),
      FitTier.WEAK, new Predicate("에 일부 맞", "에 일부 맞아요")));

  private static final String EXECUTABLE = "고 예산 내 집행이 가능해요";
  private static final String SHORTFALL = "지만 집행에는 %s원이 더 필요해요";
  private static final String QUOTE_REQUIRED = ". 등록된 단가가 없어 집행 금액은 문의가 필요해요";

  private RecommendationReason() {
  }

  /**
   * 추천 근거 문장.
   *
   * @param score        채널 적합도
   * @param industry     온보딩 업종. 업종 축을 말할 때 문장에 쓴다
   * @param shortfallWon 집행에 부족한 금액(원). 집행 가능하면 {@code null}
   * @param quoted       대표 단가를 알 수 있는지. 단가가 없으면 집행 가능 여부를 말하지 않는다
   */
  public static String of(MatchScore score, Category industry, Long shortfallWon, boolean quoted) {
    Map<FitTier, List<MatchAxis>> byTier = subjectAxesByTier(score);
    FitTier lead = leadTier(byTier);
    Predicate predicate = PREDICATES.get(lead);

    return subjects(byTier.get(lead), industry, score)
        + closing(predicate, shortfallWon, quoted);
  }

  /** 주어로 쓸 수 있는 축을 강도별로 모은다. 예산은 서술부가 따로 말하므로 뺀다. */
  private static Map<FitTier, List<MatchAxis>> subjectAxesByTier(MatchScore score) {
    Map<FitTier, List<MatchAxis>> byTier = new EnumMap<>(FitTier.class);
    for (MatchAxis axis : score.appliedAxes()) {
      if (axis.isSubject()) {
        byTier.computeIfAbsent(score.tierOf(axis), tier -> new ArrayList<>()).add(axis);
      }
    }
    return byTier;
  }

  /** 앞 문장의 주어가 될 단계. 가장 잘 맞은 축들만 근거로 내세운다. */
  private static FitTier leadTier(Map<FitTier, List<MatchAxis>> byTier) {
    for (FitTier tier : FitTier.values()) {
      if (byTier.containsKey(tier)) {
        return tier;
      }
    }
    return FitTier.WEAK;
  }

  private static String subjects(List<MatchAxis> axes, Category industry, MatchScore score) {
    if (axes == null || axes.isEmpty()) {
      return FALLBACK_SUBJECT;
    }
    StringJoiner joiner = new StringJoiner(SUBJECT_SEPARATOR);
    axes.forEach(axis -> joiner.add(subjectOf(axis, industry, score.fitOf(axis))));
    return joiner.toString();
  }

  /**
   * 축을 문장의 주어로 옮긴다. 연령은 완전히 겹칠 때와 대체로 겹칠 때를 갈라 말한다. 둘을 한
   * 문구로 묶으면 오디언스가 정확히 포개지는 매체가 그렇지 않은 매체와 같은 문장을 받는다.
   */
  private static String subjectOf(MatchAxis axis, Category industry, double fit) {
    return switch (axis) {
      case CATEGORY -> CATEGORY_SUBJECT.formatted(categoryName(industry));
      case OBJECTIVE -> OBJECTIVE_SUBJECT;
      case AGE_BAND -> fit >= EXACT_FIT ? EXACT_AGE_BAND_SUBJECT : AGE_BAND_SUBJECT;
      case BUDGET -> throw new IllegalStateException("예산은 주어로 쓰지 않습니다");
    };
  }

  private static String categoryName(Category industry) {
    if (industry == null || industry.getDescription() == null) {
      return UNNAMED_CATEGORY_SUBJECT;
    }
    return industry.getDescription();
  }

  /** 앞 문장을 집행 가능 여부로 맺는다. */
  private static String closing(Predicate predicate, Long shortfallWon, boolean quoted) {
    if (!quoted) {
      return predicate.terminal() + QUOTE_REQUIRED;
    }
    if (shortfallWon == null) {
      return predicate.connective() + EXECUTABLE;
    }
    return predicate.connective()
        + SHORTFALL.formatted(String.format(Locale.KOREA, "%,d", shortfallWon));
  }

  /** 강도별 서술. {@code connective} 는 예산 서술로 이어지고 {@code terminal} 은 문장을 끝낸다. */
  private record Predicate(String connective, String terminal) {
  }
}
