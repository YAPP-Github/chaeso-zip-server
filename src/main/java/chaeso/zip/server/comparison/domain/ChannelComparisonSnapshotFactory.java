package chaeso.zip.server.comparison.domain;

import chaeso.zip.server.channel.domain.entity.Channel;
import chaeso.zip.server.channel.domain.entity.ChannelPricing;
import chaeso.zip.server.channel.domain.entity.ChannelProduct;
import chaeso.zip.server.channel.domain.vo.PricingModel;
import chaeso.zip.server.estimation.domain.ClickCostPolicy;
import chaeso.zip.server.estimation.domain.EstimationService;
import chaeso.zip.server.estimation.domain.RepresentativeProduct;
import chaeso.zip.server.estimation.domain.vo.ClickRange;
import chaeso.zip.server.estimation.domain.vo.EstimationPricing;
import chaeso.zip.server.estimation.domain.vo.EstimationResult;
import chaeso.zip.server.estimation.domain.vo.ImpressionRange;
import chaeso.zip.server.estimation.domain.vo.PeriodDaysPolicy;
import chaeso.zip.server.onboarding.domain.entity.Onboarding;
import chaeso.zip.server.recommendation.domain.ChannelMatcher;
import chaeso.zip.server.recommendation.domain.MatchAxis;
import chaeso.zip.server.recommendation.domain.MatchScore;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 채널 카탈로그와 예산, 기간으로 비교 스냅샷을 만든다.
 */
public final class ChannelComparisonSnapshotFactory {

  /** 맞춤 인사이트 태그로 보여줄 최대 개수. */
  private static final int INSIGHT_TAG_LIMIT = 2;

  /** 온보딩 없는 로그인 요청의 기본 추정 예산(원). */
  private static final long DEFAULT_ESTIMATION_BUDGET_WON = 1_000_000L;

  private ChannelComparisonSnapshotFactory() {
  }

  /** 온보딩 없을 때 쓰는 카탈로그 스냅샷. 예상 노출, 클릭은 채우지 않는다. */
  public static ChannelComparisonSnapshot staticSnapshot(Channel channel,
      List<ChannelProduct> products, Map<UUID, List<ChannelPricing>> pricingsByProduct,
      BigDecimal defaultCtrPercent) {
    List<String> pricingModelsAll = pricingModelsAll(products, pricingsByProduct);
    CatalogPrice catalogPrice = selectCatalogPrice(products, pricingsByProduct, defaultCtrPercent);
    if (catalogPrice == null) {
      return ChannelComparisonSnapshot.catalogOnly(channel, channel.getDefaultTags(), null, null,
          pricingModelsAll);
    }
    return ChannelComparisonSnapshot.catalogOnly(channel, channel.getDefaultTags(),
        catalogPrice.cpcWon(), catalogPrice.cpmWon(), pricingModelsAll);
  }

  /**
   * 온보딩 없는 로그인 요청에 쓰는 스냅샷. 예산, 기간은
   * {@link #DEFAULT_ESTIMATION_BUDGET_WON}, {@link PeriodDaysPolicy#M1_DAYS} 고정값을 쓴다.
   */
  public static ChannelComparisonSnapshot estimatedStaticSnapshot(Channel channel,
      List<ChannelProduct> products, Map<UUID, List<ChannelPricing>> pricingsByProduct,
      BigDecimal defaultCtrPercent) {
    List<String> pricingModelsAll = pricingModelsAll(products, pricingsByProduct);
    CatalogPrice catalogPrice = selectCatalogPrice(products, pricingsByProduct, defaultCtrPercent);
    EstimatedPrice estimated = estimate(catalogPrice, DEFAULT_ESTIMATION_BUDGET_WON,
        PeriodDaysPolicy.M1_DAYS);
    return ChannelComparisonSnapshot.catalogOnly(channel, channel.getDefaultTags(),
        estimated.cpcWon(), estimated.cpmWon(), pricingModelsAll, estimated.impressions(),
        estimated.clicks());
  }

