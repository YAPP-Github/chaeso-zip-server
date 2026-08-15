package chaeso.zip.server.estimation.domain;

import chaeso.zip.server.channel.domain.vo.PriceType;
import chaeso.zip.server.channel.domain.vo.PricingModel;
import chaeso.zip.server.estimation.domain.vo.ClickRange;
import chaeso.zip.server.estimation.domain.vo.EstimationPricing;
import chaeso.zip.server.estimation.domain.vo.EstimationProduct;
import chaeso.zip.server.estimation.domain.vo.EstimationResult;
import chaeso.zip.server.estimation.domain.vo.ImpressionRange;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 예산·기간을 받아 상품의 집행 가능 여부와 예상 노출·클릭 범위를 계산하는 도메인 서비스.
 */
public final class EstimationService {

  /** 노출 추정 범위의 폭(±15%). */
  public static final BigDecimal RANGE_PCT = new BigDecimal("0.15");

  /** CTR 이 제공되지 않은 상품에 적용하는 기본 클릭률(%). */
  public static final BigDecimal DEFAULT_CTR_PERCENT = new BigDecimal("2");

  private static final BigDecimal LOWER_FACTOR = BigDecimal.ONE.subtract(RANGE_PCT);
  private static final BigDecimal UPPER_FACTOR = BigDecimal.ONE.add(RANGE_PCT);

  private static final BigDecimal HUNDRED = new BigDecimal("100");
  private static final BigDecimal CPM_UNIT = new BigDecimal("1000");

  /** 구좌 단위 일수를 알 수 없을 때 사용하는 기본값. */
  private static final BigDecimal DEFAULT_SLOT_DAYS = BigDecimal.ONE;
  private static final BigDecimal DAYS_PER_WEEK = new BigDecimal("7");
  private static final BigDecimal DAYS_PER_MONTH = new BigDecimal("30");

  /** 노출을 구좌 수 기준으로 계산하는 과금 모델. */
  private static final Set<PricingModel> SLOT_BASED_MODELS =
      Set.of(PricingModel.SLOT, PricingModel.FLAT, PricingModel.PACKAGE);

