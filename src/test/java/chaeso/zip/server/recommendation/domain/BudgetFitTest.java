package chaeso.zip.server.recommendation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BudgetFitTest {

  private static final long BUDGET_MIN = 1_000_000L;
  private static final long BUDGET_MAX = 3_000_000L;

  @Test
  @DisplayName("예산 하한으로도 집행할 수 있으면 만점이다")
  void scoresFullWithinBudgetFloor() {
    assertThat(fit(3_000)).isEqualTo(1.0);
    assertThat(fit(BUDGET_MIN)).isEqualTo(1.0);
  }

  @Test
  @DisplayName("예산 상한을 다 써야 집행되면 집행 가능한 채널 중 가장 낮은 점수를 준다")
  void scoresFloorWhenBudgetIsFullySpent() {
    assertThat(fit(BUDGET_MAX)).isEqualTo(0.6);
  }

  @Test
  @DisplayName("예산 범위 안에서는 남는 예산이 많을수록 높다")
  void interpolatesByHeadroom() {
    assertThat(fit(2_000_000L)).isEqualTo(0.8);
    assertThat(fit(1_500_000L)).isGreaterThan(fit(2_500_000L));
  }

  @Test
  @DisplayName("예산이 모자라면 부족한 정도에 따라 낮은 점수만 준다")
  void scoresLowWhenBudgetFallsShort() {
    assertThat(fit(9_000_000L)).isCloseTo(0.1, within(1e-9));   // 0.3 * 300만 / 900만
    assertThat(fit(BUDGET_MAX + 1)).isLessThan(0.3);
  }

  @Test
  @DisplayName("최소 집행 금액이 커질수록 점수가 단조 감소한다")
  void decreasesMonotonically() {
    double previous = Double.MAX_VALUE;
    for (long minBudgetWon : new long[] {1_000L, 1_000_000L, 2_000_000L, 3_000_000L, 5_000_000L,
        10_000_000L}) {
      double current = fit(minBudgetWon);
      assertThat(current).isLessThanOrEqualTo(previous);
      previous = current;
    }
  }

  @Test
  @DisplayName("예산이 0이면 어떤 매체도 집행할 수 없어 0 점이다")
  void scoresZeroWithoutBudget() {
    assertThat(BudgetFit.of(0L, 0L, 3_000L)).isZero();
  }

  @Test
  @DisplayName("예산 하한과 상한이 같아도 집행 가능하면 만점이다")
  void scoresFullOnSingleValueBudget() {
    assertThat(BudgetFit.of(BUDGET_MAX, BUDGET_MAX, BUDGET_MAX)).isEqualTo(1.0);
  }

  private static double fit(long minBudgetWon) {
    return BudgetFit.of(BUDGET_MIN, BUDGET_MAX, minBudgetWon);
  }
}
