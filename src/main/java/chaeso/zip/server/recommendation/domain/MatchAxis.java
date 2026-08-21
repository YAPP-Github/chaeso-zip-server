package chaeso.zip.server.recommendation.domain;

import java.util.Collection;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 채널 적합도를 계산하는 매칭 축과 배점.
 *
 * <p>각 축은 맞았다/아니다가 아니라 0.0~1.0 의 적합 정도로 채점된다. 어느 정도 맞아야 추천 근거
 * 문장에 쓸 수 있는지는 {@link FitTier} 이 정한다.
 */
@Getter
@RequiredArgsConstructor
public enum MatchAxis {

  /** 업종 */
  CATEGORY(30, true),

  /** 광고 목적 */
  OBJECTIVE(25, true),

  /** 주 연령대 */
  AGE_BAND(20, true),

  /** 예산 */
  BUDGET(25, false);

  /** 배점 */
  private final int weight;

  /** 추천 근거 문장의 주어가 되는 축인지 */
  private final boolean subject;

  /** 모든 축의 배점 합. 근거가 없어 빠진 축의 비중을 재는 기준이 된다 */
  public static int declaredWeight() {
    int total = 0;
    for (MatchAxis axis : values()) {
      total += axis.weight;
    }
    return total;
  }

  public static int totalWeight(Collection<MatchAxis> axes) {
    int total = 0;
    for (MatchAxis axis : axes) {
      total += axis.weight;
    }
    return total;
  }
}