  private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);
  private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

  /**
   * 대표 단가 선택 순서. 값이 같을 때를 대비해 추정 결과에 영향을 주는 나머지 필드까지 비교하므로,
   * 이 순서로 동등한 두 단가는 어느 쪽을 골라도 같은 추정치를 낸다.
   */
  private static final Comparator<EstimationPricing> CHEAPEST_FIRST = Comparator
      .comparing(EstimationPricing::value)
      .thenComparing(EstimationPricing::pricingModel)
      .thenComparing(EstimationPricing::valueMax, Comparator.nullsFirst(Comparator.naturalOrder()))
      .thenComparing(EstimationPricing::unitDays, Comparator.nullsFirst(Comparator.naturalOrder()));

  private EstimationService() {
  }

  /**
   * 상품에 예산·기간을 적용한 시뮬레이션 결과를 계산한다.
   *
   * @param product    상품 정보(단가 목록 포함)
   * @param budgetWon  예산(원)
   * @param periodDays 집행 기간(일)
   * @return 시뮬레이션 결과. 값이 있는 단가가 하나도 없어 대표 단가를 정할 수 없으면 {@code null}
   * @throws IllegalArgumentException 예산이나 기간이 양수가 아닌 경우
   */
  public static EstimationResult estimate(EstimationProduct product, long budgetWon,
      int periodDays) {
    Objects.requireNonNull(product, "product 는 null 일 수 없습니다");
    if (budgetWon <= 0) {
      throw new IllegalArgumentException("Budget must be positive.");
    }
    if (periodDays <= 0) {
      throw new IllegalArgumentException("Period days must be positive.");
    }

    EstimationPricing pricing = representativePricing(product);
    if (pricing == null) {
      return null;
    }

    BigDecimal budget = BigDecimal.valueOf(budgetWon);
    boolean executable = isExecutable(pricing, budget);

    Bounds bounds = impressionBounds(product, pricing, budget, periodDays);
    if (bounds == null) {
      return EstimationResult.executabilityOnly(executable);
    }

    return new EstimationResult(executable, impressionRange(bounds),
        clickRange(bounds, product.ctr()));
  }

  /**
   * 예산만으로 상품의 집행 가능 여부를 판정한다.
   *
   * @param product   상품 정보(단가 목록 포함)
   * @param budgetWon 예산(원)
   * @return 집행 가능 여부. 값이 있는 단가가 하나도 없어 기준 단가를 정할 수 없으면 {@code null}
   */
  public static Boolean isExecutable(EstimationProduct product, long budgetWon) {
    EstimationPricing pricing = representativePricing(product);
    if (pricing == null) {
      return null;
    }
    return isExecutable(pricing, BigDecimal.valueOf(budgetWon));
  }

  private static boolean isExecutable(EstimationPricing pricing, BigDecimal budget) {
    return budget.compareTo(pricing.value()) >= 0;
  }

  /**
   * 노출·클릭 추정에 실제로 사용되는 대표 단가를 고른다. 값이 있는 단가 중 판매가를 우선하고,
   * 후보가 여럿이면 가장 싼 것을 쓴다. 호출자가 집행 가능 판정의 기준 금액이나
   * 과금 모델(CPC/CPM 표시)을 알아야 할 때도 이 메서드를 쓴다.
   *
   * <p>결과는 {@code pricings} 의 순서에 의존하지 않는다. 단가는 정렬 없이 조회되는 경우가 많아
   * 순서에 기대면 같은 입력이 실행마다 다른 추정치를 낼 수 있다.
   *
   * @return 대표 단가. 값이 있는 단가가 없으면 {@code null}
   */
  public static EstimationPricing representativePricing(EstimationProduct product) {
    Objects.requireNonNull(product, "product 는 null 일 수 없습니다");
    List<EstimationPricing> pricings = product.pricings();
    if (pricings == null) {
      return null;
    }
    // 0 이하 단가는 나눗셈의 분모가 되므로 후보에서 제외한다
    List<EstimationPricing> candidates = pricings.stream()
        .filter(pricing -> pricing != null && pricing.value() != null
            && pricing.value().signum() > 0)
        .toList();
    if (candidates.isEmpty()) {
      return null;
    }
    return candidates.stream()
        .filter(pricing -> pricing.priceType() == PriceType.SALE)
        .min(CHEAPEST_FIRST)
        .or(() -> candidates.stream().min(CHEAPEST_FIRST))
        .orElseThrow();
  }

  /**
   * 과금 모델별 노출 하한·상한을 계산한다.
   *
   * @return 노출 범위. 노출을 추정할 근거가 없으면 {@code null}
   */
  private static Bounds impressionBounds(EstimationProduct product, EstimationPricing pricing,
      BigDecimal budget, int periodDays) {
    if (!hasImpressionBasis(product, pricing)) {
      return null;
    }

    BigDecimal price = pricing.value();

    if (pricing.pricingModel() == PricingModel.CPM) {
      BigDecimal mid = budget.divide(price, MC).multiply(CPM_UNIT);
      BigDecimal priceMax = pricing.valueMax();
      if (priceMax != null && priceMax.compareTo(price) >= 0) {
        // 구간형 단가는 상한 단가가 노출 하한, 하한 단가가 노출 상한이 된다
        return new Bounds(budget.divide(priceMax, MC).multiply(CPM_UNIT), mid);
      }
      return spread(mid);
    }

    BigDecimal slotDays = resolveSlotDays(pricing, product.expectedPeriod());
    BigDecimal slotsByPeriod = BigDecimal.valueOf(periodDays).divide(slotDays, MC);
    BigDecimal slotsByBudget = budget.divide(price, MC);
    BigDecimal actualSlots = slotsByPeriod.min(slotsByBudget);   // 기간·예산 중 빡센 쪽으로 제한
    return spread(BigDecimal.valueOf(product.expectedImpressions()).multiply(actualSlots));
  }

  /**
   * 이 상품으로 노출·클릭까지 추정할 수 있는지. 대표 단가의 과금 모델과 상품의 기대 노출 정보로
   * 정해진다. 호출자가 여러 상품 중 실제로 추정치를 낼 수 있는 쪽을 고를 때 쓴다.
   */
  public static boolean estimatesImpressions(EstimationProduct product) {
    EstimationPricing pricing = representativePricing(product);
    return pricing != null && hasImpressionBasis(product, pricing);
  }

  private static boolean hasImpressionBasis(EstimationProduct product, EstimationPricing pricing) {
    if (pricing.pricingModel() == PricingModel.CPM) {
      return true;   // 예산과 단가만으로 노출을 환산할 수 있다
    }
    return SLOT_BASED_MODELS.contains(pricing.pricingModel())
        && product.expectedImpressions() != null;
  }

  /** 구좌 1개가 차지하는 일수를 구한다. */
  private static BigDecimal resolveSlotDays(EstimationPricing pricing, String expectedPeriod) {
    BigDecimal unitDays = pricing.unitDays();
    if (unitDays != null && unitDays.signum() > 0) {
      return unitDays;
    }
    return parsePeriodDays(expectedPeriod);
  }

  /** 기간 문자열을 일수로 바꾼다. */
  private static BigDecimal parsePeriodDays(String expectedPeriod) {
    if (expectedPeriod == null || expectedPeriod.isBlank()) {
      return DEFAULT_SLOT_DAYS;
    }

    Matcher matcher = NUMBER.matcher(expectedPeriod);
    BigDecimal amount = matcher.find() ? new BigDecimal(matcher.group()) : BigDecimal.ONE;
    if (amount.signum() <= 0) {
      amount = BigDecimal.ONE;
    }

    if (expectedPeriod.contains("주")) {
      return amount.multiply(DAYS_PER_WEEK);
    }
    if (expectedPeriod.contains("월")) {
      return amount.multiply(DAYS_PER_MONTH);
    }
    if (expectedPeriod.contains("일")) {
      return amount;
    }
    return DEFAULT_SLOT_DAYS;
  }

  /** 중앙값에 ±{@link #RANGE_PCT} 를 적용한 범위. */
  private static Bounds spread(BigDecimal mid) {
    return new Bounds(mid.multiply(LOWER_FACTOR), mid.multiply(UPPER_FACTOR));
  }

  private static ImpressionRange impressionRange(Bounds bounds) {
    return new ImpressionRange(toCount(bounds.lo()), toCount(bounds.hi()));
  }

  /**
   * 노출 수에 CTR 을 적용한 예상 클릭 수
   */
  public static Long estimateClicks(Long impressions, BigDecimal ctrPercent) {
    if (impressions == null || ctrPercent == null) {
      return null;
    }
    return toCount(BigDecimal.valueOf(impressions).multiply(clickRate(ctrPercent)));
  }

  private static ClickRange clickRange(Bounds bounds, BigDecimal ctrPercent) {
    BigDecimal rate = clickRate(ctrPercent);
    return new ClickRange(toCount(bounds.lo().multiply(rate)),
        toCount(bounds.hi().multiply(rate)));
  }

  /** CTR 이 없는 상품은 기본 2% 를 적용한다. */
  private static BigDecimal clickRate(BigDecimal ctrPercent) {
    BigDecimal ctr = ctrPercent != null ? ctrPercent : DEFAULT_CTR_PERCENT;
    return ctr.divide(HUNDRED, MC);
  }

  /** 노출·클릭은 개수이므로 정수로 반올림해 노출한다. */
  private static long toCount(BigDecimal value) {
    return value.setScale(0, RoundingMode.HALF_UP).longValue();
  }

  /** 반올림 전 노출 하한·상한. */
  private record Bounds(BigDecimal lo, BigDecimal hi) {
  }
}
