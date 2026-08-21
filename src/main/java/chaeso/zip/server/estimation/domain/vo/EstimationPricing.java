package chaeso.zip.server.estimation.domain.vo;

import chaeso.zip.server.channel.domain.entity.ChannelPricing;
import chaeso.zip.server.channel.domain.vo.PriceType;
import chaeso.zip.server.channel.domain.vo.PricingModel;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 시뮬레이션 계산에 필요한 단가 정보만 추린 입력 값.
 *
 * @param pricingModel 과금 모델
 * @param priceType    가격 유형 (SALE 단가를 대표 단가로 우선 선택)
 * @param value        단가 값
 * @param valueMax     구간형 단가의 상한값 (없으면 {@code null})
 * @param unitDays     단가가 적용되는 단위 일수 (일=1, 주=7, 월=30)
 */
public record EstimationPricing(PricingModel pricingModel, PriceType priceType, BigDecimal value,
                                BigDecimal valueMax, BigDecimal unitDays) {

  public static EstimationPricing from(ChannelPricing pricing) {
    return new EstimationPricing(
        pricing.getPricingModel(),
        pricing.getPriceType(),
        pricing.getValue(),
        pricing.getValueMax(),
        pricing.getUnitDays());
  }

  /**
   * 이 단가로 한 번 집행하는 데 필요한 최소 금액(원). 원 단위 아래는 올림한다.
   */
  public long minBudgetWon() {
    return value.setScale(0, RoundingMode.CEILING).longValue();
  }
}
