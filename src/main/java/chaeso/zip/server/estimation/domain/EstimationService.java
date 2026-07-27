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

    EstimationPricing pricing = selectRepresentative(product.pricings());
    if (pricing == null) {
      return null;
    }

    BigDecimal budget = BigDecimal.valueOf(budgetWon);
    boolean executable = budget.compareTo(pricing.value()) >= 0;

    Bounds bounds = impressionBounds(product, pricing, budget, periodDays);
    if (bounds == null) {
      return EstimationResult.executabilityOnly(executable);
    }

    return new EstimationResult(executable, impressionRange(bounds),
        clickRange(bounds, product.ctr()));
  }

  /**
   * 대표 단가를 고른다. 값이 있는 단가 중 판매가를 우선하고, 없으면 첫 번째를 쓴다.
   *
   * @return 대표 단가. 후보가 없으면 {@code null}
   */
  private static EstimationPricing selectRepresentative(List<EstimationPricing> pricings) {
    if (pricings == null) {
      return null;
    }
    List<EstimationPricing> candidates = pricings.stream()
        .filter(pricing -> pricing != null && pricing.value() != null
            && pricing.value().signum() > 0)
        .toList();
    if (candidates.isEmpty()) {
      return null;
    }
    return candidates.stream()
        .filter(pricing -> pricing.priceType() == PriceType.SALE)
        .findFirst()
        .orElse(candidates.getFirst());
  }

  /**
   * 과금 모델별 노출 하한·상한을 계산한다.
   *
   * @return 노출 범위. 노출을 추정할 근거가 없으면 {@code null}
   */
  private static Bounds impressionBounds(EstimationProduct product, EstimationPricing pricing,
      BigDecimal budget, int periodDays) {
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

    if (SLOT_BASED_MODELS.contains(pricing.pricingModel())
        && product.expectedImpressions() != null) {
      BigDecimal slotDays = resolveSlotDays(pricing, product.expectedPeriod());
      BigDecimal slotsByPeriod = BigDecimal.valueOf(periodDays).divide(slotDays, MC);
      BigDecimal slotsByBudget = budget.divide(price, MC);
      BigDecimal actualSlots = slotsByPeriod.min(slotsByBudget);   // 기간·예산 중 빡센 쪽으로 제한
      return spread(BigDecimal.valueOf(product.expectedImpressions()).multiply(actualSlots));
    }

    return null;
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

  /** CTR 이 없는 상품은 기획 기준인 기본 2% 를 적용한다. */
  private static ClickRange clickRange(Bounds bounds, BigDecimal ctrPercent) {
    BigDecimal ctr = ctrPercent != null ? ctrPercent : DEFAULT_CTR_PERCENT;
    BigDecimal rate = ctr.divide(HUNDRED, MC);
    return new ClickRange(toCount(bounds.lo().multiply(rate)),
        toCount(bounds.hi().multiply(rate)));
  }

  /** 노출·클릭은 개수이므로 정수로 반올림해 노출한다. */
  private static long toCount(BigDecimal value) {
    return value.setScale(0, RoundingMode.HALF_UP).longValue();
  }

  /** 반올림 전 노출 하한·상한. */
  private record Bounds(BigDecimal lo, BigDecimal hi) {
  }
}
