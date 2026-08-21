package chaeso.zip.server.recommendation.domain;

/**
 * 온보딩 예산 범위와 채널 최소 집행 금액의 적합 정도
 */
public final class BudgetFit {

  /** 예산 상한으로 겨우 집행할 수 있을 때의 적합 정도 */
  private static final double EXECUTABLE_FLOOR = 0.6;

  /** 예산이 모자랄 때 받을 수 있는 최대 적합 정도 */
  private static final double SHORTFALL_CEILING = 0.3;

  private static final double FULL = 1.0;

  private BudgetFit() {
  }

  /**
   * 예산 축의 적합 정도(0.0~1.0).
   *
   * <p>예산 하한으로도 집행할 수 있으면 1.0, 상한까지 써야 겨우 집행되면
   * {@value #EXECUTABLE_FLOOR}, 그 사이는 남는 예산 비율로 잇는다. 예산이 모자라면 부족한
   * 정도에 따라 0 ~ {@value #SHORTFALL_CEILING} 을 준다.
   *
   * @param budgetMin    온보딩 예산 하한(원)
   * @param budgetMax    온보딩 예산 상한(원)
   * @param minBudgetWon 채널 대표 단가 기준 최소 집행 금액(원)
   */
  public static double of(long budgetMin, long budgetMax, long minBudgetWon) {
    if (minBudgetWon <= budgetMin) {
      return FULL;
    }
    if (minBudgetWon > budgetMax) {
      return SHORTFALL_CEILING * ratio(budgetMax, minBudgetWon);
    }
    long span = budgetMax - budgetMin;
    if (span <= 0) {
      return FULL;
    }
    double headroom = (double) (budgetMax - minBudgetWon) / span;
    return EXECUTABLE_FLOOR + (FULL - EXECUTABLE_FLOOR) * headroom;
  }

  private static double ratio(long budgetMax, long minBudgetWon) {
    if (budgetMax <= 0) {
      return 0;
    }
    return (double) budgetMax / minBudgetWon;
  }
}
