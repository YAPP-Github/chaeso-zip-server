package chaeso.zip.server.comparison.application;

import static chaeso.zip.server.support.ChannelCatalogFixture.channel;
import static chaeso.zip.server.support.ChannelCatalogFixture.pricing;
import static chaeso.zip.server.support.ChannelCatalogFixture.product;
import static chaeso.zip.server.support.ChannelCatalogFixture.withObjectives;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;

import chaeso.zip.server.channel.domain.ChannelNotFoundException;
import chaeso.zip.server.channel.domain.entity.Channel;
import chaeso.zip.server.channel.domain.entity.ChannelPricing;
import chaeso.zip.server.channel.domain.entity.ChannelProduct;
import chaeso.zip.server.channel.domain.repository.ChannelPricingRepository;
import chaeso.zip.server.channel.domain.repository.ChannelProductRepository;
import chaeso.zip.server.channel.domain.repository.ChannelRepository;
import chaeso.zip.server.channel.domain.vo.AgeBand;
import chaeso.zip.server.channel.domain.vo.CampaignObjective;
import chaeso.zip.server.channel.domain.vo.Category;
import chaeso.zip.server.channel.domain.vo.Gender;
import chaeso.zip.server.channel.domain.vo.PricingModel;
import chaeso.zip.server.comparison.application.dto.ChannelComparisonItemResponse;
import chaeso.zip.server.comparison.application.dto.ChannelComparisonResponse;
import chaeso.zip.server.estimation.application.DefaultCtrProvider;
import chaeso.zip.server.estimation.application.dto.CountRangeResponse;
import chaeso.zip.server.estimation.domain.EstimationService;
import chaeso.zip.server.onboarding.domain.OnboardingNotFoundException;
import chaeso.zip.server.onboarding.domain.entity.Onboarding;
import chaeso.zip.server.onboarding.domain.repository.OnboardingRepository;
import chaeso.zip.server.onboarding.domain.vo.CampaignPeriod;
import chaeso.zip.server.support.OnboardingFixture;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChannelComparisonServiceImplTest {

  private static final Category MATCHED_INDUSTRY = Category.MEDICAL_HEALTHCARE;
  private static final CampaignObjective MATCHED_OBJECTIVE = CampaignObjective.AWARENESS;
  private static final AgeBand MATCHED_AGE_BAND = AgeBand.AGE_20S;
  private static final AgeBand OTHER_AGE_BAND = AgeBand.AGE_50S_PLUS;

  private static final long BUDGET_MAX = 3_000_000L;
  private static final BigDecimal AVERAGE_CTR = new BigDecimal("2.5");

  @Mock
  private ChannelRepository channelRepository;
  @Mock
  private ChannelProductRepository channelProductRepository;
  @Mock
  private ChannelPricingRepository channelPricingRepository;
  @Mock
  private OnboardingRepository onboardingRepository;
  @Mock
  private DefaultCtrProvider defaultCtrProvider;

  private ChannelComparisonServiceImpl comparisonService;

  @BeforeEach
  void setUp() {
    comparisonService = new ChannelComparisonServiceImpl(channelRepository,
        channelProductRepository, channelPricingRepository, onboardingRepository,
        defaultCtrProvider);
    lenient().when(defaultCtrProvider.averageCtrPercent()).thenReturn(AVERAGE_CTR);
  }

  @Nested
  @DisplayName("채널 카탈로그 기준 비교")
  class WithoutOnboarding {

    @Test
    @DisplayName("비로그인 요청은 주요 오디언스와 광고 형태, 타기팅을 노출하지 않는다")
    void hidesAudienceForAnonymousRequest() {
      Channel channel = matchedChannel();
      ReflectionTestUtils.setField(channel, "defaultTags",
          List.of("정적태그1", "정적태그2", "정적태그3"));
      ChannelProduct product = matchedProduct(channel);
      ChannelPricing cpmPricing = pricing(product.getId(), PricingModel.CPM, "3000");
      givenCatalog(new CatalogEntry(channel, product, cpmPricing));

      ChannelComparisonItemResponse item = comparisonService
          .compare(List.of(channel.getId()), null, null)
          .items().getFirst();

      assertThat(item.channelId()).isEqualTo(channel.getId());
      assertThat(item.channelName()).isEqualTo(channel.getName());
      assertThat(item.audienceSummary()).isNull();
      assertThat(item.adFormats()).isEmpty();
      assertThat(item.targetingMethods()).isEmpty();
      assertThat(item.minBudgetWon()).isEqualTo(channel.getMinBudgetWon());
      assertThat(item.advantages()).containsExactly("빠른 노출");
      assertThat(item.tags()).containsExactly("정적태그1", "정적태그2", "정적태그3");
      assertThat(item.cpmWon()).isEqualByComparingTo("3000");
      assertThat(item.matchRate()).isNull();
      assertThat(item.estImpressions()).isNull();
      assertThat(item.estClicks()).isNull();
    }

    @Test
    @DisplayName("로그인한 요청은 온보딩이 없어도 채널 정보와 기본 태그 전체를 준다")
    void returnsChannelInfoWhenLoggedIn() {
      Channel channel = matchedChannel();
      ReflectionTestUtils.setField(channel, "defaultTags",
          List.of("정적태그1", "정적태그2", "정적태그3"));
      ChannelProduct product = matchedProduct(channel);
      ChannelPricing cpmPricing = pricing(product.getId(), PricingModel.CPM, "3000");
      givenCatalog(new CatalogEntry(channel, product, cpmPricing));

      ChannelComparisonItemResponse item = comparisonService
          .compare(List.of(channel.getId()), null, UUID.randomUUID())
          .items().getFirst();

      assertThat(item.audienceSummary()).isEqualTo(channel.getAudienceSummary());
      assertThat(item.adFormats()).isEqualTo(channel.getAdFormats());
      assertThat(item.targetingMethods()).isEqualTo(channel.getTargetingMethods());
      assertThat(item.tags()).containsExactly("정적태그1", "정적태그2", "정적태그3");
      assertThat(item.matchRate()).isNull();
    }

    @Test
    @DisplayName("온보딩이 없으면 대표 과금 방식의 단가만 비교값으로 반환한다")
    void returnsOnlyRepresentativePricingModel() {
      Channel cpmChannel = channel(UUID.randomUUID(), "CPM 채널");
      ChannelProduct cpmProduct = product(UUID.randomUUID(), cpmChannel.getId());
      ChannelPricing cpmPricing = pricing(cpmProduct.getId(), PricingModel.CPM, "3000");

      Channel cpcChannel = channel(UUID.randomUUID(), "CPC 채널");
      ChannelProduct cpcProduct = product(UUID.randomUUID(), cpcChannel.getId());
      ChannelPricing cpcPricing = pricing(cpcProduct.getId(), PricingModel.CPC, "150");

      given(channelRepository.findByIdAndActiveTrue(cpmChannel.getId()))
          .willReturn(Optional.of(cpmChannel));
      given(channelRepository.findByIdAndActiveTrue(cpcChannel.getId()))
          .willReturn(Optional.of(cpcChannel));
      given(channelProductRepository.findByChannelIdIn(any()))
          .willReturn(List.of(cpmProduct, cpcProduct));
      given(channelPricingRepository.findByChannelProductIdIn(any()))
          .willReturn(List.of(cpmPricing, cpcPricing));

      ChannelComparisonResponse response = comparisonService.compare(
          List.of(cpmChannel.getId(), cpcChannel.getId()), null, null);

      ChannelComparisonItemResponse cpmItem = response.items().get(0);
      assertThat(cpmItem.cpmWon()).isEqualByComparingTo("3000");
      assertThat(cpmItem.cpcWon()).isNull();

      ChannelComparisonItemResponse cpcItem = response.items().get(1);
      assertThat(cpcItem.cpcWon()).isEqualByComparingTo("150");
      assertThat(cpcItem.cpmWon()).isNull();
    }

    @Test
    @DisplayName("로그인 + 온보딩 없음은 기본 예산 100만원/기간 1개월 기준으로 예상 노출/클릭과 환산 클릭당 비용을 채운다")
    void fillsEstimatesWithDefaultBudgetWhenLoggedInWithoutOnboarding() {
      Channel channel = matchedChannel();
      ChannelProduct product = matchedProduct(channel);
      ChannelPricing cpmPricing = pricing(product.getId(), PricingModel.CPM, "3000");
      givenCatalog(new CatalogEntry(channel, product, cpmPricing));

      ChannelComparisonItemResponse item = comparisonService
          .compare(List.of(channel.getId()), null, UUID.randomUUID())
          .items().getFirst();

      assertThat(item.matchRate()).isNull();
      assertThat(item.cpmWon()).isEqualByComparingTo("3000");
      // 1,000,000 / 3,000 * 1000 = 333,333 노출, ±15%
      assertThat(item.estImpressions()).isEqualTo(new CountRangeResponse(283_333, 383_333));
      // 상품에 CTR 이 없어 카탈로그 평균 2.5% 적용
      assertThat(item.estClicks()).isEqualTo(new CountRangeResponse(7_083, 9_583));
      // 1,000,000 / 8,333 클릭(중앙값) = 120 원
      assertThat(item.cpcWon()).isEqualByComparingTo("120");
    }

    @Test
    @DisplayName("로그인 + 온보딩 없음은 기본 예산보다 대표 단가가 비싸면 예상 노출/클릭을 비우고 고정 단가만 반환한다")
    void leavesEstimatesEmptyWhenDefaultBudgetIsBelowRepresentativePrice() {
      Channel channel = matchedChannel();
      ChannelProduct product = matchedProduct(channel);
      ChannelPricing cpmPricing = pricing(product.getId(), PricingModel.CPM, "5000000");
      givenCatalog(new CatalogEntry(channel, product, cpmPricing));

      ChannelComparisonItemResponse item = comparisonService
          .compare(List.of(channel.getId()), null, UUID.randomUUID())
          .items().getFirst();

      assertThat(item.matchRate()).isNull();
      assertThat(item.cpmWon()).isEqualByComparingTo("5000000");
      assertThat(item.cpcWon()).isNull();
      assertThat(item.estImpressions()).isNull();
      assertThat(item.estClicks()).isNull();
    }
  }

  @Nested
  @DisplayName("온보딩 기준 맞춤 비교")
  class WithOnboarding {

    @Test
    @DisplayName("비로그인 요청은 적합도와 예상 노출·클릭을 비우고 맞은 축 태그와 단가는 남긴다")
    void leavesMatchRateEmptyForAnonymousOnboarding() {
      Channel channel = matchedChannel();
      ReflectionTestUtils.setField(channel, "defaultTags", List.of("빠른매칭", "안정노출"));
      ChannelProduct product = matchedProduct(channel);
      ChannelPricing pricing = pricing(product.getId(), PricingModel.CPM, "3000");
      givenCatalog(new CatalogEntry(channel, product, pricing));

      UUID onboardingId = UUID.randomUUID();
      Onboarding anonymousOnboarding = matchedOnboarding(null);
      given(onboardingRepository.findById(onboardingId))
          .willReturn(Optional.of(anonymousOnboarding));

      ChannelComparisonItemResponse item = comparisonService
          .compare(List.of(channel.getId()), onboardingId, null)
          .items().getFirst();

      assertThat(item.matchRate()).isNull();
      assertThat(item.estImpressions()).isNull();
      assertThat(item.estClicks()).isNull();
      assertThat(item.audienceSummary()).isNull();
      assertThat(item.adFormats()).isEmpty();
      assertThat(item.targetingMethods()).isEmpty();
      assertThat(item.tags()).containsExactly("CATEGORY", "OBJECTIVE");
      assertThat(item.advantages()).containsExactly("빠른 노출");
      assertThat(item.cpmWon()).isEqualByComparingTo("3000");
      assertThat(item.cpcWon()).isNotNull();
    }

    @Test
    @DisplayName("온보딩 예산으로 CPM 채널의 클릭당 비용을 환산하고 최소 예산은 카탈로그 값을 유지한다")
    void convertsCpmToCpcAndKeepsCatalogMinBudget() {
      Channel channel = matchedChannel();
      ChannelProduct product = matchedProduct(channel);
      ChannelPricing cpmPricing = pricing(product.getId(), PricingModel.CPM, "3000");
      givenCatalog(new CatalogEntry(channel, product, cpmPricing));

      UUID onboardingId = UUID.randomUUID();
      Onboarding onboarding = matchedOnboarding(null);
      given(onboardingRepository.findById(onboardingId)).willReturn(Optional.of(onboarding));

      ChannelComparisonItemResponse item = comparisonService
          .compare(List.of(channel.getId()), onboardingId, null)
          .items().getFirst();

      assertThat(item.cpmWon()).isEqualByComparingTo("3000");
      assertThat(item.cpcWon()).isNotNull();
      assertThat(item.minBudgetWon()).isEqualTo(1_000_000);
    }

    @Test
    @DisplayName("로그인 맞춤 비교는 집행 가능한 예산을 기준으로 예상 노출·클릭과 환산 클릭당 비용을 채운다")
    void fillsEstimatesWhenExecutable() {
      Channel channel = matchedChannel();
      ChannelProduct product = matchedProduct(channel);
      ChannelPricing cpmPricing = pricing(product.getId(), PricingModel.CPM, "3000");
      givenCatalog(new CatalogEntry(channel, product, cpmPricing));

      UUID userId = UUID.randomUUID();
      UUID onboardingId = UUID.randomUUID();
      given(onboardingRepository.findById(onboardingId))
          .willReturn(Optional.of(matchedOnboarding(userId)));

      ChannelComparisonItemResponse item = comparisonService
          .compare(List.of(channel.getId()), onboardingId, userId)
          .items().getFirst();

      assertThat(item.matchRate()).isEqualTo(100);
      assertThat(item.cpmWon()).isEqualByComparingTo("3000");
      // 3,000,000 / 3,000 * 1000 = 1,000,000 노출, ±15%
      assertThat(item.estImpressions()).isEqualTo(new CountRangeResponse(850_000, 1_150_000));
      // 상품에 CTR 이 없어 카탈로그 평균 2.5% 적용
      assertThat(item.estClicks()).isEqualTo(new CountRangeResponse(21_250, 28_750));
      // 3,000,000 / 25,000 클릭(중앙값) = 120 원
      assertThat(item.cpcWon()).isEqualByComparingTo("120");
    }

    @Test
    @DisplayName("클릭당 과금 매체는 단가를 환산 없이 클릭당 비용으로 준다")
    void usesCpcPriceAsIs() {
      Channel channel = matchedChannel();
      ChannelProduct product = matchedProduct(channel);
      ChannelPricing cpcPricing = pricing(product.getId(), PricingModel.CPC, "500");
      givenCatalog(new CatalogEntry(channel, product, cpcPricing));

      UUID userId = UUID.randomUUID();
      UUID onboardingId = UUID.randomUUID();
      given(onboardingRepository.findById(onboardingId))
          .willReturn(Optional.of(matchedOnboarding(userId)));

      ChannelComparisonItemResponse item = comparisonService
          .compare(List.of(channel.getId()), onboardingId, userId)
          .items().getFirst();

      assertThat(item.cpcWon()).isEqualByComparingTo("500");
      assertThat(item.cpmWon()).isNull();
      assertThat(item.matchRate()).isEqualTo(100);
      assertThat(item.estImpressions()).isNull();
      assertThat(item.estClicks()).isNull();
    }

    @Test
    @DisplayName("온보딩 예산이 대표 단가보다 적으면 예상 노출·클릭 수와 환산 CPC를 비우고 고정 CPM은 유지한다")
    void leavesEstimatesEmptyWhenBudgetIsBelowRepresentativePrice() {
      Channel channel = matchedChannel();
      ChannelProduct product = matchedProduct(channel);
      ChannelPricing cpmPricing = pricing(product.getId(), PricingModel.CPM, "5000000");
      givenCatalog(new CatalogEntry(channel, product, cpmPricing));

      UUID userId = UUID.randomUUID();
      UUID onboardingId = UUID.randomUUID();
      given(onboardingRepository.findById(onboardingId))
          .willReturn(Optional.of(matchedOnboarding(userId)));

      ChannelComparisonItemResponse item = comparisonService
          .compare(List.of(channel.getId()), onboardingId, userId)
          .items().getFirst();

      assertThat(item.matchRate()).isEqualTo(100);
      assertThat(item.tags()).containsExactly("CATEGORY", "OBJECTIVE");
      assertThat(item.cpmWon()).isEqualByComparingTo("5000000");
      assertThat(item.cpcWon()).isNull();
      assertThat(item.estImpressions()).isNull();
      assertThat(item.estClicks()).isNull();
    }

    @Test
    @DisplayName("온보딩 예산이 0이면 예상 노출/클릭을 계산하지 않고 고정 단가만 반환한다")
    void leavesEstimatesEmptyWhenBudgetIsZero() {
      Channel channel = matchedChannel();
      ChannelProduct product = matchedProduct(channel);
      ChannelPricing cpmPricing = pricing(product.getId(), PricingModel.CPM, "3000");
      givenCatalog(new CatalogEntry(channel, product, cpmPricing));

      UUID userId = UUID.randomUUID();
      UUID onboardingId = UUID.randomUUID();
      given(onboardingRepository.findById(onboardingId)).willReturn(Optional.of(
          OnboardingFixture.onboarding(userId, MATCHED_INDUSTRY, MATCHED_OBJECTIVE,
              List.of(MATCHED_AGE_BAND), 0L, 0L, CampaignPeriod.M1)));

      ChannelComparisonItemResponse item = comparisonService
          .compare(List.of(channel.getId()), onboardingId, userId)
          .items().getFirst();

      assertThat(item.matchRate()).isEqualTo(100);
      assertThat(item.cpmWon()).isEqualByComparingTo("3000");
      assertThat(item.cpcWon()).isNull();
      assertThat(item.estImpressions()).isNull();
      assertThat(item.estClicks()).isNull();
    }

    @Test
    @DisplayName("온보딩 예산이 0이면 클릭당 과금 매체의 고정 CPC만 남기고 예상 노출·클릭은 비운다")
    void keepsFixedCpcWhenBudgetIsZero() {
      Channel channel = matchedChannel();
      ChannelProduct product = matchedProduct(channel);
      ChannelPricing cpcPricing = pricing(product.getId(), PricingModel.CPC, "500");
      givenCatalog(new CatalogEntry(channel, product, cpcPricing));

      UUID userId = UUID.randomUUID();
      UUID onboardingId = UUID.randomUUID();
      given(onboardingRepository.findById(onboardingId)).willReturn(Optional.of(
          OnboardingFixture.onboarding(userId, MATCHED_INDUSTRY, MATCHED_OBJECTIVE,
              List.of(MATCHED_AGE_BAND), 0L, 0L, CampaignPeriod.M1)));

      ChannelComparisonItemResponse item = comparisonService
          .compare(List.of(channel.getId()), onboardingId, userId)
          .items().getFirst();

      assertThat(item.matchRate()).isEqualTo(100);
      assertThat(item.cpcWon()).isEqualByComparingTo("500");
      assertThat(item.cpmWon()).isNull();
      assertThat(item.estImpressions()).isNull();
      assertThat(item.estClicks()).isNull();
    }

    @Test
    @DisplayName("추정을 계산할 수 없으면 예상 노출·클릭을 비우고 고정 단가만 반환한다")
    void leavesEstimatesEmptyWhenEstimateIsUnavailable() {
      Channel channel = matchedChannel();
      ChannelProduct product = matchedProduct(channel);
      ChannelPricing cpmPricing = pricing(product.getId(), PricingModel.CPM, "3000");
      givenCatalog(new CatalogEntry(channel, product, cpmPricing));

      UUID userId = UUID.randomUUID();
      UUID onboardingId = UUID.randomUUID();
      given(onboardingRepository.findById(onboardingId))
          .willReturn(Optional.of(matchedOnboarding(userId)));

      try (MockedStatic<EstimationService> estimation =
          mockStatic(EstimationService.class, CALLS_REAL_METHODS)) {
        estimation.when(() -> EstimationService.estimate(any(), anyLong(), anyInt()))
            .thenReturn(null);

        ChannelComparisonItemResponse item = comparisonService
            .compare(List.of(channel.getId()), onboardingId, userId)
            .items().getFirst();

        assertThat(item.matchRate()).isEqualTo(100);
        assertThat(item.cpmWon()).isEqualByComparingTo("3000");
        assertThat(item.cpcWon()).isNull();
        assertThat(item.estImpressions()).isNull();
        assertThat(item.estClicks()).isNull();
      }
    }

    @Test
    @DisplayName("등록된 상품이 없어 예상 노출·클릭 수를 계산할 수 없으면 적합도만 반환한다")
    void leavesEstimatesEmptyWithoutProduct() {
      Channel channel = matchedChannel();
      given(channelRepository.findByIdAndActiveTrue(channel.getId()))
          .willReturn(Optional.of(channel));
      given(channelProductRepository.findByChannelIdIn(any())).willReturn(List.of());

      UUID userId = UUID.randomUUID();
      UUID onboardingId = UUID.randomUUID();
      given(onboardingRepository.findById(onboardingId))
          .willReturn(Optional.of(matchedOnboarding(userId)));

      ChannelComparisonItemResponse item = comparisonService
          .compare(List.of(channel.getId()), onboardingId, userId)
          .items().getFirst();

      assertThat(item.minBudgetWon()).isEqualTo(1_000_000);
      // 상품이 없으면 목적 축이 빠지고 업종·연령만 맞아 67%
      assertThat(item.matchRate()).isEqualTo(67);
      assertThat(item.estImpressions()).isNull();
      assertThat(item.estClicks()).isNull();
    }

    @Test
    @DisplayName("자신이 제출한 온보딩으로 맞춤 비교할 수 있다")
    void allowsOwnedOnboarding() {
      Channel channel = matchedChannel();
      ChannelProduct product = matchedProduct(channel);
      ChannelPricing pricing = pricing(product.getId(), PricingModel.CPM, "3000");
      givenCatalog(new CatalogEntry(channel, product, pricing));

      UUID userId = UUID.randomUUID();
      UUID onboardingId = UUID.randomUUID();
      Onboarding onboarding = matchedOnboarding(userId);
      given(onboardingRepository.findById(onboardingId)).willReturn(Optional.of(onboarding));

      ChannelComparisonResponse response =
          comparisonService.compare(List.of(channel.getId()), onboardingId, userId);

      assertThat(response.items().getFirst().matchRate()).isEqualTo(100);
      assertThat(response.items().getFirst().audienceSummary())
          .isEqualTo(channel.getAudienceSummary());
    }

    @Test
    @DisplayName("적합도 내림차순으로 반환하고 요청한 순서는 따르지 않는다")
    void ordersByMatchRateIgnoringRequestOrder() {
      Channel fullMatch = matchingChannel("세 축 채널", List.of(MATCHED_AGE_BAND), "20대");
      Channel twoAxes = matchingChannel("두 축 채널", List.of(OTHER_AGE_BAND), "50대");
      givenCatalog(
          entry(fullMatch, MATCHED_OBJECTIVE, "3000"),
          entry(twoAxes, MATCHED_OBJECTIVE, "3000"));

      UUID userId = UUID.randomUUID();
      UUID onboardingId = UUID.randomUUID();
      given(onboardingRepository.findById(onboardingId))
          .willReturn(Optional.of(matchedOnboarding(userId)));

      ChannelComparisonResponse response = comparisonService.compare(
          List.of(twoAxes.getId(), fullMatch.getId()), onboardingId, userId);

      assertThat(response.items()).extracting(ChannelComparisonItemResponse::channelName)
          .containsExactly("세 축 채널", "두 축 채널");
      assertThat(response.items()).extracting(ChannelComparisonItemResponse::matchRate)
          .containsExactly(100, 78);
    }

    @Test
    @DisplayName("적합도가 같으면 집행 가능한 채널을 먼저, 그다음 매체명 순으로 정렬한다")
    void breaksTiesByExecutabilityThenName() {
      Channel expensive = matchingChannel("가매체", List.of(OTHER_AGE_BAND), "50대");
      Channel cheapEarlier = matchingChannel("다매체", List.of(OTHER_AGE_BAND), "50대");
      givenCatalog(
          entry(expensive, CampaignObjective.CONVERSION, "9000000"),
          entry(cheapEarlier, CampaignObjective.CONVERSION, "3000"));

      UUID userId = UUID.randomUUID();
      UUID onboardingId = UUID.randomUUID();
      given(onboardingRepository.findById(onboardingId))
          .willReturn(Optional.of(matchedOnboarding(userId)));

      ChannelComparisonResponse response = comparisonService.compare(
          List.of(expensive.getId(), cheapEarlier.getId()), onboardingId, userId);

      assertThat(response.items()).extracting(ChannelComparisonItemResponse::channelName)
          .containsExactly("다매체", "가매체");
      assertThat(response.items()).extracting(ChannelComparisonItemResponse::matchRate)
          .containsExactly(44, 44);
    }

    @Test
    @DisplayName("다른 사용자가 제출한 온보딩은 없는 것과 같은 404 로 숨긴다")
    void hidesOtherUsersOnboarding() {
      Channel channel = matchedChannel();
      ChannelProduct product = matchedProduct(channel);
      ChannelPricing pricing = pricing(product.getId(), PricingModel.CPM, "3000");
      givenCatalog(new CatalogEntry(channel, product, pricing));

      UUID ownerId = UUID.randomUUID();
      UUID strangerId = UUID.randomUUID();
      UUID onboardingId = UUID.randomUUID();
      Onboarding onboarding = matchedOnboarding(ownerId);
      given(onboardingRepository.findById(onboardingId)).willReturn(Optional.of(onboarding));
      List<UUID> channelIds = List.of(channel.getId());

      assertThatThrownBy(() -> comparisonService.compare(channelIds, onboardingId, strangerId))
          .isInstanceOf(OnboardingNotFoundException.class);
    }

    @Test
    @DisplayName("로그인 사용자가 제출한 온보딩은 비로그인 요청에 노출하지 않는다")
    void hidesOwnedOnboardingFromAnonymousUser() {
      UUID ownerId = UUID.randomUUID();
      UUID onboardingId = UUID.randomUUID();
      Onboarding onboarding = matchedOnboarding(ownerId);
      given(onboardingRepository.findById(onboardingId)).willReturn(Optional.of(onboarding));

      Channel channel = matchedChannel();
      given(channelRepository.findByIdAndActiveTrue(channel.getId()))
          .willReturn(Optional.of(channel));
      given(channelProductRepository.findByChannelIdIn(any())).willReturn(List.of());
      List<UUID> channelIds = List.of(channel.getId());

      assertThatThrownBy(() -> comparisonService.compare(channelIds, onboardingId, null))
          .isInstanceOf(OnboardingNotFoundException.class);
    }

    @Test
    @DisplayName("존재하지 않는 온보딩은 404 로 거부한다")
    void rejectsMissingOnboarding() {
      UUID onboardingId = UUID.randomUUID();
      given(onboardingRepository.findById(onboardingId)).willReturn(Optional.empty());

      Channel channel = matchedChannel();
      given(channelRepository.findByIdAndActiveTrue(channel.getId()))
          .willReturn(Optional.of(channel));
      given(channelProductRepository.findByChannelIdIn(any())).willReturn(List.of());
      List<UUID> channelIds = List.of(channel.getId());

      assertThatThrownBy(() -> comparisonService.compare(channelIds, onboardingId, null))
          .isInstanceOf(OnboardingNotFoundException.class);
    }

    @Test
    @DisplayName("온보딩이 없으면 비교 결과는 사용자가 선택한 채널 순서를 유지한다")
    void preservesRequestOrderWithoutOnboarding() {
      Channel first = channel(UUID.randomUUID(), "가매체");
      Channel second = channel(UUID.randomUUID(), "나매체");
      Channel third = channel(UUID.randomUUID(), "다매체");

      given(channelRepository.findByIdAndActiveTrue(first.getId()))
          .willReturn(Optional.of(first));
      given(channelRepository.findByIdAndActiveTrue(second.getId()))
          .willReturn(Optional.of(second));
      given(channelRepository.findByIdAndActiveTrue(third.getId()))
          .willReturn(Optional.of(third));
      given(channelProductRepository.findByChannelIdIn(any())).willReturn(List.of());

      ChannelComparisonResponse response = comparisonService.compare(
          List.of(third.getId(), first.getId(), second.getId()), null, null);

      assertThat(response.items()).extracting(ChannelComparisonItemResponse::channelName)
          .containsExactly("다매체", "가매체", "나매체");
    }
  }

  @Test
  @DisplayName("존재하지 않거나 비활성인 채널이 포함되면 404 로 거부한다")
  void rejectsMissingOrInactiveChannel() {
    UUID missingId = UUID.randomUUID();
    given(channelRepository.findByIdAndActiveTrue(missingId)).willReturn(Optional.empty());
    List<UUID> channelIds = List.of(missingId);

    assertThatThrownBy(() -> comparisonService.compare(channelIds, null, null))
        .isInstanceOf(ChannelNotFoundException.class);
  }

  /**
   * 비교할 채널과 각 채널의 단일 상품·단가를 조회하도록 준비한다.
   */
  private void givenCatalog(CatalogEntry... entries) {
    for (CatalogEntry entry : entries) {
      given(channelRepository.findByIdAndActiveTrue(entry.channel().getId()))
          .willReturn(Optional.of(entry.channel()));
    }
    given(channelProductRepository.findByChannelIdIn(any()))
        .willReturn(Arrays.stream(entries).map(CatalogEntry::product).toList());
    given(channelPricingRepository.findByChannelProductIdIn(any()))
        .willReturn(Arrays.stream(entries).map(CatalogEntry::pricing).toList());
  }

  private static CatalogEntry entry(Channel channel, CampaignObjective objective, String price) {
    ChannelProduct product =
        withObjectives(product(UUID.randomUUID(), channel.getId()), objective);
    return new CatalogEntry(channel, product, pricing(product.getId(), PricingModel.CPM, price));
  }

  /** 채널 하나와 그 채널의 단일 상품·단가. */
  private record CatalogEntry(Channel channel, ChannelProduct product, ChannelPricing pricing) {
  }

  /**
   * 업종·목적·연령이 모두 맞고 비교 화면에 필요한 카탈로그 값이 채워진 채널.
   */
  private static Channel matchedChannel() {
    return matchingChannel("매칭 채널", List.of(MATCHED_AGE_BAND), "20대");
  }

  private static Channel matchingChannel(String name, List<AgeBand> ageBandCodes,
      String primaryAgeBand) {
    Channel channel = channel(UUID.randomUUID(), name, List.of(MATCHED_INDUSTRY), ageBandCodes,
        primaryAgeBand, Gender.FEMALE);
    ReflectionTestUtils.setField(channel, "audienceSummary", "20대 여성");
    ReflectionTestUtils.setField(channel, "adFormats", List.of("배너"));
    ReflectionTestUtils.setField(channel, "targetingMethods", List.of("관심사"));
    ReflectionTestUtils.setField(channel, "minBudgetWon", 1_000_000);
    ReflectionTestUtils.setField(channel, "advantages", List.of("빠른 노출"));
    return channel;
  }

  /**
   * 온보딩의 광고 목적을 지원하는 채널 상품.
   */
  private static ChannelProduct matchedProduct(Channel channel) {
    return withObjectives(product(UUID.randomUUID(), channel.getId()), MATCHED_OBJECTIVE);
  }

  /**
   * 업종·목적·연령이 모두 맞는 온보딩. 예산 상한 300만원, 기간 1개월.
   */
  private static Onboarding matchedOnboarding(UUID userId) {
    return OnboardingFixture.onboarding(userId, MATCHED_INDUSTRY, MATCHED_OBJECTIVE,
        List.of(MATCHED_AGE_BAND), 1_000_000L, BUDGET_MAX, CampaignPeriod.M1);
  }
}
