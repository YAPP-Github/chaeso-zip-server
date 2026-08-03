package chaeso.zip.server.estimation.domain;

import chaeso.zip.server.channel.domain.vo.PricingModel;
import chaeso.zip.server.estimation.domain.vo.EstimationPricing;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 과금 모델이 다른 매체를 하나의 "클릭당 비용"으로 통일해 보여주기 위한 환산 규칙
 */
public final class ClickCostPolicy {

  private ClickCostPolicy() {
  }

  /**
   * 클릭당 비용(원)
   */
  public static BigDecimal cpcWon(EstimationPricing pricing, long budgetWon,
      Long estimatedClicks) {
    if (pricing.pricingModel() == PricingModel.CPC) {
      return pricing.value();
    }
    if (budgetWon <= 0 || estimatedClicks == null || estimatedClicks <= 0) {
      return null;
    }
    // 표시용 파생값이라 원 단위로 반올림한다
    return BigDecimal.valueOf(budgetWon)
        .divide(BigDecimal.valueOf(estimatedClicks), 0, RoundingMode.HALF_UP);
  }
}
