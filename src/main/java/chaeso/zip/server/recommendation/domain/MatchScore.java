package chaeso.zip.server.recommendation.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 채널 하나의 적합도
 *
 * @param matchedAxes 온보딩 응답과 맞은 축
 * @param appliedAxes 적합도 계산에 적용된 축. 맞출 기준이 없어 빠진 축은 만점에서도 빠진다
 */
public record MatchScore(Set<MatchAxis> matchedAxes, Set<MatchAxis> appliedAxes) {

  public MatchScore {
    matchedAxes = immutableCopy(matchedAxes);
    appliedAxes = immutableCopy(appliedAxes);
    requireEveryMatchedAxisApplied(matchedAxes, appliedAxes);
  }

  /** 배점 합 */
  public int score() {
    return MatchAxis.totalWeight(matchedAxes);
  }

  /** 만점 */
  public int maxScore() {
    return MatchAxis.totalWeight(appliedAxes);
  }

  /** 적합도(%) */
  public int matchRate() {
    int maxScore = maxScore();
    if (maxScore == 0) {
      return 0;
    }
    return (int) Math.round(score() * 100.0 / maxScore);
  }

  public boolean isMatched() {
    return !matchedAxes.isEmpty();
  }

  private static void requireEveryMatchedAxisApplied(Set<MatchAxis> matchedAxes,
      Set<MatchAxis> appliedAxes) {
    for (MatchAxis matchedAxis : matchedAxes) {
      if (!appliedAxes.contains(matchedAxis)) {
        throw new IllegalArgumentException(
            "%s 축은 적합도 계산에 적용되지 않아 맞은 축이 될 수 없습니다. 적용된 축=%s"
                .formatted(matchedAxis, appliedAxes));
      }
    }
  }

  private static Set<MatchAxis> immutableCopy(Set<MatchAxis> axes) {
    return axes.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(axes));
  }
}