  /**
   * 온보딩 예산과 캠페인 조건으로 맞춤 태그, 단가, 적합도, 예상 노출, 클릭을 계산한다.
   */
  public static ChannelComparisonSnapshot personalizedSnapshot(Onboarding onboarding,
      Channel channel, List<ChannelProduct> products,
      Map<UUID, List<ChannelPricing>> pricingsByProduct, long budgetWon, int periodDays,
      BigDecimal defaultCtrPercent) {
    MatchScore score = ChannelMatcher.match(onboarding, channel, products);
    List<String> tags = score.matchedAxes().stream()
        .limit(INSIGHT_TAG_LIMIT)
        .map(MatchAxis::name)
        .toList();
    List<String> pricingModelsAll = pricingModelsAll(products, pricingsByProduct);

    CatalogPrice catalogPrice = selectCatalogPrice(products, pricingsByProduct, defaultCtrPercent);
    EstimatedPrice estimated = estimate(catalogPrice, budgetWon, periodDays);

    return ChannelComparisonSnapshot.matched(channel, score, tags, estimated.cpcWon(),
        estimated.cpmWon(), pricingModelsAll, estimated.executable(), estimated.impressions(),
        estimated.clicks());
  }

  /**
   * 대표 단가와 예산으로 예상 노출, 클릭, 환산 클릭당 비용을 계산한다. 카탈로그 단가가 없거나 예산이
   * 0 이하이거나 최소 단가에도 못 미쳐 집행 불가능하면 노출, 클릭 없이 카탈로그 단가만 남긴다.
   */
  private static EstimatedPrice estimate(CatalogPrice catalogPrice, long budgetWon,
      int periodDays) {
    if (catalogPrice == null) {
      return new EstimatedPrice(null, null, null, null, false);
    }
    EstimatedPrice catalogOnly = new EstimatedPrice(catalogPrice.cpcWon(), catalogPrice.cpmWon(),
        null, null, false);
    if (budgetWon <= 0) {
      return catalogOnly;
    }
    EstimationResult result = EstimationService
        .estimate(catalogPrice.representative().product(), budgetWon, periodDays);
    if (result == null || !result.isExecutable()) {
      return catalogOnly;
    }
    ClickRange estimatedClicks = result.clicks();
    // 비교 화면에서는 과금 방식이 달라도 같은 기준으로 볼 수 있도록 클릭당 비용을 환산한다
    BigDecimal cpcWon = ClickCostPolicy.cpcWon(catalogPrice.representative().pricing(), budgetWon,
        midpoint(estimatedClicks));
    return new EstimatedPrice(cpcWon, catalogPrice.cpmWon(), result.impressions(), estimatedClicks,
        true);
  }

  /**
   * 매체의 대표 상품과 그 카탈로그 단가(CPC/CPM)를 고른다. 대표 상품을 정할 수 없으면 {@code null}.
   */
  private static CatalogPrice selectCatalogPrice(List<ChannelProduct> products,
      Map<UUID, List<ChannelPricing>> pricingsByProduct, BigDecimal defaultCtrPercent) {
    RepresentativeProduct representative = RepresentativeProduct
        .select(products, pricingsByProduct, defaultCtrPercent)
        .orElse(null);
    if (representative == null) {
      return null;
    }
    EstimationPricing pricing = representative.pricing();
    BigDecimal cpcWon = pricing.pricingModel() == PricingModel.CPC ? pricing.value() : null;
    BigDecimal cpmWon = pricing.pricingModel() == PricingModel.CPM ? pricing.value() : null;
    return new CatalogPrice(representative, cpcWon, cpmWon);
  }

  /**
   * 채널이 그 시점에 지원하던 전체 과금 방식. 저장 스냅샷에만 사용
   *
   * <p>대표 단가로 뽑히지 않은 상품의 과금 방식도 포함하며, enum 선언 순서로 정렬해 같은 채널이면
   * 항상 같은 배열을 만든다.
   */
  private static List<String> pricingModelsAll(List<ChannelProduct> products,
      Map<UUID, List<ChannelPricing>> pricingsByProduct) {
    return products.stream()
        .flatMap(product -> pricingsByProduct.getOrDefault(product.getId(), List.of()).stream())
        .map(ChannelPricing::getPricingModel)
        .distinct()
        .sorted()
        .map(PricingModel::name)
        .toList();
  }

  /**
   * 예상 클릭 범위의 중앙값을 클릭당 비용 환산 기준으로 사용.
   */
  private static Long midpoint(ClickRange clicks) {
    return clicks == null ? null : Math.round((clicks.min() + clicks.max()) / 2.0);
  }

  /** 대표 상품과 그 카탈로그 단가(CPC/CPM). */
  private record CatalogPrice(RepresentativeProduct representative, BigDecimal cpcWon,
                               BigDecimal cpmWon) {
  }

  /** 예산으로 계산한 예상 클릭당 비용, 노출, 클릭, 집행 가능 여부. */
  private record EstimatedPrice(BigDecimal cpcWon, BigDecimal cpmWon,
                                 ImpressionRange impressions, ClickRange clicks,
                                 boolean executable) {
  }
}
