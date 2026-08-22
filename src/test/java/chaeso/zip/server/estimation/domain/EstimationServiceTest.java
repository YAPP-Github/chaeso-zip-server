package chaeso.zip.server.estimation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import chaeso.zip.server.channel.domain.vo.PriceType;
import chaeso.zip.server.channel.domain.vo.PricingModel;
import chaeso.zip.server.estimation.domain.vo.ClickRange;
import chaeso.zip.server.estimation.domain.vo.EstimationPricing;
import chaeso.zip.server.estimation.domain.vo.EstimationProduct;
import chaeso.zip.server.estimation.domain.vo.EstimationResult;
import chaeso.zip.server.estimation.domain.vo.ImpressionRange;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class EstimationServiceTest {

  @Nested
  @DisplayName("입력 검증")
  class InputValidation {

    @ParameterizedTest
    @ValueSource(longs = {-1_000_000L, -1L, 0L})
    @DisplayName("예산이 양수가 아니면 거부한다")
    void rejectsNonPositiveBudget(long budgetWon) {
      EstimationProduct product = product(null, null, null,
          pricing(PricingModel.CPM, PriceType.LIST, "5000", null, null));

      assertThatThrownBy(() -> EstimationService.estimate(product, budgetWon, 30))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Budget must be positive.");
    }

    @ParameterizedTest
    @ValueSource(ints = {-30, -1, 0})
    @DisplayName("집행 기간이 양수가 아니면 거부한다")
    void rejectsNonPositivePeriod(int periodDays) {
      EstimationProduct product = product(null, 100_000L, null,
          pricing(PricingModel.SLOT, PriceType.LIST, "500000", null, "7"));

      assertThatThrownBy(() -> EstimationService.estimate(product, 1_000_000L, periodDays))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Period days must be positive.");
    }
  }

  @Nested
  @DisplayName("CPM 노출 계산")
  class Cpm {

    @Test
    @DisplayName("단가 상한이 없으면 예산/단가 기준 중앙값에 ±15% 를 적용한다")
    void spreadsAroundMidWhenNoPriceMax() {
      // 1,000,000 / 5,000 * 1000 = 200,000 노출
      EstimationProduct product = product(null, null, null,
          pricing(PricingModel.CPM, PriceType.LIST, "5000", null, null));

      EstimationResult result = EstimationService.estimate(product, 1_000_000L, 30);

      assertThat(result.isExecutable()).isTrue();
      assertThat(result.impressions()).isEqualTo(new ImpressionRange(170_000, 230_000));
    }

    @Test
    @DisplayName("단가 상한이 있으면 상한 단가가 노출 하한, 하한 단가가 노출 상한이 된다")
    void usesPriceRangeWhenPriceMaxExists() {
      // 하한: 1,000,000 / 8,000 * 1000 = 125,000 / 상한: 1,000,000 / 5,000 * 1000 = 200,000
      EstimationProduct product = product(new BigDecimal("1.5"), null, null,
          pricing(PricingModel.CPM, PriceType.LIST, "5000", "8000", null));

      EstimationResult result = EstimationService.estimate(product, 1_000_000L, 30);

      assertThat(result.impressions()).isEqualTo(new ImpressionRange(125_000, 200_000));
      assertThat(result.clicks()).isEqualTo(new ClickRange(1_875, 3_000));   // 1.5%
    }

    @Test
    @DisplayName("단가 상한이 하한과 같으면 노출 하한·상한도 같다")
    void collapsesRangeWhenPriceMaxEqualsPrice() {
      EstimationProduct product = product(null, null, null,
          pricing(PricingModel.CPM, PriceType.LIST, "5000", "5000", null));

      EstimationResult result = EstimationService.estimate(product, 1_000_000L, 30);

      assertThat(result.impressions()).isEqualTo(new ImpressionRange(200_000, 200_000));
    }

    @Test
    @DisplayName("단가 상한이 하한보다 작은 이상 데이터면 ±15% 로 폴백한다")
    void fallsBackToSpreadWhenPriceMaxBelowPrice() {
      // 그대로 계산하면 하한이 1,000,000/3,000*1000 = 333,333 으로 상한 200,000 을 넘어 역전된다
      EstimationProduct product = product(null, null, null,
          pricing(PricingModel.CPM, PriceType.LIST, "5000", "3000", null));

      EstimationResult result = EstimationService.estimate(product, 1_000_000L, 30);

      assertThat(result.impressions()).isEqualTo(new ImpressionRange(170_000, 230_000));
    }
  }

  @Nested
  @DisplayName("단위형(SLOT/FLAT/PACKAGE/CPP) 노출 계산")
  class SlotBased {

    @ParameterizedTest
    @EnumSource(value = PricingModel.class, names = {"SLOT", "FLAT", "PACKAGE", "CPP"})
    @DisplayName("기간이 예산보다 빡세면 기간 기준으로 구좌 수가 제한된다")
    void limitsSlotsByPeriod(PricingModel pricingModel) {
      // 기간 14일 / 구좌 7일 = 2구좌, 예산 5,000,000 / 500,000 = 10구좌 → 2구좌 채택
      EstimationProduct product = product(BigDecimal.ONE, 100_000L, null,
          pricing(pricingModel, PriceType.LIST, "500000", null, "7"));

      EstimationResult result = EstimationService.estimate(product, 5_000_000L, 14);

      assertThat(result.impressions()).isEqualTo(new ImpressionRange(170_000, 230_000));
      assertThat(result.clicks()).isEqualTo(new ClickRange(1_700, 2_300));   // 1%
    }

    @Test
    @DisplayName("예산이 기간보다 빡세면 예산 기준으로 구좌 수가 제한된다")
    void limitsSlotsByBudget() {
      // 기간 70일 / 구좌 7일 = 10구좌, 예산 1,500,000 / 500,000 = 3구좌 → 3구좌 채택
      EstimationProduct product = product(BigDecimal.ONE, 100_000L, null,
          pricing(PricingModel.SLOT, PriceType.LIST, "500000", null, "7"));

      EstimationResult result = EstimationService.estimate(product, 1_500_000L, 70);

      assertThat(result.impressions()).isEqualTo(new ImpressionRange(255_000, 345_000));
    }

    @Test
    @DisplayName("일 단위 기간형(CPP) 상품도 기대 노출로 추정한다")
    void estimatesDailyPeriodPricing() {
      // 기간 7일 / 단위 1일 = 7단위, 예산 720,000 / 140,000 = 5.142...단위 → 예산 기준 채택
      EstimationProduct product = product(null, 86_000L, "1일",
          pricing(PricingModel.CPP, PriceType.DISCOUNT, "140000", null, "1"));

      EstimationResult result = EstimationService.estimate(product, 720_000L, 7);

      assertThat(result.isExecutable()).isTrue();
      assertThat(result.impressions()).isEqualTo(new ImpressionRange(375_943, 508_629));
      assertThat(result.clicks()).isEqualTo(new ClickRange(7_519, 10_173));   // 기본 2%
    }

    @Test
    @DisplayName("구좌 수가 소수로 나뉘어도 예외 없이 계산한다")
    void handlesFractionalSlots() {
      // 기간 7일 / 구좌 30일 = 0.2333...구좌 → 300,000 * 0.2333... ≒ 70,000 노출
      EstimationProduct product = product(null, 300_000L, "1개월",
          pricing(PricingModel.SLOT, PriceType.LIST, "1000", null, null));

      EstimationResult result = EstimationService.estimate(product, 10_000_000L, 7);

      assertThat(result.impressions()).isEqualTo(new ImpressionRange(59_500, 80_500));
      assertThat(result.clicks()).isEqualTo(new ClickRange(1_190, 1_610));   // 기본 2%
    }
  }

  @Nested
  @DisplayName("구좌 기간(slotDays) 결정")
  class SlotDays {

    @Test
    @DisplayName("단가의 단위 일수가 있으면 상품의 기대 기간 문자열보다 우선한다")
    void unitDaysWinsOverExpectedPeriod() {
      // unitDays=30 → 60/30 = 2구좌 (expectedPeriod "2주"=14일을 썼다면 4.28구좌가 된다)
      EstimationProduct product = product(null, 10_000L, "2주",
          pricing(PricingModel.SLOT, PriceType.LIST, "100000", null, "30"));

      EstimationResult result = EstimationService.estimate(product, 100_000_000L, 60);

      assertThat(result.impressions()).isEqualTo(new ImpressionRange(17_000, 23_000));
    }

    @Test
    @DisplayName("단가의 단위 일수가 없으면 상품의 기대 기간 문자열을 파싱해 사용한다")
    void fallsBackToExpectedPeriod() {
      // "2주" → 14일 → 42/14 = 3구좌
      EstimationProduct product = product(null, 10_000L, "2주",
          pricing(PricingModel.SLOT, PriceType.LIST, "100000", null, null));

      EstimationResult result = EstimationService.estimate(product, 100_000_000L, 42);

      assertThat(result.impressions()).isEqualTo(new ImpressionRange(25_500, 34_500));
    }

    @ParameterizedTest
    @CsvSource({
        "2주,     25500",     // 14일 → 30구좌 → 30,000 노출
        "주,      51000",     // 숫자 없음 → 1주 = 7일 → 60구좌 → 60,000 노출
        "1개월,   11900",     // 30일 → 14구좌 → 14,000 노출
        "개월,    11900",     // 숫자 없음 → 1개월 = 30일
        "2월,     5950",      // 60일 → 7구좌 → 7,000 노출
        "3일,     119000",    // 3일 → 140구좌 → 140,000 노출
        "상시,    357000",    // 단위 없음 → 1일 → 420구좌 → 420,000 노출
    })
    @DisplayName("기대 기간 문자열의 단위별 파싱 규칙을 지킨다")
    void parsesExpectedPeriodUnits(String expectedPeriod, long expectedMinImpressions) {
      EstimationProduct product = product(null, 1_000L, expectedPeriod,
          pricing(PricingModel.SLOT, PriceType.LIST, "1000", null, null));

      EstimationResult result = EstimationService.estimate(product, 10_000_000L, 420);

      assertThat(result.impressions().min()).isEqualTo(expectedMinImpressions);
    }

    @Test
    @DisplayName("단위 일수와 기대 기간이 모두 없으면 구좌 기간은 1일이다")
    void defaultsToOneDay() {
      // 420/1 = 420구좌 → 420,000 노출
      EstimationProduct product = product(null, 1_000L, null,
          pricing(PricingModel.SLOT, PriceType.LIST, "1000", null, null));

      EstimationResult result = EstimationService.estimate(product, 10_000_000L, 420);

      assertThat(result.impressions().min()).isEqualTo(357_000);
    }
  }

  @Nested
  @DisplayName("집행 가능 여부")
  class Executability {

    @Test
    @DisplayName("예산이 단가와 같으면 기간이 짧아도 집행 가능하다")
    void executableWhenBudgetEqualsPrice() {
      EstimationProduct product = product(null, null, null,
          pricing(PricingModel.CPM, PriceType.LIST, "1000000", null, null));

      EstimationResult result = EstimationService.estimate(product, 1_000_000L, 1);

      assertThat(result.isExecutable()).isTrue();
    }

    @Test
    @DisplayName("예산이 단가보다 적으면 기간이 길어도 집행 불가다")
    void notExecutableWhenBudgetBelowPrice() {
      EstimationProduct product = product(null, null, null,
          pricing(PricingModel.CPM, PriceType.LIST, "1000000", null, null));

      EstimationResult result = EstimationService.estimate(product, 999_999L, 365);

      assertThat(result.isExecutable()).isFalse();
      assertThat(result.impressions()).isNotNull();   // 집행 불가여도 노출 추정은 계산된다
    }
  }

  @Nested
  @DisplayName("노출 정보가 없는 상품")
  class WithoutImpressionData {

    @Test
    @DisplayName("구좌형이지만 기대 노출이 없으면 집행 가능 여부만 반환한다")
    void slotModelWithoutExpectedImpressions() {
      EstimationProduct product = product(new BigDecimal("3"), null, "1개월",
          pricing(PricingModel.SLOT, PriceType.LIST, "500000", null, "30"));

      EstimationResult result = EstimationService.estimate(product, 1_000_000L, 30);

      assertThat(result.isExecutable()).isTrue();
      assertThat(result.impressions()).isNull();
      assertThat(result.clicks()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = PricingModel.class, names = {"CPC", "CPA", "CPV", "PER_UNIT", "OTHER"})
    @DisplayName("노출 계산 규칙이 없는 과금 모델은 집행 가능 여부만 반환한다")
    void unsupportedPricingModels(PricingModel pricingModel) {
      EstimationProduct product = product(new BigDecimal("3"), 100_000L, "1개월",
          pricing(pricingModel, PriceType.LIST, "500000", null, "30"));

      EstimationResult result = EstimationService.estimate(product, 1_000_000L, 30);

      assertThat(result.isExecutable()).isTrue();
      assertThat(result.impressions()).isNull();
      assertThat(result.clicks()).isNull();
    }
  }

  @Nested
  @DisplayName("클릭 계산")
  class Clicks {

    @Test
    @DisplayName("CTR 이 있으면 해당 CTR 로 클릭을 계산한다")
    void usesProvidedCtr() {
      // 노출 850,000 ~ 1,150,000 에 5% 적용
      EstimationProduct product = product(new BigDecimal("5"), null, null,
          pricing(PricingModel.CPM, PriceType.LIST, "1000", null, null));

      EstimationResult result = EstimationService.estimate(product, 1_000_000L, 30);

      assertThat(result.impressions()).isEqualTo(new ImpressionRange(850_000, 1_150_000));
      assertThat(result.clicks()).isEqualTo(new ClickRange(42_500, 57_500));
    }

    @Test
    @DisplayName("CTR 이 없으면 기본 2% 로 클릭을 계산한다")
    void fallsBackToDefaultCtr() {
      EstimationProduct product = product(null, null, null,
          pricing(PricingModel.CPM, PriceType.LIST, "1000", null, null));

      EstimationResult result = EstimationService.estimate(product, 1_000_000L, 30);

      assertThat(result.clicks()).isEqualTo(new ClickRange(17_000, 23_000));
    }
  }

  @Nested
  @DisplayName("노출 기준 클릭 계산")
  class EstimateClicks {

    @Test
    @DisplayName("노출에 CTR 을 적용해 클릭 수를 낸다")
    void multipliesImpressionsByCtr() {
      assertThat(EstimationService.estimateClicks(1_500_000L, new BigDecimal("0.35")))
          .isEqualTo(5_250L);
    }

    @Test
    @DisplayName("CTR 을 모르면 기본 CTR 로 채우지 않고 클릭 수를 비운다")
    void nullWithoutCtr() {
      assertThat(EstimationService.estimateClicks(1_500_000L, null)).isNull();
    }

    @Test
    @DisplayName("소수점 클릭은 반올림한다")
    void roundsToWholeClicks() {
      assertThat(EstimationService.estimateClicks(1_000L, new BigDecimal("0.25")))
          .isEqualTo(3L);   // 2.5 → 3
    }

    @Test
    @DisplayName("노출을 모르면 클릭도 낼 수 없다")
    void nullWithoutImpressions() {
      assertThat(EstimationService.estimateClicks(null, new BigDecimal("5"))).isNull();
    }
  }

  @Nested
  @DisplayName("노출 추정 가능 여부")
  class EstimatesImpressions {

    @Test
    @DisplayName("CPM 은 예산과 단가만으로 노출을 낼 수 있다")
    void cpmAlwaysEstimates() {
      assertThat(EstimationService.estimatesImpressions(product(null, null, null,
          pricing(PricingModel.CPM, PriceType.LIST, "3000", null, null)))).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = PricingModel.class, names = {"SLOT", "FLAT", "PACKAGE", "CPP"})
    @DisplayName("단위형은 기대 노출이 있을 때만 노출을 낼 수 있다")
    void slotBasedNeedsExpectedImpressions(PricingModel pricingModel) {
      EstimationPricing pricing = pricing(pricingModel, PriceType.LIST, "500000", null, "7");

      assertThat(EstimationService.estimatesImpressions(
          product(null, 100_000L, null, pricing))).isTrue();
      assertThat(EstimationService.estimatesImpressions(
          product(null, null, null, pricing))).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = PricingModel.class, names = {"CPC", "CPA", "CPV", "PER_UNIT", "OTHER"})
    @DisplayName("노출 계산 규칙이 없는 과금 모델은 기대 노출이 있어도 노출을 낼 수 없다")
    void unsupportedModelsCannotEstimate(PricingModel pricingModel) {
      assertThat(EstimationService.estimatesImpressions(product(null, 100_000L, "1개월",
          pricing(pricingModel, PriceType.LIST, "500", null, null)))).isFalse();
    }

    @Test
    @DisplayName("값이 있는 단가가 없으면 노출을 낼 수 없다")
    void noUsablePricingCannotEstimate() {
      assertThat(EstimationService.estimatesImpressions(product(null, 100_000L, "1개월",
          pricing(PricingModel.CPM, PriceType.LIST, null, null, null)))).isFalse();
    }
  }

  @Nested
  @DisplayName("대표 단가 선택")
  class RepresentativePricing {

    @Test
    @DisplayName("판매가 단가를 우선 선택한다")
    void prefersSalePrice() {
      // SALE 5,000 선택 → 200,000 노출 (LIST 10,000 이었다면 100,000)
      EstimationProduct product = product(null, null, null,
          pricing(PricingModel.CPM, PriceType.LIST, "10000", null, null),
          pricing(PricingModel.CPM, PriceType.SALE, "5000", null, null));

      EstimationResult result = EstimationService.estimate(product, 1_000_000L, 30);

      assertThat(result.impressions()).isEqualTo(new ImpressionRange(170_000, 230_000));
    }

    @Test
    @DisplayName("판매가가 없으면 첫 번째 단가를 선택한다")
    void fallsBackToCheapestPricing() {
      // DISCOUNT 5,000 선택 → 200,000 노출
      EstimationProduct product = product(null, null, null,
          pricing(PricingModel.CPM, PriceType.LIST, "10000", null, null),
          pricing(PricingModel.CPM, PriceType.DISCOUNT, "5000", null, null));

      EstimationResult result = EstimationService.estimate(product, 1_000_000L, 30);

      assertThat(result.impressions()).isEqualTo(new ImpressionRange(170_000, 230_000));
    }

    @Test
    @DisplayName("판매가가 여러 개면 가장 싼 판매가를 선택한다")
    void picksCheapestSalePrice() {
      // SALE 4,000 선택 → 250,000 노출 (더 싼 LIST 1,000 이 있어도 판매가가 우선한다)
      EstimationProduct product = product(null, null, null,
          pricing(PricingModel.CPM, PriceType.SALE, "8000", null, null),
          pricing(PricingModel.CPM, PriceType.LIST, "1000", null, null),
          pricing(PricingModel.CPM, PriceType.SALE, "4000", null, null));

      EstimationResult result = EstimationService.estimate(product, 1_000_000L, 30);

      assertThat(result.impressions()).isEqualTo(new ImpressionRange(212_500, 287_500));
    }

    @Test
    @DisplayName("단가 목록의 순서가 바뀌어도 같은 대표 단가를 고른다")
    void isIndependentOfPricingOrder() {
      // 단가는 정렬 없이 조회되므로, 순서에 기대면 같은 입력이 실행마다 다른 결과를 낸다
      EstimationPricing cheap = pricing(PricingModel.CPM, PriceType.LIST, "5000", null, null);
      EstimationPricing expensive = pricing(PricingModel.CPM, PriceType.LIST, "10000", null, null);
      EstimationPricing middle = pricing(PricingModel.CPM, PriceType.LIST, "8000", null, null);

      EstimationResult ascending = EstimationService.estimate(
          product(null, null, null, cheap, middle, expensive), 1_000_000L, 30);
      EstimationResult descending = EstimationService.estimate(
          product(null, null, null, expensive, middle, cheap), 1_000_000L, 30);
      EstimationResult shuffled = EstimationService.estimate(
          product(null, null, null, middle, expensive, cheap), 1_000_000L, 30);

      assertThat(ascending.impressions()).isEqualTo(new ImpressionRange(170_000, 230_000));
      assertThat(descending.impressions()).isEqualTo(ascending.impressions());
      assertThat(shuffled.impressions()).isEqualTo(ascending.impressions());
    }

    @Test
    @DisplayName("대표 단가 조회도 목록 순서에 의존하지 않는다")
    void representativePricingIsIndependentOfOrder() {
      EstimationPricing cheap = pricing(PricingModel.CPC, PriceType.LIST, "500", null, null);
      EstimationPricing expensive = pricing(PricingModel.CPM, PriceType.LIST, "9000", null, null);

      assertThat(EstimationService.representativePricing(product(null, null, null, cheap,
          expensive))).isEqualTo(cheap);
      assertThat(EstimationService.representativePricing(product(null, null, null, expensive,
          cheap))).isEqualTo(cheap);
    }

    @Test
    @DisplayName("값이 없는 단가는 후보에서 제외한다")
    void skipsPricingWithoutValue() {
      EstimationProduct product = product(null, null, null,
          pricing(PricingModel.CPM, PriceType.SALE, null, null, null),
          pricing(PricingModel.CPM, PriceType.LIST, "10000", null, null));

      EstimationResult result = EstimationService.estimate(product, 1_000_000L, 30);

      assertThat(result.impressions()).isEqualTo(new ImpressionRange(85_000, 115_000));
    }

    @Test
    @DisplayName("값이 있는 단가가 하나도 없으면 계산할 수 없다")
    void returnsNullWhenNoUsablePricing() {
      EstimationProduct product = product(new BigDecimal("3"), 100_000L, "1개월",
          pricing(PricingModel.CPM, PriceType.LIST, null, null, null));

      assertThat(EstimationService.estimate(product, 1_000_000L, 30)).isNull();
    }

    @Test
    @DisplayName("단가 목록이 비어 있으면 계산할 수 없다")
    void returnsNullWhenPricingsEmpty() {
      EstimationProduct product = new EstimationProduct(null, 100_000L, "1개월", List.of());

      assertThat(EstimationService.estimate(product, 1_000_000L, 30)).isNull();
    }
  }

  @Nested
  @DisplayName("예산만으로 하는 집행 가능 판정")
  class ExecutabilityOnly {

    @Test
    @DisplayName("예산이 기준 단가 이상이면 집행할 수 있다. 같은 금액도 집행 가능이다")
    void executableWhenBudgetCoversPrice() {
      EstimationProduct product = product(null, null, null,
          pricing(PricingModel.CPM, PriceType.LIST, "1000000", null, null));

      assertThat(EstimationService.isExecutable(product, 1_000_001L)).isTrue();
      assertThat(EstimationService.isExecutable(product, 1_000_000L)).isTrue();
      assertThat(EstimationService.isExecutable(product, 999_999L)).isFalse();
    }

    @Test
    @DisplayName("기간을 보는 estimate 와 같은 판정을 낸다")
    void agreesWithEstimate() {
      EstimationProduct product = product(new BigDecimal("3"), 100_000L, "1개월",
          pricing(PricingModel.SLOT, PriceType.LIST, "3000000", null, "30"));

      assertThat(EstimationService.isExecutable(product, 5_000_000L))
          .isEqualTo(EstimationService.estimate(product, 5_000_000L, 30).isExecutable());
      assertThat(EstimationService.isExecutable(product, 1_000_000L))
          .isEqualTo(EstimationService.estimate(product, 1_000_000L, 30).isExecutable());
    }

    @Test
    @DisplayName("판매가가 있으면 더 싼 공시가가 있어도 판매가를 기준으로 삼는다")
    void judgesBySalePriceEvenWhenListPriceIsCheaper() {
      EstimationProduct product = product(null, null, null,
          pricing(PricingModel.CPM, PriceType.LIST, "1000000", null, null),
          pricing(PricingModel.CPM, PriceType.SALE, "5000000", null, null));

      assertThat(EstimationService.isExecutable(product, 3_000_000L)).isFalse();
    }

    @Test
    @DisplayName("값이 있는 단가가 하나도 없으면 판정할 수 없다")
    void cannotJudgeWithoutUsablePricing() {
      assertThat(EstimationService.isExecutable(product(null, null, null,
          pricing(PricingModel.CPM, PriceType.LIST, null, null, null)), 1_000_000L)).isNull();
      assertThat(EstimationService.isExecutable(
          new EstimationProduct(null, null, null, List.of()), 1_000_000L)).isNull();
    }
  }

  private static EstimationProduct product(BigDecimal ctr, Long expectedImpressions,
      String expectedPeriod, EstimationPricing... pricings) {
    return new EstimationProduct(ctr, expectedImpressions, expectedPeriod, List.of(pricings));
  }

  private static EstimationPricing pricing(PricingModel pricingModel, PriceType priceType,
      String value, String valueMax, String unitDays) {
    return new EstimationPricing(pricingModel, priceType, decimal(value), decimal(valueMax),
        decimal(unitDays));
  }

  private static BigDecimal decimal(String value) {
    return value == null ? null : new BigDecimal(value);
  }
}
