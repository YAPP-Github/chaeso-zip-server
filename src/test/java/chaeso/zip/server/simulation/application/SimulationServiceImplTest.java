package chaeso.zip.server.simulation.application;

import static chaeso.zip.server.support.ChannelCatalogFixture.channel;
import static chaeso.zip.server.support.ChannelCatalogFixture.channelWithIconUrl;
import static chaeso.zip.server.support.ChannelCatalogFixture.pricing;
import static chaeso.zip.server.support.ChannelCatalogFixture.product;
import static chaeso.zip.server.support.ChannelCatalogFixture.productWithCtrRange;
import static chaeso.zip.server.support.ChannelCatalogFixture.productWithMinBudget;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import chaeso.zip.server.channel.domain.ChannelNotFoundException;
import chaeso.zip.server.channel.domain.entity.ChannelPricing;
import chaeso.zip.server.channel.domain.entity.ChannelProduct;
import chaeso.zip.server.channel.domain.repository.ChannelPricingRepository;
import chaeso.zip.server.channel.domain.repository.ChannelProductRepository;
import chaeso.zip.server.channel.domain.repository.ChannelRepository;
import chaeso.zip.server.channel.domain.vo.PriceType;
import chaeso.zip.server.channel.domain.vo.PricingModel;
import chaeso.zip.server.estimation.application.DefaultCtrProvider;
import chaeso.zip.server.estimation.application.dto.CountRangeResponse;
import chaeso.zip.server.onboarding.domain.vo.CampaignPeriod;
import chaeso.zip.server.simulation.application.dto.AllocationCommand;
import chaeso.zip.server.simulation.application.dto.SimulationCommand;
import chaeso.zip.server.simulation.application.dto.SimulationItemResponse;
import chaeso.zip.server.simulation.application.dto.SimulationResponse;
import chaeso.zip.server.simulation.application.dto.SimulationSummaryResponse;
import chaeso.zip.server.simulation.domain.SimulationNotFoundException;
import chaeso.zip.server.simulation.domain.entity.BudgetSimulation;
import chaeso.zip.server.simulation.domain.entity.BudgetSimulationItem;
import chaeso.zip.server.simulation.domain.repository.BudgetSimulationItemRepository;
import chaeso.zip.server.simulation.domain.repository.BudgetSimulationRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SimulationServiceImplTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID CHANNEL_ID = UUID.randomUUID();
  private static final UUID PRODUCT_ID = UUID.randomUUID();
  private static final String CHANNEL_NAME = "11번가 광고";
  private static final String SERVICE_NAME = "채소집";

  /** 카탈로그 평균 CTR. 상품에 CTR 이 없을 때 이 값이 쓰이는지로 주입을 확인한다. */
  private static final BigDecimal AVERAGE_CTR = new BigDecimal("2.5");

  private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 3, 14, 10, 22, 31);

  @Mock
  private ChannelRepository channelRepository;
  @Mock
  private ChannelProductRepository channelProductRepository;
  @Mock
  private ChannelPricingRepository channelPricingRepository;
  @Mock
  private BudgetSimulationRepository budgetSimulationRepository;
  @Mock
  private BudgetSimulationItemRepository budgetSimulationItemRepository;
  @Mock
  private DefaultCtrProvider defaultCtrProvider;

  @InjectMocks
  private SimulationServiceImpl simulationService;

  @Nested
  @DisplayName("계산 (POST /simulations/estimate)")
  class Estimate {

    @BeforeEach
    void stubAverageCtr() {
      given(defaultCtrProvider.averageCtrPercent()).willReturn(AVERAGE_CTR);
    }

    @Test
    @DisplayName("CPM 매체의 노출·클릭 범위와 CPM 단가를 계산하고 합계는 범위 중앙값으로 낸다")
    void calculatesCpmChannel() {
      String iconUrl = "https://assets.chaeso-zip.com/channels/icon.png";
      given(channelRepository.findAllById(anyList()))
          .willReturn(List.of(channelWithIconUrl(CHANNEL_ID, CHANNEL_NAME, iconUrl)));
      given(channelProductRepository.findByChannelIdIn(anyList()))
          .willReturn(List.of(product(PRODUCT_ID, CHANNEL_ID)));
      given(channelPricingRepository.findByChannelProductIdIn(anyList()))
          .willReturn(List.of(pricing(PRODUCT_ID, PricingModel.CPM, "3000")));

      SimulationResponse response = simulationService.estimate(
          command(3_000_000, CampaignPeriod.M1, allocation(3_000_000, "100")));

      SimulationItemResponse item = response.items().getFirst();
      assertThat(response.simulationId()).isNull();          // 저장하지 않았다
      assertThat(item.channelName()).isEqualTo(CHANNEL_NAME);
      assertThat(item.iconUrl()).isEqualTo(iconUrl);
      assertThat(item.channelProductId()).isEqualTo(PRODUCT_ID);
      assertThat(item.isExecutable()).isTrue();
      assertThat(item.shortfallWon()).isNull();
      // 3,000,000 / 3,000 * 1000 = 1,000,000 노출, ±15%
      assertThat(item.estImpressions()).isEqualTo(new CountRangeResponse(850_000, 1_150_000));
      // 평균 CTR 2.5% 적용
      assertThat(item.estClicks()).isEqualTo(new CountRangeResponse(21_250, 28_750));
      assertThat(item.cpmWon()).isEqualByComparingTo("3000");
      // 클릭당 비용으로 통일 표시: 3,000,000 / 25,000 클릭(중앙값) = 120 원
      assertThat(item.cpcWon()).isEqualByComparingTo("120");
      assertThat(response.totalEstImpressions()).isEqualTo(1_000_000);
      assertThat(response.totalEstClicks()).isEqualTo(25_000);
      assertThat(response.executableChannelCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("상품이 여러 개면 대표 단가가 가장 싼 상품을 기준으로 추정한다")
    void picksCheapestProduct() {
      UUID expensiveId = UUID.randomUUID();
      given(channelRepository.findAllById(anyList()))
          .willReturn(List.of(channel(CHANNEL_ID, CHANNEL_NAME)));
      given(channelProductRepository.findByChannelIdIn(anyList())).willReturn(List.of(
          product(expensiveId, CHANNEL_ID), product(PRODUCT_ID, CHANNEL_ID)));
      given(channelPricingRepository.findByChannelProductIdIn(anyList())).willReturn(List.of(
          pricing(expensiveId, PricingModel.CPM, "5000"),
          pricing(PRODUCT_ID, PricingModel.CPM, "3000")));

      SimulationResponse response = simulationService.estimate(
          command(3_000_000, CampaignPeriod.M1, allocation(3_000_000, "100")));

      SimulationItemResponse item = response.items().getFirst();
      assertThat(item.channelProductId()).isEqualTo(PRODUCT_ID);
      assertThat(item.cpmWon()).isEqualByComparingTo("3000");
      assertThat(item.estImpressions().max()).isEqualTo(1_150_000);   // 5,000 기준이면 690,000
    }

    @Test
    @DisplayName("노출을 낼 수 있는 상품이 더 비싸도, 못 내는 싼 상품보다 우선 선택된다")
    void prefersProductThatCanEstimateImpressions() {
      // 클릭당 10원과 구좌당 15만원은 단위가 달라 금액만으로 비교하면 안 된다.
      // 10원 상품이 뽑히면 노출·클릭을 전혀 못 내고 "10원이면 집행 가능"으로 읽힌다.
      UUID cheapCpcId = UUID.randomUUID();
      given(channelRepository.findAllById(anyList()))
          .willReturn(List.of(channel(CHANNEL_ID, CHANNEL_NAME)));
      given(channelProductRepository.findByChannelIdIn(anyList())).willReturn(List.of(
          product(cheapCpcId, CHANNEL_ID),
          product(PRODUCT_ID, CHANNEL_ID, new BigDecimal("2"), 15_000L, null)));
      given(channelPricingRepository.findByChannelProductIdIn(anyList())).willReturn(List.of(
          pricing(cheapCpcId, PricingModel.CPC, "10"),
          pricing(PRODUCT_ID, PricingModel.SLOT, PriceType.LIST, "150000", null, "1")));

      SimulationItemResponse item = simulationService.estimate(
          command(1_000_000, CampaignPeriod.LE_1W, allocation(1_000_000, "100"))).items().getFirst();

      assertThat(item.channelProductId()).isEqualTo(PRODUCT_ID);
      assertThat(item.estImpressions()).isNotNull();
      // 구좌형이어도 클릭당 비용으로 환산된다: 1,000,000 / 2,000 클릭 = 500 원
      assertThat(item.cpcWon()).isEqualByComparingTo("500");
    }

    @Test
    @DisplayName("배분 0원이어도 노출을 낼 수 있는 상품 기준으로 필요 금액을 알려준다")
    void reportsShortfallOfEstimableProduct() {
      UUID cheapCpcId = UUID.randomUUID();
      given(channelRepository.findAllById(anyList()))
          .willReturn(List.of(channel(CHANNEL_ID, CHANNEL_NAME)));
      given(channelProductRepository.findByChannelIdIn(anyList())).willReturn(List.of(
          product(cheapCpcId, CHANNEL_ID),
          product(PRODUCT_ID, CHANNEL_ID, new BigDecimal("2"), 15_000L, null)));
      given(channelPricingRepository.findByChannelProductIdIn(anyList())).willReturn(List.of(
          pricing(cheapCpcId, PricingModel.CPC, "10"),
          pricing(PRODUCT_ID, PricingModel.SLOT, PriceType.LIST, "150000", null, "1")));

      SimulationItemResponse item = simulationService.estimate(
          command(1_000_000, CampaignPeriod.LE_1W, allocation(0, "0"))).items().getFirst();

      assertThat(item.shortfallWon()).isEqualTo(150_000);   // 10 원이 아니다
      assertThat(item.basisNote()).startsWith("미집행 (배분 예산 0원)");
    }

    @Test
    @DisplayName("노출을 낼 수 있는 상품이 없으면 남은 상품으로 집행 가능 여부라도 알려준다")
    void fallsBackToNonEstimableProduct() {
      givenCatalog(product(PRODUCT_ID, CHANNEL_ID),
          pricing(PRODUCT_ID, PricingModel.CPC, "500"));

      SimulationItemResponse item = simulationService.estimate(
          command(1_000_000, CampaignPeriod.M1, allocation(1_000_000, "100"))).items().getFirst();

      assertThat(item.channelProductId()).isEqualTo(PRODUCT_ID);   // 견적 문의로 빠지지 않는다
      assertThat(item.isExecutable()).isTrue();
      assertThat(item.estImpressions()).isNull();
      assertThat(item.cpcWon()).isEqualByComparingTo("500");
      assertThat(item.basisNote()).startsWith("노출 정보 미제공 상품");
    }

    @Test
    @DisplayName("대표 단가가 같은 상품이 여럿이면 조회 순서와 무관하게 같은 상품을 고른다")
    void picksSamePriceProductDeterministically() {
      // 상품은 정렬 없이 조회되므로, 순서에 기대면 같은 요청이 실행마다 다른 상품을 고른다
      UUID lowerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
      UUID higherId = UUID.fromString("22222222-2222-2222-2222-222222222222");
      List<ChannelProduct> products = List.of(product(higherId, CHANNEL_ID),
          product(lowerId, CHANNEL_ID));
      List<ChannelPricing> pricings = List.of(
          pricing(higherId, PricingModel.CPM, "3000"),
          pricing(lowerId, PricingModel.CPM, "3000"));

      given(channelRepository.findAllById(anyList()))
          .willReturn(List.of(channel(CHANNEL_ID, CHANNEL_NAME)));
      given(channelProductRepository.findByChannelIdIn(anyList()))
          .willReturn(products, products.reversed());
      given(channelPricingRepository.findByChannelProductIdIn(anyList())).willReturn(pricings);

      SimulationCommand command = command(3_000_000, CampaignPeriod.M1, allocation(3_000_000, "100"));

      assertThat(simulationService.estimate(command).items().getFirst().channelProductId())
          .isEqualTo(lowerId);
      assertThat(simulationService.estimate(command).items().getFirst().channelProductId())
          .isEqualTo(lowerId);
    }

    @Test
    @DisplayName("클릭당 과금 매체는 단가를 환산 없이 그대로 클릭당 비용으로 쓴다")
    void usesCpcPriceAsIs() {
      givenCatalog(product(PRODUCT_ID, CHANNEL_ID, null, 100_000L, "1개월"),
          pricing(PRODUCT_ID, PricingModel.CPC, "500"));

      SimulationItemResponse item = simulationService.estimate(
          command(3_000_000, CampaignPeriod.M1, allocation(3_000_000, "100"))).items().getFirst();

      assertThat(item.cpcWon()).isEqualByComparingTo("500");
      assertThat(item.cpmWon()).isNull();
    }

    @Test
    @DisplayName("예상 클릭이 없는 매체는 클릭당 비용을 환산할 수 없어 비워 둔다")
    void leavesCpcEmptyWithoutClickEstimate() {
      // 노출 정보가 없어 클릭을 못 내는 구좌형 상품
      givenCatalog(product(PRODUCT_ID, CHANNEL_ID),
          pricing(PRODUCT_ID, PricingModel.SLOT, "1000000"));

      SimulationItemResponse item = simulationService.estimate(
          command(2_000_000, CampaignPeriod.M1, allocation(2_000_000, "100"))).items().getFirst();

      assertThat(item.isExecutable()).isTrue();
      assertThat(item.estClicks()).isNull();
      assertThat(item.cpcWon()).isNull();
    }

    @Test
    @DisplayName("집행 불가 매체는 클릭 추정이 없으므로 클릭당 비용도 비워 둔다")
    void leavesCpcEmptyWhenNotExecutable() {
      givenCatalog(product(PRODUCT_ID, CHANNEL_ID),
          pricing(PRODUCT_ID, PricingModel.CPM, "5000000"));

      SimulationItemResponse item = simulationService.estimate(
          command(1_000_000, CampaignPeriod.LE_1W, allocation(1_000_000, "100"))).items().getFirst();

      assertThat(item.isExecutable()).isFalse();
      assertThat(item.cpcWon()).isNull();
    }

    @Test
    @DisplayName("배분 예산이 단가에 못 미치면 노출·클릭 없이 부족 금액만 채운다")
    void reportsShortfallWhenBudgetBelowPrice() {
      givenCatalog(product(PRODUCT_ID, CHANNEL_ID),
          pricing(PRODUCT_ID, PricingModel.CPM, "5000000"));

      SimulationResponse response = simulationService.estimate(
          command(1_000_000, CampaignPeriod.LE_1W, allocation(1_000_000, "100")));

      SimulationItemResponse item = response.items().getFirst();
      assertThat(item.isExecutable()).isFalse();
      assertThat(item.estImpressions()).isNull();
      assertThat(item.estClicks()).isNull();
      assertThat(item.minBudgetWon()).isEqualTo(5_000_000);
      assertThat(item.shortfallWon()).isEqualTo(4_000_000);
      assertThat(item.basisNote()).startsWith("집행 예산 부족");
      assertThat(response.totalEstImpressions()).isZero();
      assertThat(response.totalEstClicks()).isZero();
    }

    @Test
    @DisplayName("최소 집행 금액이 등록돼 있으면 단가가 아니라 그 금액으로 집행 가능 여부를 가른다")
    void judgesAgainstMinBudgetInsteadOfPrice() {
      givenCatalog(productWithMinBudget(PRODUCT_ID, CHANNEL_ID, 10_000),
          pricing(PRODUCT_ID, PricingModel.CPC, "1"));

      SimulationItemResponse item = simulationService.estimate(
          command(5_000, CampaignPeriod.M1, allocation(5_000, "100"))).items().getFirst();

      assertThat(item.minBudgetWon()).isEqualTo(10_000);
      assertThat(item.isExecutable()).isFalse();      // 단가 기준이면 true 로 잘못 나온다
      assertThat(item.shortfallWon()).isEqualTo(5_000);
      assertThat(item.basisNote()).startsWith("집행 예산 부족");
    }

    @Test
    @DisplayName("최소 집행 금액을 채우면 집행 가능으로 보고 부족 금액을 비운다")
    void judgesExecutableWhenBudgetMeetsMinBudget() {
      givenCatalog(productWithMinBudget(PRODUCT_ID, CHANNEL_ID, 10_000),
          pricing(PRODUCT_ID, PricingModel.CPC, "1"));

      SimulationItemResponse item = simulationService.estimate(
          command(10_000, CampaignPeriod.M1, allocation(10_000, "100"))).items().getFirst();

      assertThat(item.minBudgetWon()).isEqualTo(10_000);
      assertThat(item.isExecutable()).isTrue();
      assertThat(item.shortfallWon()).isNull();
    }

    @Test
    @DisplayName("최소 집행 금액이 없는 상품은 대표 단가로 판정하고 그 금액을 최소 집행 금액으로 알려준다")
    void fallsBackToPriceWithoutMinBudget() {
      givenCatalog(product(PRODUCT_ID, CHANNEL_ID),
          pricing(PRODUCT_ID, PricingModel.CPM, "5000000"));

      SimulationItemResponse item = simulationService.estimate(
          command(1_000_000, CampaignPeriod.M1, allocation(1_000_000, "100"))).items().getFirst();

      // 판정 기준을 비워 두면 화면은 예산 부족만 알고 얼마가 필요한지 모른다
      assertThat(item.minBudgetWon()).isEqualTo(5_000_000);
      assertThat(item.isExecutable()).isFalse();
      assertThat(item.shortfallWon()).isEqualTo(4_000_000);   // 대표 단가 기준
    }

    @Test
    @DisplayName("최소 집행 금액이 없는 상품의 대표 단가가 원 미만이면 올려서 판정 기준으로 쓴다")
    void ceilsFractionalPriceAsMinBudget() {
      givenCatalog(product(PRODUCT_ID, CHANNEL_ID),
          pricing(PRODUCT_ID, PricingModel.CPM, "5000000.4"));

      SimulationItemResponse item = simulationService.estimate(
          command(5_000_000, CampaignPeriod.M1, allocation(5_000_000, "100"))).items().getFirst();

      assertThat(item.minBudgetWon()).isEqualTo(5_000_001);
      assertThat(item.isExecutable()).isFalse();
      assertThat(item.shortfallWon()).isEqualTo(1);
    }

    @Test
    @DisplayName("미집행 매체도 최소 집행 금액이 있으면 그 금액을 필요 금액으로 알려준다")
    void reportsMinBudgetAsShortfallWhenNotAllocated() {
      givenCatalog(productWithMinBudget(PRODUCT_ID, CHANNEL_ID, 10_000),
          pricing(PRODUCT_ID, PricingModel.CPC, "1"));

      SimulationItemResponse item = simulationService.estimate(
          command(1_000_000, CampaignPeriod.M1, allocation(0, "0"))).items().getFirst();

      assertThat(item.minBudgetWon()).isEqualTo(10_000);
      assertThat(item.shortfallWon()).isEqualTo(10_000);      // 단가 1 원이 아니다
      assertThat(item.basisNote()).startsWith("미집행 (배분 예산 0원)");
    }

    @Test
    @DisplayName("집행 불가로 갈린 매체는 합계에서 빠진다")
    void excludesChannelBelowMinBudgetFromTotals() {
      givenCatalog(productWithMinBudget(PRODUCT_ID, CHANNEL_ID, 10_000_000),
          pricing(PRODUCT_ID, PricingModel.CPM, "3000"));

      SimulationResponse response = simulationService.estimate(
          command(3_000_000, CampaignPeriod.M1, allocation(3_000_000, "100")));

      // 단가(3,000원) 기준이면 집행 가능으로 잡혀 노출·클릭이 합계에 들어갔다
      assertThat(response.items().getFirst().isExecutable()).isFalse();
      assertThat(response.totalEstImpressions()).isZero();
      assertThat(response.totalEstClicks()).isZero();
      assertThat(response.executableChannelCount()).isZero();
    }

    @Test
    @DisplayName("배분 예산이 0원이면 미집행으로 두고 집행에 필요한 금액을 알려준다")
    void marksZeroBudgetAsNotAllocated() {
      givenCatalog(product(PRODUCT_ID, CHANNEL_ID),
          pricing(PRODUCT_ID, PricingModel.CPM, "3000"));

      SimulationItemResponse item = simulationService.estimate(
          command(1_000_000, CampaignPeriod.M1, allocation(0, "0"))).items().getFirst();

      assertThat(item.isExecutable()).isFalse();
      assertThat(item.allocatedBudgetWon()).isZero();
      assertThat(item.estImpressions()).isNull();
      assertThat(item.minBudgetWon()).isEqualTo(3_000);      // 대표 단가로 대신한다
      assertThat(item.shortfallWon()).isEqualTo(3_000);
      assertThat(item.cpmWon()).isEqualByComparingTo("3000");
      assertThat(item.basisNote()).startsWith("미집행 (배분 예산 0원)");
    }

    @Test
    @DisplayName("단가 정보가 있는 상품이 없는 매체는 견적 문의 안내만 남긴다")
    void guidesToQuoteWhenNoUsablePricing() {
      String iconUrl = "https://assets.chaeso-zip.com/channels/icon.png";
      given(channelRepository.findAllById(anyList()))
          .willReturn(List.of(channelWithIconUrl(CHANNEL_ID, CHANNEL_NAME, iconUrl)));
      given(channelProductRepository.findByChannelIdIn(anyList()))
          .willReturn(List.of(product(PRODUCT_ID, CHANNEL_ID)));
      given(channelPricingRepository.findByChannelProductIdIn(anyList())).willReturn(List.of(
          pricing(PRODUCT_ID, PricingModel.CPM, PriceType.LIST, null, null, null)));

      SimulationItemResponse item = simulationService.estimate(
          command(1_000_000, CampaignPeriod.M1, allocation(1_000_000, "100"))).items().getFirst();

      assertThat(item.channelName()).isEqualTo(CHANNEL_NAME);
      assertThat(item.iconUrl()).isEqualTo(iconUrl);
      assertThat(item.channelProductId()).isNull();
      assertThat(item.isExecutable()).isFalse();
      assertThat(item.estImpressions()).isNull();
      assertThat(item.shortfallWon()).isNull();
      assertThat(item.cpmWon()).isNull();
      assertThat(item.basisNote()).startsWith("견적 문의 필요");
    }

    @Test
    @DisplayName("상품이 아예 없는 매체도 견적 문의 안내만 남긴다")
    void guidesToQuoteWhenNoProducts() {
      given(channelRepository.findAllById(anyList()))
          .willReturn(List.of(channel(CHANNEL_ID, CHANNEL_NAME)));
      given(channelProductRepository.findByChannelIdIn(anyList())).willReturn(List.of());

      SimulationItemResponse item = simulationService.estimate(
          command(1_000_000, CampaignPeriod.M1, allocation(1_000_000, "100"))).items().getFirst();

      assertThat(item.basisNote()).startsWith("견적 문의 필요");
      assertThat(item.isExecutable()).isFalse();
    }

    @Test
    @DisplayName("노출 정보가 없는 상품은 집행 가능 여부만 판단하고 그 사유를 남긴다")
    void reportsExecutabilityOnlyWithoutImpressionData() {
      givenCatalog(product(PRODUCT_ID, CHANNEL_ID),
          pricing(PRODUCT_ID, PricingModel.SLOT, "1000000"));

      SimulationResponse response = simulationService.estimate(
          command(2_000_000, CampaignPeriod.M1, allocation(2_000_000, "100")));

      SimulationItemResponse item = response.items().getFirst();
      assertThat(item.isExecutable()).isTrue();
      assertThat(item.estImpressions()).isNull();
      assertThat(item.estClicks()).isNull();
      assertThat(item.shortfallWon()).isNull();
      assertThat(item.basisNote()).startsWith("노출 정보 미제공 상품");
      assertThat(response.totalEstImpressions()).isZero();
    }

    @Test
    @DisplayName("CTR 구간만 있는 상품은 평균 CTR 대신 구간 평균으로 클릭을 계산한다")
    void prefersProductCtrRangeOverCatalogAverage() {
      givenCatalog(productWithCtrRange(PRODUCT_ID, CHANNEL_ID, "1", "3"),
          pricing(PRODUCT_ID, PricingModel.CPM, "1000"));

      SimulationItemResponse item = simulationService.estimate(
          command(1_000_000, CampaignPeriod.M1, allocation(1_000_000, "100"))).items().getFirst();

      // 노출 850,000~1,150,000 에 구간 평균 2% 적용 (평균 CTR 2.5% 였다면 21,250~28,750)
      assertThat(item.estClicks()).isEqualTo(new CountRangeResponse(17_000, 23_000));
    }

    @Test
    @DisplayName("구좌형 상품은 기간과 예산 중 빡센 쪽으로 구좌 수가 제한된다")
    void limitsSlotsByPeriodOrBudget() {
      givenCatalog(product(PRODUCT_ID, CHANNEL_ID, new BigDecimal("2"), 100_000L, null),
          pricing(PRODUCT_ID, PricingModel.SLOT, PriceType.LIST, "500000", null, "15"));

      // 기간 30일 / 구좌 15일 = 2구좌, 예산 3,000,000 / 500,000 = 6구좌 → 2구좌
      SimulationItemResponse item = simulationService.estimate(
          command(3_000_000, CampaignPeriod.M1, allocation(3_000_000, "100"))).items().getFirst();

      assertThat(item.estImpressions()).isEqualTo(new CountRangeResponse(170_000, 230_000));
    }

    @Test
    @DisplayName("일 단위 기간형(CPP) 매체도 노출·클릭과 클릭당 비용을 낸다")
    void estimatesDailyPeriodPricingChannel() {
      givenCatalog(product(PRODUCT_ID, CHANNEL_ID, null, 86_000L, "1일"),
          pricing(PRODUCT_ID, PricingModel.CPP, PriceType.DISCOUNT, "140000", null, "1"));

      // 기간 7일 / 단위 1일 = 7단위, 예산 720,000 / 140,000 = 5.142...단위 → 예산 기준
      SimulationItemResponse item = simulationService.estimate(
          command(720_000, CampaignPeriod.LE_1W, allocation(720_000, "100"))).items().getFirst();

      assertThat(item.isExecutable()).isTrue();
      assertThat(item.minBudgetWon()).isEqualTo(140_000);
      assertThat(item.estImpressions()).isEqualTo(new CountRangeResponse(375_943, 508_629));
      assertThat(item.estClicks()).isEqualTo(new CountRangeResponse(9_399, 12_716));   // 평균 2.5%
      assertThat(item.cpcWon()).isEqualByComparingTo("65");   // 720,000 / 11,058 클릭(중앙값)
      assertThat(item.basisNote()).doesNotContain("노출 정보 미제공");
    }

    @Test
    @DisplayName("여러 매체의 합계는 집행 가능한 매체만 더한다")
    void totalsCountExecutableChannelsOnly() {
      UUID otherChannelId = UUID.randomUUID();
      UUID otherProductId = UUID.randomUUID();
      given(channelRepository.findAllById(anyList())).willReturn(List.of(
          channel(CHANNEL_ID, CHANNEL_NAME), channel(otherChannelId, "당근마켓 광고")));
      given(channelProductRepository.findByChannelIdIn(anyList())).willReturn(List.of(
          product(PRODUCT_ID, CHANNEL_ID), product(otherProductId, otherChannelId)));
      given(channelPricingRepository.findByChannelProductIdIn(anyList())).willReturn(List.of(
          pricing(PRODUCT_ID, PricingModel.CPM, "3000"),
          pricing(otherProductId, PricingModel.CPM, "9000000")));   // 배분 예산으로 집행 불가

      SimulationResponse response = simulationService.estimate(new SimulationCommand(SERVICE_NAME,
          4_000_000, CampaignPeriod.M1,
          List.of(new AllocationCommand(CHANNEL_ID, 3_000_000, new BigDecimal("75")),
              new AllocationCommand(otherChannelId, 1_000_000, new BigDecimal("25")))));

      assertThat(response.items()).hasSize(2);
      assertThat(response.items().get(1).isExecutable()).isFalse();
      assertThat(response.totalEstImpressions()).isEqualTo(1_000_000);   // 첫 매체 몫만
      assertThat(response.totalEstClicks()).isEqualTo(25_000);
      assertThat(response.executableChannelCount()).isEqualTo(1);        // 2개 중 1개만
    }

    @Test
    @DisplayName("배분한 채널이 존재하지 않으면 404 로 거부한다")
    void rejectsUnknownChannel() {
      given(channelRepository.findAllById(anyList())).willReturn(List.of());

      SimulationCommand command = command(1_000_000, CampaignPeriod.M1, allocation(1_000_000, "100"));

      assertThatThrownBy(() -> simulationService.estimate(command))
          .isInstanceOf(ChannelNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("저장 (POST /simulations)")
  class Save {

    @Test
    @DisplayName("계산 결과를 스냅샷으로 저장하고 simulationId 를 반환한다")
    void savesSnapshotAndReturnsId() {
      UUID simulationId = UUID.randomUUID();
      given(defaultCtrProvider.averageCtrPercent()).willReturn(AVERAGE_CTR);
      givenCatalog(productWithMinBudget(PRODUCT_ID, CHANNEL_ID, 1_000_000),
          pricing(PRODUCT_ID, PricingModel.CPM, "3000"));
      given(budgetSimulationRepository.save(any(BudgetSimulation.class)))
          .willAnswer(invocation -> withId(invocation.getArgument(0), simulationId));

      SimulationResponse response = simulationService.save(USER_ID,
          command(3_000_000, CampaignPeriod.M1, allocation(3_000_000, "100")));

      assertThat(response.simulationId()).isEqualTo(simulationId);

      ArgumentCaptor<BudgetSimulation> header = ArgumentCaptor.forClass(BudgetSimulation.class);
      verify(budgetSimulationRepository).save(header.capture());
      assertThat(header.getValue().getUserId()).isEqualTo(USER_ID);
      assertThat(header.getValue().getServiceName()).isEqualTo(SERVICE_NAME);
      assertThat(header.getValue().getPeriod()).isEqualTo(CampaignPeriod.M1);
      assertThat(header.getValue().getTotalBudgetWon()).isEqualTo(3_000_000);
      assertThat(header.getValue().getTotalEstImpressions()).isEqualTo(1_000_000);
      assertThat(header.getValue().getTotalEstClicks()).isEqualTo(25_000);

      BudgetSimulationItem saved = capturedItems().getFirst();
      assertThat(saved.getBudgetSimulationId()).isEqualTo(simulationId);
      assertThat(saved.getChannelId()).isEqualTo(CHANNEL_ID);
      assertThat(saved.getChannelProductId()).isEqualTo(PRODUCT_ID);
      assertThat(saved.getEstImpressionsMin()).isEqualTo(850_000);
      assertThat(saved.getEstImpressionsMax()).isEqualTo(1_150_000);
      assertThat(saved.getEstClicksMin()).isEqualTo(21_250);
      assertThat(saved.getEstClicksMax()).isEqualTo(28_750);
      assertThat(saved.getCpcWon()).isEqualByComparingTo("120");   // 환산값도 스냅샷에 남는다
      assertThat(saved.getCpmWon()).isEqualByComparingTo("3000");
      assertThat(saved.getMinBudgetWon()).isEqualTo(1_000_000);
      assertThat(saved.isExecutable()).isTrue();
      assertThat(saved.getBasisNote()).isEqualTo(
          "매체 소개서 기반 / VAT 별도 가정 / CTR 미제공 시 전체 평균 CTR 적용");
    }

    @Test
    @DisplayName("매체 순서를 sortOrder 로 남겨 불러오기에서 그대로 재현한다")
    void keepsRequestedChannelOrder() {
      UUID otherChannelId = UUID.randomUUID();
      UUID otherProductId = UUID.randomUUID();
      given(defaultCtrProvider.averageCtrPercent()).willReturn(AVERAGE_CTR);
      given(channelRepository.findAllById(anyList())).willReturn(List.of(
          channel(CHANNEL_ID, CHANNEL_NAME), channel(otherChannelId, "당근마켓 광고")));
      given(channelProductRepository.findByChannelIdIn(anyList())).willReturn(List.of(
          product(PRODUCT_ID, CHANNEL_ID), product(otherProductId, otherChannelId)));
      given(channelPricingRepository.findByChannelProductIdIn(anyList())).willReturn(List.of(
          pricing(PRODUCT_ID, PricingModel.CPM, "3000"),
          pricing(otherProductId, PricingModel.CPM, "4000")));
      given(budgetSimulationRepository.save(any(BudgetSimulation.class)))
          .willAnswer(invocation -> withId(invocation.getArgument(0), UUID.randomUUID()));

      simulationService.save(USER_ID, new SimulationCommand(SERVICE_NAME, 4_000_000, CampaignPeriod.M1,
          List.of(new AllocationCommand(otherChannelId, 1_000_000, new BigDecimal("25")),
              new AllocationCommand(CHANNEL_ID, 3_000_000, new BigDecimal("75")))));

      List<BudgetSimulationItem> items = capturedItems();
      assertThat(items).hasSize(2);
      assertThat(items.get(0).getSortOrder()).isZero();
      assertThat(items.get(0).getChannelId()).isEqualTo(otherChannelId);
      assertThat(items.get(1).getSortOrder()).isEqualTo(1);
      assertThat(items.get(1).getChannelId()).isEqualTo(CHANNEL_ID);
    }
  }

  @Nested
  @DisplayName("불러오기 (GET /simulations/latest)")
  class FindLatest {

    @Test
    @DisplayName("저장된 스냅샷을 재계산 없이 그대로 반환한다")
    void restoresSnapshotWithoutRecalculating() {
      UUID simulationId = UUID.randomUUID();
      BudgetSimulation simulation = withId(BudgetSimulation.builder()
          .userId(USER_ID)
          .totalBudgetWon(3_000_000L)
          .period(CampaignPeriod.M1)
          .totalEstImpressions(1_000_000L)
          .totalEstClicks(25_000L)
          .build(), simulationId);
      given(budgetSimulationRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
          .willReturn(Optional.of(simulation));
      given(budgetSimulationItemRepository
          .findByBudgetSimulationIdOrderBySortOrderAsc(simulationId))
          .willReturn(List.of(BudgetSimulationItem.builder()
              .budgetSimulationId(simulationId)
              .channelId(CHANNEL_ID)
              .channelProductId(PRODUCT_ID)
              .sortOrder(0)
              .allocatedBudgetWon(3_000_000L)
              .allocationPct(new BigDecimal("100"))
              .estImpressionsMin(850_000L)
              .estImpressionsMax(1_150_000L)
              .estClicksMin(21_250L)
              .estClicksMax(28_750L)
              .cpcWon(new BigDecimal("120"))
              .cpmWon(new BigDecimal("3000"))
              .minBudgetWon(1_000_000L)
              .executable(true)
              .basisNote("저장 당시 고지")
              .build()));
      given(channelRepository.findAllById(anyList()))
          .willReturn(List.of(channel(CHANNEL_ID, CHANNEL_NAME)));

      SimulationResponse response = simulationService.findLatest(USER_ID).orElseThrow();

      assertThat(response.simulationId()).isEqualTo(simulationId);
      assertThat(response.totalBudgetWon()).isEqualTo(3_000_000);
      assertThat(response.period()).isEqualTo(CampaignPeriod.M1);
      assertThat(response.totalEstImpressions()).isEqualTo(1_000_000);
      assertThat(response.totalEstClicks()).isEqualTo(25_000);

      SimulationItemResponse item = response.items().getFirst();
      assertThat(item.channelName()).isEqualTo(CHANNEL_NAME);
      assertThat(item.channelProductId()).isEqualTo(PRODUCT_ID);
      assertThat(item.estImpressions()).isEqualTo(new CountRangeResponse(850_000, 1_150_000));
      assertThat(item.estClicks()).isEqualTo(new CountRangeResponse(21_250, 28_750));
      assertThat(item.cpcWon()).isEqualByComparingTo("120");   // 저장 당시 환산값이 그대로
      assertThat(item.cpmWon()).isEqualByComparingTo("3000");
      assertThat(item.minBudgetWon()).isEqualTo(1_000_000);    // 판정 기준도 그대로
      assertThat(item.basisNote()).isEqualTo("저장 당시 고지");
      assertThat(response.executableChannelCount()).isEqualTo(1);   // 항목에서 센다

      // 재계산하지 않았음: 상품·단가 조회도, CTR 집계도 하지 않는다
      verifyNoInteractions(channelProductRepository, channelPricingRepository,
          defaultCtrProvider);
    }

    @Test
    @DisplayName("추정하지 못한 항목은 범위가 없는 상태로 되살아난다")
    void restoresItemWithoutRanges() {
      UUID simulationId = UUID.randomUUID();
      BudgetSimulation simulation = withId(BudgetSimulation.builder()
          .userId(USER_ID)
          .totalBudgetWon(1_000_000L)
          .period(CampaignPeriod.LE_1W)
          .totalEstImpressions(0L)
          .totalEstClicks(0L)
          .build(), simulationId);
      given(budgetSimulationRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
          .willReturn(Optional.of(simulation));
      given(budgetSimulationItemRepository
          .findByBudgetSimulationIdOrderBySortOrderAsc(simulationId))
          .willReturn(List.of(BudgetSimulationItem.builder()
              .budgetSimulationId(simulationId)
              .channelId(CHANNEL_ID)
              .sortOrder(0)
              .allocatedBudgetWon(1_000_000L)
              .allocationPct(new BigDecimal("100"))
              .executable(false)
              .shortfallWon(4_000_000L)
              .basisNote("견적 문의 필요")
              .build()));
      given(channelRepository.findAllById(anyList()))
          .willReturn(List.of(channel(CHANNEL_ID, CHANNEL_NAME)));

      SimulationItemResponse item =
          simulationService.findLatest(USER_ID).orElseThrow().items().getFirst();

      assertThat(item.estImpressions()).isNull();
      assertThat(item.estClicks()).isNull();
      assertThat(item.isExecutable()).isFalse();
      assertThat(item.shortfallWon()).isEqualTo(4_000_000);
    }

    @Test
    @DisplayName("저장된 결과가 없으면 빈 값을 반환한다")
    void returnsEmptyWhenNothingSaved() {
      given(budgetSimulationRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
          .willReturn(Optional.empty());

      assertThat(simulationService.findLatest(USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("이후 삭제된 채널을 참조하는 항목은 예외 없이 이름·아이콘을 비운 채로 되살아난다")
    void restoresItemWithDeletedChannelAsNull() {
      UUID simulationId = UUID.randomUUID();
      BudgetSimulation simulation = withId(BudgetSimulation.builder()
          .userId(USER_ID)
          .totalBudgetWon(1_000_000L)
          .period(CampaignPeriod.LE_1W)
          .totalEstImpressions(0L)
          .totalEstClicks(0L)
          .build(), simulationId);
      given(budgetSimulationRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
          .willReturn(Optional.of(simulation));
      given(budgetSimulationItemRepository
          .findByBudgetSimulationIdOrderBySortOrderAsc(simulationId))
          .willReturn(List.of(BudgetSimulationItem.builder()
              .budgetSimulationId(simulationId)
              .channelId(CHANNEL_ID)
              .sortOrder(0)
              .allocatedBudgetWon(1_000_000L)
              .allocationPct(new BigDecimal("100"))
              .executable(false)
              .basisNote("견적 문의 필요")
              .build()));
      given(channelRepository.findAllById(anyList())).willReturn(List.of());   // 채널이 삭제됨

      SimulationItemResponse item =
          simulationService.findLatest(USER_ID).orElseThrow().items().getFirst();

      assertThat(item.channelName()).isNull();
      assertThat(item.iconUrl()).isNull();
    }
  }

  @Nested
  @DisplayName("내 목록 (GET /simulations)")
  class FindMySimulations {

    private final Pageable pageable = PageRequest.of(0, 10);

    @Test
    @DisplayName("저장된 요약을 반환하고 집행 가능 매체 수는 항목에서 센다")
    void summarizesSavedSnapshots() {
      UUID simulationId = UUID.randomUUID();
      UUID otherChannelId = UUID.randomUUID();
      givenMyPage(savedSimulation(simulationId, USER_ID));
      given(budgetSimulationItemRepository
          .findByBudgetSimulationIdInOrderBySortOrderAsc(List.of(simulationId)))
          .willReturn(List.of(
              savedItem(simulationId, CHANNEL_ID, 0, true),
              savedItem(simulationId, otherChannelId, 1, false)));
      given(channelRepository.findAllById(anyList())).willReturn(List.of(
          channel(CHANNEL_ID, CHANNEL_NAME), channel(otherChannelId, "당근마켓 광고")));

      SimulationSummaryResponse summary =
          simulationService.findMySimulations(USER_ID, pageable).getContent().getFirst();

      assertThat(summary.id()).isEqualTo(simulationId);
      assertThat(summary.serviceName()).isEqualTo(SERVICE_NAME);
      assertThat(summary.createdAt()).isEqualTo(CREATED_AT);
      assertThat(summary.channelNames()).containsExactly(CHANNEL_NAME, "당근마켓 광고");

      // 재계산하지 않았음: 상품·단가 조회도, CTR 집계도 하지 않는다
      verifyNoInteractions(channelProductRepository, channelPricingRepository, defaultCtrProvider);
    }

    @Test
    @DisplayName("매체명은 저장 순서대로 모두 담는다")
    void listsChannelNamesInSavedOrder() {
      UUID simulationId = UUID.randomUUID();
      List<UUID> channelIds = List.of(CHANNEL_ID, UUID.randomUUID(), UUID.randomUUID(),
          UUID.randomUUID());
      givenMyPage(savedSimulation(simulationId, USER_ID));
      given(budgetSimulationItemRepository
          .findByBudgetSimulationIdInOrderBySortOrderAsc(List.of(simulationId)))
          .willReturn(IntStream.range(0, channelIds.size())
              .mapToObj(order -> savedItem(simulationId, channelIds.get(order), order, true))
              .toList());
      given(channelRepository.findAllById(anyList())).willReturn(
          IntStream.range(0, channelIds.size())
              .mapToObj(order -> channel(channelIds.get(order), "매체" + order))
              .toList());

      SimulationSummaryResponse summary =
          simulationService.findMySimulations(USER_ID, pageable).getContent().getFirst();

      assertThat(summary.channelNames()).containsExactly("매체0", "매체1", "매체2", "매체3");
    }

    @Test
    @DisplayName("예산을 배분하지 않은 매체는 매체명에서 뺀다")
    void excludesUnallocatedChannels() {
      // 담아만 두고 0원을 준 매체는 상세 재현을 위해 저장되지만, 목록에서는 배분한 매체가 아니다
      UUID simulationId = UUID.randomUUID();
      UUID unallocatedChannelId = UUID.randomUUID();
      givenMyPage(savedSimulation(simulationId, USER_ID));
      given(budgetSimulationItemRepository
          .findByBudgetSimulationIdInOrderBySortOrderAsc(List.of(simulationId)))
          .willReturn(List.of(
              savedItem(simulationId, unallocatedChannelId, 0, false, 0L),
              savedItem(simulationId, CHANNEL_ID, 1, true)));
      given(channelRepository.findAllById(anyList())).willReturn(List.of(
          channel(unallocatedChannelId, "당근마켓 광고"), channel(CHANNEL_ID, CHANNEL_NAME)));

      SimulationSummaryResponse summary =
          simulationService.findMySimulations(USER_ID, pageable).getContent().getFirst();

      assertThat(summary.channelNames()).containsExactly(CHANNEL_NAME);
    }

    @Test
    @DisplayName("저장된 결과가 없으면 빈 페이지를 반환하고 항목·채널은 조회하지 않는다")
    void returnsEmptyPageWithoutLoadingItems() {
      givenMyPage();

      assertThat(simulationService.findMySimulations(USER_ID, pageable)).isEmpty();

      verifyNoInteractions(channelRepository);
      verify(budgetSimulationItemRepository, never())
          .findByBudgetSimulationIdInOrderBySortOrderAsc(anyList());
    }

    private void givenMyPage(BudgetSimulation... simulations) {
      given(budgetSimulationRepository.findByUserIdOrderByCreatedAtDescIdDesc(USER_ID, pageable))
          .willReturn(new PageImpl<>(List.of(simulations), pageable, simulations.length));
    }
  }

  @Nested
  @DisplayName("상세 (GET /simulations/{simulationId})")
  class FindSimulation {

    @Test
    @DisplayName("본인이 저장한 시뮬레이션은 매체별 항목까지 재계산 없이 그대로 반환한다")
    void restoresOwnSnapshot() {
      UUID simulationId = UUID.randomUUID();
      given(budgetSimulationRepository.findById(simulationId))
          .willReturn(Optional.of(savedSimulation(simulationId, USER_ID)));
      given(budgetSimulationItemRepository
          .findByBudgetSimulationIdOrderBySortOrderAsc(simulationId))
          .willReturn(List.of(savedItem(simulationId, CHANNEL_ID, 0, true)));
      given(channelRepository.findAllById(anyList()))
          .willReturn(List.of(channel(CHANNEL_ID, CHANNEL_NAME)));

      SimulationResponse response = simulationService.findSimulation(USER_ID, simulationId);

      assertThat(response.simulationId()).isEqualTo(simulationId);
      assertThat(response.totalBudgetWon()).isEqualTo(3_000_000);
      assertThat(response.executableChannelCount()).isEqualTo(1);
      SimulationItemResponse item = response.items().getFirst();
      assertThat(item.channelName()).isEqualTo(CHANNEL_NAME);
      assertThat(item.allocatedBudgetWon()).isEqualTo(1_000_000);
      assertThat(item.basisNote()).isEqualTo("저장 당시 고지");

      verifyNoInteractions(channelProductRepository, channelPricingRepository, defaultCtrProvider);
    }

    @Test
    @DisplayName("다른 사용자가 저장한 시뮬레이션은 없는 것과 같은 404 로 숨기고 항목도 읽지 않는다")
    void hidesOtherUsersSnapshot() {
      UUID simulationId = UUID.randomUUID();
      UUID otherUserId = UUID.randomUUID();
      given(budgetSimulationRepository.findById(simulationId))
          .willReturn(Optional.of(savedSimulation(simulationId, otherUserId)));

      assertThatThrownBy(() -> simulationService.findSimulation(USER_ID, simulationId))
          .isInstanceOf(SimulationNotFoundException.class);

      verifyNoInteractions(budgetSimulationItemRepository, channelRepository);
    }

    @Test
    @DisplayName("존재하지 않는 id 는 404 로 거부한다")
    void rejectsUnknownId() {
      UUID simulationId = UUID.randomUUID();
      given(budgetSimulationRepository.findById(simulationId)).willReturn(Optional.empty());

      assertThatThrownBy(() -> simulationService.findSimulation(USER_ID, simulationId))
          .isInstanceOf(SimulationNotFoundException.class);
    }
  }

  @Captor
  private ArgumentCaptor<List<BudgetSimulationItem>> itemsCaptor;

  private List<BudgetSimulationItem> capturedItems() {
    verify(budgetSimulationItemRepository).saveAll(itemsCaptor.capture());
    return itemsCaptor.getValue();
  }

  private void givenCatalog(ChannelProduct product, ChannelPricing... pricings) {
    given(channelRepository.findAllById(anyList()))
        .willReturn(List.of(channel(CHANNEL_ID, CHANNEL_NAME)));
    given(channelProductRepository.findByChannelIdIn(anyList())).willReturn(List.of(product));
    given(channelPricingRepository.findByChannelProductIdIn(anyList()))
        .willReturn(List.of(pricings));
  }

  private static SimulationCommand command(int totalBudgetWon, CampaignPeriod period,
      AllocationCommand... allocations) {
    return new SimulationCommand(SERVICE_NAME, totalBudgetWon, period, List.of(allocations));
  }

  private static AllocationCommand allocation(int budgetWon, String allocationPct) {
    return new AllocationCommand(CHANNEL_ID, budgetWon, new BigDecimal(allocationPct));
  }

  /** 저장 시 발급되는 id 를 리포지토리 목이 대신 채워준다. */
  private static <T> T withId(T entity, UUID id) {
    ReflectionTestUtils.setField(entity, "id", id);
    return entity;
  }

  /** 이미 저장된 헤더. id 와 저장 시각은 JPA 가 채우는 값이라 목에서 직접 심는다. */
  private static BudgetSimulation savedSimulation(UUID simulationId, UUID userId) {
    BudgetSimulation simulation = withId(BudgetSimulation.builder()
        .userId(userId)
        .serviceName(SERVICE_NAME)
        .totalBudgetWon(3_000_000L)
        .period(CampaignPeriod.M1)
        .totalEstImpressions(1_000_000L)
        .totalEstClicks(25_000L)
        .build(), simulationId);
    ReflectionTestUtils.setField(simulation, "createdAt", CREATED_AT);
    return simulation;
  }

  private static BudgetSimulationItem savedItem(UUID simulationId, UUID channelId, int sortOrder,
      boolean executable) {
    return savedItem(simulationId, channelId, sortOrder, executable, 1_000_000L);
  }

  private static BudgetSimulationItem savedItem(UUID simulationId, UUID channelId, int sortOrder,
      boolean executable, long allocatedBudgetWon) {
    return BudgetSimulationItem.builder()
        .budgetSimulationId(simulationId)
        .channelId(channelId)
        .sortOrder(sortOrder)
        .allocatedBudgetWon(allocatedBudgetWon)
        .allocationPct(new BigDecimal("50"))
        .executable(executable)
        .basisNote("저장 당시 고지")
        .build();
  }
}
