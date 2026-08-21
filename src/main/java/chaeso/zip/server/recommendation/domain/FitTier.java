package chaeso.zip.server.recommendation.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 축이 얼마나 맞았는지의 단계
 */
@Getter
@RequiredArgsConstructor
public enum FitTier {

  /** 추천 근거로 그대로 내세울 만큼 맞은 축 */
  STRONG(0.8),

  /** 맞기는 하지만 단서를 달아야 하는 축 */
  PARTIAL(0.5),

  /** 사실상 맞지 않는 축 */
  WEAK(0.0);

  /** 이 단계에 들기 위한 최소 적합 정도 */
  private final double floor;

  public static FitTier of(double fit) {
    if (fit >= STRONG.floor) {
      return STRONG;
    }
    return fit >= PARTIAL.floor ? PARTIAL : WEAK;
  }
}
