package chaeso.zip.server.estimation.domain.vo;

/**
 * 예상 클릭 수 범위.
 */
public record ClickRange(long min, long max) {

  /**
   * 범위의 중앙값. 클릭당 비용 환산과 정렬에서 범위를 대표하는 값으로 쓴다.
   */
  public long midpoint() {
    return Math.round((min + max) / 2.0);
  }
}
