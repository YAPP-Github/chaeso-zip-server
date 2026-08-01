package chaeso.zip.server.estimation.domain;

import chaeso.zip.server.channel.domain.entity.ChannelPricing;
import chaeso.zip.server.channel.domain.entity.ChannelProduct;
import chaeso.zip.server.estimation.domain.vo.EstimationPricing;
import chaeso.zip.server.estimation.domain.vo.EstimationProduct;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 매체를 대표하는 상품과 그 상품의 대표 단가
 */
public record RepresentativeProduct(UUID productId, EstimationProduct product,
                                    EstimationPricing pricing, boolean estimatesImpressions) {

  /**
   * 대표 상품 선택 순서. 노출을 낼 수 있는 상품을 먼저 고른 뒤 단가가 싼 쪽을 쓴다.
   */
  private static final Comparator<RepresentativeProduct> BEST_FIRST = Comparator
      .comparing(RepresentativeProduct::estimatesImpressions, Comparator.reverseOrder())
      .thenComparing(candidate -> candidate.pricing().value())
      .thenComparing(RepresentativeProduct::productId);

  /**
   * 매체의 상품 중 대표 상품을 고른다.
   */
  public static Optional<RepresentativeProduct> select(List<ChannelProduct> products,
      Map<UUID, List<ChannelPricing>> pricingsByProduct, BigDecimal defaultCtrPercent) {
    return products.stream()
        .map(product -> of(product, pricingsByProduct.getOrDefault(product.getId(), List.of()),
            defaultCtrPercent))
        .flatMap(Optional::stream)
        .min(BEST_FIRST);
  }

  public static Optional<RepresentativeProduct> of(ChannelProduct product,
      List<ChannelPricing> pricings, BigDecimal defaultCtrPercent) {
    EstimationProduct estimationProduct =
        EstimationProduct.from(product, pricings, defaultCtrPercent);
    EstimationPricing pricing = EstimationService.representativePricing(estimationProduct);
    if (pricing == null) {
      return Optional.empty();
    }
    return Optional.of(new RepresentativeProduct(product.getId(), estimationProduct, pricing,
        EstimationService.estimatesImpressions(estimationProduct)));
  }
}
