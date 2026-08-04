package chaeso.zip.server.estimation.domain.vo;

import chaeso.zip.server.channel.domain.entity.ChannelPricing;
import chaeso.zip.server.channel.domain.entity.ChannelProduct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 시뮬레이션 계산에 필요한 상품 정보만 추린 입력 값.
 *
 * @param ctr                 클릭률(%, 예: {@code 2.5} 는 2.5%). 없으면 {@code null}
 * @param expectedImpressions 구좌 1개당 기대 노출 수. 없으면 {@code null}
 * @param expectedPeriod      기대 노출이 발생하는 기간 문자열 (예: {@code "2주"}). 없으면 {@code null}
 * @param pricings            상품의 단가 목록
 */
public record EstimationProduct(BigDecimal ctr, Long expectedImpressions, String expectedPeriod,
                                List<EstimationPricing> pricings) {

  private static final BigDecimal TWO = new BigDecimal("2");
  private static final int CTR_SCALE = 4;

  public static EstimationProduct from(ChannelProduct product, List<ChannelPricing> pricings) {
    return from(product, pricings, null);
  }

  public static EstimationProduct from(ChannelProduct product, List<ChannelPricing> pricings,
      BigDecimal defaultCtrPercent) {
    return new EstimationProduct(
        resolveCtr(product, defaultCtrPercent),
        product.getExpectedImpressions(),
        product.getExpectedPeriod(),
        pricings.stream().map(EstimationPricing::from).toList());
  }

  private static BigDecimal resolveCtr(ChannelProduct product, BigDecimal defaultCtrPercent) {
    if (product.getCtr() != null) {
      return product.getCtr();
    }
    BigDecimal min = product.getCtrMin();
    BigDecimal max = product.getCtrMax();
    if (min != null && max != null) {
      return min.add(max).divide(TWO, CTR_SCALE, RoundingMode.HALF_UP);
    }
    return defaultCtrPercent;
  }
}
