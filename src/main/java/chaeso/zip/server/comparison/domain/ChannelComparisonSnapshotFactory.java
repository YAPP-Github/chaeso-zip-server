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
import chaeso.zip.server.recommendation.domain.BudgetFit;
import chaeso.zip.server.recommendation.domain.ChannelMatcher;
import chaeso.zip.server.recommendation.domain.MatchAxis;
import chaeso.zip.server.recommendation.domain.MatchScore;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 채널 카탈로그와 예산, 기간으로 비교 스냅샷을 만든다.
 */
public final class ChannelComparisonSnapshotFactory {

  /** 채널 태그로 보여줄 최대 개수. */
  private static final int MAX_TAGS = 2;

  /** 채널 장점으로 보여줄 최대 개수. 정렬 근거가 아직 없어 저장 순서 그대로 자른다. */
  private static final int MAX_ADVANTAGES = 3;

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
      return ChannelComparisonSnapshot.catalogOnly(channel, tags(channel), advantages(channel),
          null, null, pricingModelsAll);
    }
    return ChannelComparisonSnapshot.catalogOnly(channel, tags(channel), advantages(channel),
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
    return ChannelComparisonSnapshot.catalogOnly(channel, tags(channel), advantages(channel),
        estimated.cpcWon(), estimated.cpmWon(), pricingModelsAll, estimated.impressions(),
        estimated.clicks());
  }

  /**
   * 온보딩 예산과 캠페인 조건으로 단가, 적합도, 예상 노출, 클릭을 계산, 채널 태그를 반환한다.
   */
  public static ChannelComparisonSnapshot personalizedSnapshot(Onboarding onboarding,
      Channel channel, List<ChannelProduct> products,
      Map<UUID, List<ChannelPricing>> pricingsByProduct, long budgetWon, int periodDays,
      BigDecimal defaultCtrPercent) {
    List<String> pricingModelsAll = pricingModelsAll(products, pricingsByProduct);

    CatalogPrice catalogPrice = selectCatalogPrice(products, pricingsByProduct, defaultCtrPercent);
    EstimatedPrice estimated = estimate(catalogPrice, budgetWon, periodDays);
    MatchScore score = withBudgetFit(ChannelMatcher.match(onboarding, channel, products),
        onboarding, catalogPrice);

    return ChannelComparisonSnapshot.matched(channel, score, tags(channel), advantages(channel),
        estimated.cpcWon(), estimated.cpmWon(), pricingModelsAll, estimated.executable(),
        estimated.impressions(), estimated.clicks());
  }

  /**
   * 캠페인 조건 적합도에 예산 축을 붙인다. 추천 목록과 같은 산식을 써야 두 화면의 적합도가
   * 어긋나지 않는다. 대표 단가가 없어 집행 금액을 모르면 예산 축을 근거 없음으로 둔다.
   */
  private static MatchScore withBudgetFit(MatchScore score, Onboarding onboarding,
      CatalogPrice catalogPrice) {
    if (catalogPrice == null) {
      return score.withUnknown(MatchAxis.BUDGET);
    }
    long minBudgetWon = catalogPrice.representative().pricing().value()
        .setScale(0, RoundingMode.CEILING).longValue();
    return score.with(MatchAxis.BUDGET,
        BudgetFit.of(onboarding.getBudgetMin(), onboarding.getBudgetMax(), minBudgetWon));
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

  /** 채널 고유 태그. 최대 {@link #MAX_TAGS}개로 자른다. */
  private static List<String> tags(Channel channel) {
    List<String> defaultTags = channel.getDefaultTags();
    if (defaultTags == null || defaultTags.size() <= MAX_TAGS) {
      return defaultTags;
    }
    return List.copyOf(defaultTags.subList(0, MAX_TAGS));
  }

  /** 채널 장점. 최대 {@link #MAX_ADVANTAGES}개로 자른다. */
  private static List<String> advantages(Channel channel) {
    List<String> advantages = channel.getAdvantages();
    if (advantages == null || advantages.size() <= MAX_ADVANTAGES) {
      return advantages;
    }
    return List.copyOf(advantages.subList(0, MAX_ADVANTAGES));
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
