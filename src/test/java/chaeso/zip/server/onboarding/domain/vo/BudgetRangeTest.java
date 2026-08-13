package chaeso.zip.server.onboarding.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import chaeso.zip.server.onboarding.domain.OnboardingBusinessException;
import chaeso.zip.server.onboarding.domain.OnboardingErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BudgetRangeTest {

  @Test
  @DisplayName("유효한 최소 예산과 최대 예산으로 BudgetRange 객체를 생성한다")
  void createsBudgetRangeSuccessfully() {
    BudgetRange range = BudgetRange.of(1_000_000L, 5_000_000L);

    assertThat(range.getBudgetMin()).isEqualTo(1_000_000L);
    assertThat(range.getBudgetMax()).isEqualTo(5_000_000L);
  }

  @Test
  @DisplayName("budgetMin이 null이면 예외가 발생한다")
  void throwsExceptionWhenBudgetMinIsNull() {
    assertThatThrownBy(() -> BudgetRange.of(null, 5_000_000L))
        .isInstanceOf(OnboardingBusinessException.class)
        .extracting("errorCode")
        .isEqualTo(OnboardingErrorCode.INVALID_BUDGET_RANGE);
  }

  @Test
  @DisplayName("budgetMax가 null이면 예외가 발생한다")
  void throwsExceptionWhenBudgetMaxIsNull() {
    assertThatThrownBy(() -> BudgetRange.of(1_000_000L, null))
        .isInstanceOf(OnboardingBusinessException.class)
        .extracting("errorCode")
        .isEqualTo(OnboardingErrorCode.INVALID_BUDGET_RANGE);
  }

  @Test
  @DisplayName("budgetMin이 budgetMax보다 크면 예외가 발생한다")
  void throwsExceptionWhenBudgetMinIsGreaterThanBudgetMax() {
    assertThatThrownBy(() -> BudgetRange.of(10_000_000L, 1_000_000L))
        .isInstanceOf(OnboardingBusinessException.class)
        .extracting("errorCode")
        .isEqualTo(OnboardingErrorCode.INVALID_BUDGET_RANGE);
  }
}
