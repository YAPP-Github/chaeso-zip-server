package chaeso.zip.server.estimation.domain.vo;

/**
 * 특정 상품에 대한 예산·기간 시뮬레이션 결과.
 *
 * <p>노출 정보가 없는 상품은 집행 가능 여부만 판정할 수 있으므로
 * {@code impressions}/{@code clicks} 가 모두 {@code null} 이다.
 */
public record EstimationResult(boolean isExecutable, ImpressionRange impressions,
                               ClickRange clicks) {

  /** 집행 가능 여부만 판정된 결과. */
  public static EstimationResult executabilityOnly(boolean isExecutable) {
    return new EstimationResult(isExecutable, null, null);
  }
}
