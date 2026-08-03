package chaeso.zip.server.recommendation.domain;

import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 채널 적합도를 계산하는 매칭 축과 배점
 */
@Getter
@RequiredArgsConstructor
public enum MatchAxis {

  /** 업종 */
  CATEGORY(40),

  /** 광고 목적 */
  OBJECTIVE(30),

  /** 주 연령대 */
  AGE_BAND(20);

  private final int weight;

  public static final int MAX_SCORE = Arrays.stream(values()).mapToInt(MatchAxis::getWeight).sum();
}
