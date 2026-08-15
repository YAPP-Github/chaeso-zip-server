package chaeso.zip.server.onboarding.domain.vo;

import chaeso.zip.server.onboarding.domain.OnboardingBusinessException;
import chaeso.zip.server.onboarding.domain.OnboardingErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 온보딩 집행 예산 범위 값 객체
 */
@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BudgetRange {

  private static final long MIN_BUDGET = 0L;
  private static final long MAX_BUDGET = 10_000_000L;

  @Column(name = "budget_min", nullable = false)
  private Long budgetMin;

  @Column(name = "budget_max", nullable = false)
  private Long budgetMax;

  public BudgetRange(Long budgetMin, Long budgetMax) {
    if (budgetMin == null || budgetMax == null
        || budgetMin < MIN_BUDGET || budgetMax < MIN_BUDGET
        || budgetMin > MAX_BUDGET || budgetMax > MAX_BUDGET
        || budgetMin > budgetMax) {
      throw new OnboardingBusinessException(OnboardingErrorCode.INVALID_BUDGET_RANGE);
    }
    this.budgetMin = budgetMin;
    this.budgetMax = budgetMax;
  }

  public static BudgetRange of(Long budgetMin, Long budgetMax) {
    return new BudgetRange(budgetMin, budgetMax);
  }
}
