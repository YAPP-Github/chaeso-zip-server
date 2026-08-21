package chaeso.zip.server.recommendation.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 채널 하나의 적합도
 *
 * @param fits        적합도 계산에 적용된 축과 그 축의 적합 정도(0.0~1.0). 온보딩이 기준을 주지
 *                    않은 축은 만점에서도 빠지며, 빠졌다는 사실 자체로는 감점하지 않는다
 * @param unknownAxes 채널 소개서에 판정 근거가 없어 채점하지 못한 축. 만점에서 빠지는 대신
 *                    {@link #confidence()} 를 낮춰, 데이터가 부실한 채널이 남은 축만 맞고
 *                    상위를 차지하지 못하게 한다
 */
public record MatchScore(Map<MatchAxis, Double> fits, Set<MatchAxis> unknownAxes) {

  /** 근거 없는 축의 배점 비중에 곱해 신뢰도를 깎는 비율 */
  private static final double UNKNOWN_PENALTY_RATE = 0.5;

  private static final double FULL_CONFIDENCE = 1.0;

  public MatchScore {
    fits = immutableFits(fits);
    unknownAxes = immutableAxes(unknownAxes);
    requireDisjoint(fits.keySet(), unknownAxes);
  }

  /** 어느 축도 채점하지 못한 적합도 */
  public static MatchScore empty() {
    return new MatchScore(Map.of(), Set.of());
  }

  /** 축 하나의 적합 정도를 더한 새 적합도 */
  public MatchScore with(MatchAxis axis, double fit) {
    Map<MatchAxis, Double> merged = new EnumMap<>(fits);
    merged.put(axis, clamp(fit));
    Set<MatchAxis> remaining = EnumSet.noneOf(MatchAxis.class);
    remaining.addAll(unknownAxes);
    remaining.remove(axis);
    return new MatchScore(merged, remaining);
  }

  /** 축 하나를 근거 없음으로 표시한 새 적합도 */
  public MatchScore withUnknown(MatchAxis axis) {
    Map<MatchAxis, Double> remaining = new EnumMap<>(fits);
    remaining.remove(axis);
    Set<MatchAxis> unknown = EnumSet.noneOf(MatchAxis.class);
    unknown.addAll(unknownAxes);
    unknown.add(axis);
    return new MatchScore(remaining, unknown);
  }

  /** 적합도 계산에 적용된 축 */
  public Set<MatchAxis> appliedAxes() {
    return fits.keySet();
  }

  /** 추천 근거로 말할 만큼 맞은 축 */
  public Set<MatchAxis> matchedAxes() {
    Set<MatchAxis> matched = EnumSet.noneOf(MatchAxis.class);
    fits.forEach((axis, fit) -> {
      if (FitTier.of(fit) != FitTier.WEAK) {
        matched.add(axis);
      }
    });
    return Collections.unmodifiableSet(matched);
  }

  /** 축 하나가 얼마나 맞았는지의 단계 */
  public FitTier tierOf(MatchAxis axis) {
    return FitTier.of(fitOf(axis));
  }

  /** 축 하나의 적합 정도. 적용되지 않은 축은 0 */
  public double fitOf(MatchAxis axis) {
    return fits.getOrDefault(axis, 0.0);
  }

  /** 배점 합 */
  public double score() {
    double total = 0;
    for (Map.Entry<MatchAxis, Double> fit : fits.entrySet()) {
      total += fit.getKey().getWeight() * fit.getValue();
    }
    return total;
  }

  /** 만점 */
  public int maxScore() {
    return MatchAxis.totalWeight(fits.keySet());
  }

  /**
   * 판정 근거가 없어 빠진 축의 비중만큼 낮아지는 신뢰도(0.0~1.0). 적합도(%)에 그대로 곱한다.
   */
  public double confidence() {
    if (unknownAxes.isEmpty()) {
      return FULL_CONFIDENCE;
    }
    double unknownShare =
        (double) MatchAxis.totalWeight(unknownAxes) / MatchAxis.declaredWeight();
    return FULL_CONFIDENCE - UNKNOWN_PENALTY_RATE * unknownShare;
  }

  /**
   * 반올림하지 않은 적합도(%)
   */
  public double matchRateExact() {
    int maxScore = maxScore();
    if (maxScore == 0) {
      return 0;
    }
    return score() * 100.0 / maxScore * confidence();
  }

  /** 적합도(%) */
  public int matchRate() {
    return (int) Math.round(matchRateExact());
  }

  /**
   * 추천 후보로 남길지
   */
  public boolean isMatched() {
    return matchedAxes().stream().anyMatch(MatchAxis::isSubject);
  }

  private static double clamp(double fit) {
    if (Double.isNaN(fit)) {
      throw new IllegalArgumentException("적합 정도는 NaN 일 수 없습니다");
    }
    return Math.clamp(fit, 0.0, 1.0);
  }

  private static Map<MatchAxis, Double> immutableFits(Map<MatchAxis, Double> fits) {
    if (fits.isEmpty()) {
      return Map.of();
    }
    Map<MatchAxis, Double> copy = new EnumMap<>(MatchAxis.class);
    fits.forEach((axis, fit) -> copy.put(axis, clamp(fit)));
    return Collections.unmodifiableMap(copy);
  }

  private static Set<MatchAxis> immutableAxes(Set<MatchAxis> axes) {
    return axes.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(axes));
  }

  private static void requireDisjoint(Set<MatchAxis> applied, Set<MatchAxis> unknown) {
    for (MatchAxis axis : unknown) {
      if (applied.contains(axis)) {
        throw new IllegalArgumentException(
            "%s 축은 채점된 축이면서 근거 없는 축일 수 없습니다".formatted(axis));
      }
    }
  }
}
