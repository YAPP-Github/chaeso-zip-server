package chaeso.zip.server.recommendation.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 채널 하나의 적합도
 */
public record MatchScore(Set<MatchAxis> matchedAxes) {

  public MatchScore(Set<MatchAxis> matchedAxes) {
    this.matchedAxes = matchedAxes.isEmpty()
        ? Set.of()
        : Collections.unmodifiableSet(EnumSet.copyOf(matchedAxes));
  }

  /** 배점 합 */
  public int score() {
    return matchedAxes.stream().mapToInt(MatchAxis::getWeight).sum();
  }

  /** 적합도(%) */
  public int matchRate() {
    return (int) Math.round(score() * 100.0 / MatchAxis.MAX_SCORE);
  }

  /** 추천 후보인지 */
  public boolean isMatched() {
    return !matchedAxes.isEmpty();
  }
}
