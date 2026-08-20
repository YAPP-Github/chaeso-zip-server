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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
import chaeso.zip.server.comparison.application.dto.ChannelComparisonSummaryResponse;
import chaeso.zip.server.comparison.application.dto.SavedChannelComparisonResponse;
import chaeso.zip.server.comparison.domain.ChannelComparisonNotFoundException;
import chaeso.zip.server.comparison.domain.entity.ChannelComparison;
import chaeso.zip.server.comparison.domain.entity.ChannelComparisonItem;
import chaeso.zip.server.comparison.domain.repository.ChannelComparisonItemRepository;
import chaeso.zip.server.comparison.domain.repository.ChannelComparisonRepository;
import chaeso.zip.server.estimation.application.DefaultCtrProvider;
import chaeso.zip.server.estimation.application.dto.CountRangeResponse;
import chaeso.zip.server.estimation.domain.EstimationService;
import chaeso.zip.server.onboarding.domain.OnboardingNotFoundException;
import chaeso.zip.server.onboarding.domain.entity.Onboarding;
import chaeso.zip.server.onboarding.domain.repository.OnboardingRepository;
import chaeso.zip.server.onboarding.domain.vo.CampaignPeriod;
import chaeso.zip.server.support.OnboardingFixture;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
  private ChannelComparisonRepository channelComparisonRepository;
  @Mock
  private ChannelComparisonItemRepository channelComparisonItemRepository;
  @Mock
  private DefaultCtrProvider defaultCtrProvider;

  private ChannelComparisonServiceImpl comparisonService;

  @BeforeEach
  void setUp() {
    comparisonService = new ChannelComparisonServiceImpl(channelRepository,
        channelProductRepository, channelPricingRepository, onboardingRepository,
        channelComparisonRepository, channelComparisonItemRepository, defaultCtrProvider);
    lenient().when(defaultCtrProvider.averageCtrPercent()).thenReturn(AVERAGE_CTR);
  }

  @Nested
  @DisplayName("채널 카탈로그 기준 비교")
  class WithoutOnboarding {

    @Test
    @DisplayName("비로그인 요청은 주요 오디언스와 광고 형태, 타기팅, 예상 노출/클릭을 고정 MOCK 값으로 채우고 적합도는 null이다")
    void mocksCatalogDetailsForAnonymousRequest() {
      Channel channel = matchedChannel();
      ReflectionTestUtils.setField(channel, "defaultTags",
          List.of("정적태그1", "정적태그2", "정적태그3"));
      ReflectionTestUtils.setField(channel, "advantages",
          List.of("빠른 노출1", "빠른 노출2", "빠른 노출3", "빠른 노출4"));
      ChannelProduct product = matchedProduct(channel);
      ChannelPricing cpmPricing = pricing(product.getId(), PricingModel.CPM, "3000");
      givenCatalog(new CatalogEntry(channel, product, cpmPricing));

      ChannelComparisonItemResponse item = comparisonService
          .compare(List.of(channel.getId()), null, null)
          .items().getFirst();

      assertThat(item.channelId()).isEqualTo(channel.getId());
      assertThat(item.channelName()).isEqualTo(channel.getName());
      assertThat(item.audienceSummary()).isEqualTo("20~30대");
      assertThat(item.adFormats()).containsExactly("배너", "네이티브");
      assertThat(item.targetingMethods()).containsExactly("키워드", "리타겟팅");
      assertThat(item.minBudgetWon()).isEqualTo(channel.getMinBudgetWon());
      // advantages는 4개지만 장점은 최대 3개까지만 잘라서 준다.
      assertThat(item.advantages()).containsExactly("빠른 노출1", "빠른 노출2", "빠른 노출3");
      // defaultTags는 3개지만 태그는 최대 2개까지만 잘라서 준다.
      assertThat(item.tags()).containsExactly("정적태그1", "정적태그2");
      assertThat(item.cpmWon()).isEqualByComparingTo("3000");
      assertThat(item.matchRate()).isNull();
      assertThat(item.estImpressions()).isEqualTo(new CountRangeResponse(40_000, 60_000));
      assertThat(item.estClicks()).isEqualTo(new CountRangeResponse(400, 600));
    }

    @Test
    @DisplayName("로그인한 요청은 온보딩이 없어도 채널 정보와 태그(최대 2개)를 준다")
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
      assertThat(item.tags()).containsExactly("정적태그1", "정적태그2");
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

      given(channelRepository.findAllById(anyList()))
          .willReturn(List.of(cpmChannel, cpcChannel));
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

    @Test
    @DisplayName("로그인 + 온보딩 없음은 등록된 상품이 없으면 단가와 예상 노출/클릭을 노출하지 않는다")
    void leavesEverythingEmptyWithoutProductWhenLoggedInWithoutOnboarding() {
      Channel channel = matchedChannel();
      given(channelRepository.findAllById(anyList())).willReturn(List.of(channel));
      given(channelProductRepository.findByChannelIdIn(any())).willReturn(List.of());

      ChannelComparisonItemResponse item = comparisonService
          .compare(List.of(channel.getId()), null, UUID.randomUUID())
          .items().getFirst();

      assertThat(item.cpcWon()).isNull();
      assertThat(item.cpmWon()).isNull();
      assertThat(item.estImpressions()).isNull();
      assertThat(item.estClicks()).isNull();
    }
  }

  @Nested
  @DisplayName("온보딩 기준 맞춤 비교")
  class WithOnboarding {

    @Test
    @DisplayName("비로그인 요청은 예상 노출·클릭·오디언스·광고형태·타기팅을 고정 MOCK 값으로 채우고 적합도는 null이다")
    void mocksCatalogDetailsForAnonymousOnboarding() {
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
      assertThat(item.estImpressions()).isEqualTo(new CountRangeResponse(40_000, 60_000));
      assertThat(item.estClicks()).isEqualTo(new CountRangeResponse(400, 600));
      assertThat(item.audienceSummary()).isEqualTo("20~30대");
      assertThat(item.adFormats()).containsExactly("배너", "네이티브");
      assertThat(item.targetingMethods()).containsExactly("키워드", "리타겟팅");
      assertThat(item.tags()).containsExactly("빠른매칭", "안정노출");
      assertThat(item.advantages()).containsExactly("빠른 노출");
      assertThat(item.cpmWon()).isEqualByComparingTo("3000");
      assertThat(item.cpcWon()).isNotNull();
    }

    @Test
    @DisplayName("비로그인 다건 비교는 채널별로 다른 MOCK 값을 매긴다")
    void mocksDistinctProfilesByPositionForAnonymousMultiChannel() {
      Channel first = matchingChannel("가매체", List.of(MATCHED_AGE_BAND), "20대");
      Channel second = matchingChannel("나매체", List.of(OTHER_AGE_BAND), "50대");
      Channel third = matchingChannel("다매체", List.of(OTHER_AGE_BAND), "50대");
      givenCatalog(
          entry(first, MATCHED_OBJECTIVE, "3000"),
          entry(second, MATCHED_OBJECTIVE, "3000"),
          entry(third, MATCHED_OBJECTIVE, "3000"));

      UUID onboardingId = UUID.randomUUID();
      given(onboardingRepository.findById(onboardingId))
          .willReturn(Optional.of(matchedOnboarding(null)));

      List<ChannelComparisonItemResponse> items = comparisonService
          .compare(List.of(first.getId(), second.getId(), third.getId()), onboardingId, null)
          .items();

      assertThat(items).extracting(ChannelComparisonItemResponse::matchRate)
          .containsExactly(null, null, null);
      assertThat(items).extracting(ChannelComparisonItemResponse::audienceSummary)
          .containsExactly("20~30대", "30~40대", "전 연령");
    }

    @Test
    @DisplayName("비로그인 + 익명 온보딩은 실제 적합도 순위와 무관하게 요청 순서를 유지한다")
    void preservesRequestOrderForAnonymousOnboardingRegardlessOfActualMatchScore() {
      Channel lowScore = matchingChannel("낮은적합도매체", List.of(OTHER_AGE_BAND), "50대");
      Channel highScore = matchingChannel("높은적합도매체", List.of(MATCHED_AGE_BAND), "20대");
      givenCatalog(
          entry(lowScore, MATCHED_OBJECTIVE, "3000"),
          entry(highScore, MATCHED_OBJECTIVE, "3000"));

      UUID onboardingId = UUID.randomUUID();
      given(onboardingRepository.findById(onboardingId))
          .willReturn(Optional.of(matchedOnboarding(null)));

      // 실제 적합도는 highScore가 더 높지만, 비로그인 요청은 요청 순서(lowScore, highScore)를 그대로 유지해야 한다.
      List<ChannelComparisonItemResponse> items = comparisonService
          .compare(List.of(lowScore.getId(), highScore.getId()), onboardingId, null)
          .items();

      assertThat(items).extracting(ChannelComparisonItemResponse::channelName)
          .containsExactly("낮은적합도매체", "높은적합도매체");
      assertThat(items).extracting(ChannelComparisonItemResponse::matchRate)
          .containsExactly(null, null);
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
      ReflectionTestUtils.setField(channel, "defaultTags", List.of("빠른 배송", "쉬운 정산"));
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
      assertThat(item.tags()).containsExactly("빠른 배송", "쉬운 정산");
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
      given(channelRepository.findAllById(anyList())).willReturn(List.of(channel));
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
      given(channelRepository.findAllById(anyList())).willReturn(List.of(channel));
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
      given(channelRepository.findAllById(anyList())).willReturn(List.of(channel));
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

      given(channelRepository.findAllById(anyList()))
          .willReturn(List.of(first, second, third));
      given(channelProductRepository.findByChannelIdIn(any())).willReturn(List.of());

      ChannelComparisonResponse response = comparisonService.compare(
          List.of(third.getId(), first.getId(), second.getId()), null, null);

      assertThat(response.items()).extracting(ChannelComparisonItemResponse::channelName)
          .containsExactly("다매체", "가매체", "나매체");
    }
  }

  /**
   * 정렬 정책(요청순 vs 적합도순)이 로그인 × 온보딩 4가지 조합 전체에서 의도대로 유지되는지
   * 한 곳에서 검증한다. 각 케이스를 개별 테스트로 흩어 두면, 실제 적합도 순위와 요청 순서가
   * 우연히 같은 데이터로는 정렬 로직 회귀(예: 비로그인인데 적합도순 정렬)를 잡아내지 못한다.
   */
  @Nested
  @DisplayName("정렬 정책: 로그인 × 온보딩 매트릭스")
  class OrderingMatrix {

    @ParameterizedTest(name = "loggedIn={0}, onboarding={1} → 첫 채널: {2}")
    @MethodSource("chaeso.zip.server.comparison.application.ChannelComparisonServiceImplTest#orderingCases")
    void ordersByLoginAndOnboardingCombination(boolean loggedIn, boolean hasOnboarding,
        String expectedFirstChannel) {
      // 요청 순서(낮은적합도매체, 높은적합도매체)와 실제 적합도 순위를 일부러 반대로 둬서,
      // 정렬 로직이 실제로 동작하는지와 동작하지 말아야 할 때 동작하지 않는지를 함께 확인한다.
      Channel lowScore = matchingChannel("낮은적합도매체", List.of(OTHER_AGE_BAND), "50대");
      Channel highScore = matchingChannel("높은적합도매체", List.of(MATCHED_AGE_BAND), "20대");
      givenCatalog(
          entry(lowScore, MATCHED_OBJECTIVE, "3000"),
          entry(highScore, MATCHED_OBJECTIVE, "3000"));

      UUID requesterId = loggedIn ? UUID.randomUUID() : null;
      UUID onboardingId = null;
      if (hasOnboarding) {
        onboardingId = UUID.randomUUID();
        // 익명 온보딩: 로그인 여부와 무관하게 findAccessibleOnboarding을 통과한다.
        given(onboardingRepository.findById(onboardingId))
            .willReturn(Optional.of(matchedOnboarding(null)));
      }

      List<ChannelComparisonItemResponse> items = comparisonService
          .compare(List.of(lowScore.getId(), highScore.getId()), onboardingId, requesterId)
          .items();

      assertThat(items.getFirst().channelName()).isEqualTo(expectedFirstChannel);
    }
  }

  /** {@link OrderingMatrix#ordersByLoginAndOnboardingCombination}용 (loggedIn, hasOnboarding, 예상 1위 채널). */
  static Stream<Arguments> orderingCases() {
    return Stream.of(
        Arguments.of(false, false, "낮은적합도매체"), // 비로그인, 온보딩 없음 → 요청순
        Arguments.of(true, false, "낮은적합도매체"), // 로그인, 온보딩 없음 → 요청순
        Arguments.of(false, true, "낮은적합도매체"), // 비로그인, 온보딩 있음(익명) → 요청순
        Arguments.of(true, true, "높은적합도매체")   // 로그인, 온보딩 있음 → 적합도순
    );
  }

  @Nested
  @DisplayName("비교 결과 저장")
  class Save {

    private static final UUID COMPARISON_ID = UUID.randomUUID();

    @BeforeEach
    void stubComparisonSave() {
      lenient().when(channelComparisonRepository.save(any())).thenAnswer(invocation -> {
        ChannelComparison comparison = invocation.getArgument(0);
        ReflectionTestUtils.setField(comparison, "id", COMPARISON_ID);
        return comparison;
      });
    }

    @Test
    @DisplayName("온보딩 없이 저장하면 서비스명을 담고 요청 순서 그대로 저장한다")
    void savesWithServiceNameInRequestOrder() {
      Channel first = channel(UUID.randomUUID(), "가매체");
      Channel second = channel(UUID.randomUUID(), "나매체");
      ReflectionTestUtils.setField(first, "iconUrl", "https://cdn/first.png");

      given(channelRepository.findAllById(anyList()))
          .willReturn(List.of(first, second));
      given(channelProductRepository.findByChannelIdIn(any())).willReturn(List.of());

      UUID userId = UUID.randomUUID();
      SavedChannelComparisonResponse response = comparisonService.save(userId,
          List.of(second.getId(), first.getId()), null, "채소집");

      assertThat(response.comparisonId()).isEqualTo(COMPARISON_ID);
      assertThat(response.items()).extracting(ChannelComparisonItemResponse::channelName)
          .containsExactly("나매체", "가매체");

      ArgumentCaptor<ChannelComparison> comparisonCaptor =
          ArgumentCaptor.forClass(ChannelComparison.class);
      verify(channelComparisonRepository).save(comparisonCaptor.capture());
      assertThat(comparisonCaptor.getValue().getUserId()).isEqualTo(userId);
      assertThat(comparisonCaptor.getValue().getOnboardingId()).isNull();
      assertThat(comparisonCaptor.getValue().getServiceName()).isEqualTo("채소집");

      ArgumentCaptor<List<ChannelComparisonItem>> itemsCaptor = ArgumentCaptor.captor();
      verify(channelComparisonItemRepository).saveAll(itemsCaptor.capture());
      assertThat(itemsCaptor.getValue()).extracting(ChannelComparisonItem::getSortOrder)
          .containsExactly(1, 2);
      ChannelComparisonItem savedFirst = itemsCaptor.getValue().get(1);
      assertThat(savedFirst.getChannelId()).isEqualTo(first.getId());
      assertThat(savedFirst.getChannelName()).isEqualTo("가매체");
      assertThat(savedFirst.getIconUrlSnap()).isEqualTo("https://cdn/first.png");
      assertThat(savedFirst.getMatchRate()).isNull();
      assertThat(savedFirst.getComparisonId()).isEqualTo(COMPARISON_ID);
    }

    @Test
    @DisplayName("온보딩으로 저장하면 서비스명은 무시하고 적합도순으로 정렬해 저장한다")
    void savesWithOnboardingOrderedByMatchRate() {
      Channel fullMatch = matchingChannel("세 축 채널", List.of(MATCHED_AGE_BAND), "20대");
      Channel twoAxes = matchingChannel("두 축 채널", List.of(OTHER_AGE_BAND), "50대");
      givenCatalog(
          entry(fullMatch, MATCHED_OBJECTIVE, "3000"),
          entry(twoAxes, MATCHED_OBJECTIVE, "3000"));

      UUID userId = UUID.randomUUID();
      UUID onboardingId = UUID.randomUUID();
      given(onboardingRepository.findById(onboardingId))
          .willReturn(Optional.of(matchedOnboarding(userId)));

      SavedChannelComparisonResponse response = comparisonService.save(userId,
          List.of(twoAxes.getId(), fullMatch.getId()), onboardingId, "무시될 서비스명");

      assertThat(response.items()).extracting(ChannelComparisonItemResponse::channelName)
          .containsExactly("세 축 채널", "두 축 채널");

      ArgumentCaptor<ChannelComparison> comparisonCaptor =
          ArgumentCaptor.forClass(ChannelComparison.class);
      verify(channelComparisonRepository).save(comparisonCaptor.capture());
      assertThat(comparisonCaptor.getValue().getOnboardingId()).isEqualTo(onboardingId);
      assertThat(comparisonCaptor.getValue().getServiceName()).isNull();

      ArgumentCaptor<List<ChannelComparisonItem>> itemsCaptor = ArgumentCaptor.captor();
      verify(channelComparisonItemRepository).saveAll(itemsCaptor.capture());
      assertThat(itemsCaptor.getValue()).extracting(ChannelComparisonItem::getSortOrder)
          .containsExactly(1, 2);
      assertThat(itemsCaptor.getValue()).extracting(ChannelComparisonItem::getMatchRate)
          .containsExactly(100, 78);
    }

    @Test
    @DisplayName("존재하지 않거나 비활성인 채널이 포함되면 404 로 거부한다")
    void rejectsMissingChannel() {
      UUID missingId = UUID.randomUUID();
      given(channelRepository.findAllById(anyList())).willReturn(List.of());
      UUID userId = UUID.randomUUID();
      List<UUID> channelIds = List.of(missingId);

      assertThatThrownBy(() -> comparisonService.save(userId, channelIds, null, "서비스명"))
          .isInstanceOf(ChannelNotFoundException.class);
    }

    @Test
    @DisplayName("다른 사용자가 제출한 온보딩으로는 저장할 수 없다")
    void rejectsOtherUsersOnboarding() {
      Channel channel = matchedChannel();
      ChannelProduct product = matchedProduct(channel);
      ChannelPricing pricing = pricing(product.getId(), PricingModel.CPM, "3000");
      givenCatalog(new CatalogEntry(channel, product, pricing));

      UUID ownerId = UUID.randomUUID();
      UUID strangerId = UUID.randomUUID();
      UUID onboardingId = UUID.randomUUID();
      given(onboardingRepository.findById(onboardingId))
          .willReturn(Optional.of(matchedOnboarding(ownerId)));
      List<UUID> channelIds = List.of(channel.getId());

      assertThatThrownBy(
          () -> comparisonService.save(strangerId, channelIds, onboardingId, null))
          .isInstanceOf(OnboardingNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("저장된 비교 조회")
  class FindComparison {

    @Test
    @DisplayName("본인이 저장한 비교는 정렬 순서 그대로 스냅샷을 반환한다")
    void returnsSavedSnapshotInSortOrder() {
      UUID userId = UUID.randomUUID();
      UUID comparisonId = UUID.randomUUID();
      ChannelComparison comparison = ChannelComparison.builder()
          .userId(userId)
          .onboardingId(null)
          .serviceName("채소집")
          .build();
      ReflectionTestUtils.setField(comparison, "id", comparisonId);
      given(channelComparisonRepository.findById(comparisonId))
          .willReturn(Optional.of(comparison));

      ChannelComparisonItem item = ChannelComparisonItem.builder()
          .comparisonId(comparisonId)
          .channelId(UUID.randomUUID())
          .sortOrder(1)
          .channelName("가매체")
          .matchRate(78)
          .build();
      given(channelComparisonItemRepository.findByComparisonIdOrderBySortOrderAsc(comparisonId))
          .willReturn(List.of(item));

      SavedChannelComparisonResponse response =
          comparisonService.findComparison(userId, comparisonId);

      assertThat(response.comparisonId()).isEqualTo(comparisonId);
      assertThat(response.items()).extracting(ChannelComparisonItemResponse::channelName)
          .containsExactly("가매체");
      assertThat(response.items().getFirst().matchRate()).isEqualTo(78);
      assertThat(response.items().getFirst().estImpressions()).isNull();
      assertThat(response.items().getFirst().estClicks()).isNull();
    }

    @Test
    @DisplayName("다른 사용자가 저장한 비교는 404 로 응답한다")
    void hidesOtherUsersComparison() {
      UUID ownerId = UUID.randomUUID();
      UUID strangerId = UUID.randomUUID();
      UUID comparisonId = UUID.randomUUID();
      ChannelComparison comparison = ChannelComparison.builder()
          .userId(ownerId)
          .build();
      ReflectionTestUtils.setField(comparison, "id", comparisonId);
      given(channelComparisonRepository.findById(comparisonId))
          .willReturn(Optional.of(comparison));

      assertThatThrownBy(() -> comparisonService.findComparison(strangerId, comparisonId))
          .isInstanceOf(ChannelComparisonNotFoundException.class);
    }

    @Test
    @DisplayName("존재하지 않는 비교는 404 로 응답한다")
    void rejectsMissingComparison() {
      UUID userId = UUID.randomUUID();
      UUID comparisonId = UUID.randomUUID();
      given(channelComparisonRepository.findById(comparisonId)).willReturn(Optional.empty());

      assertThatThrownBy(() -> comparisonService.findComparison(userId, comparisonId))
          .isInstanceOf(ChannelComparisonNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("내가 저장한 채널 비교 목록 조회")
  class FindMyComparisons {

    private final Pageable pageable = PageRequest.of(0, 5);
    private final LocalDateTime createdAt = LocalDateTime.of(2026, Month.MARCH, 14, 10, 22, 31);

    @Test
    @DisplayName("온보딩 기반 저장이면 온보딩의 서비스명을 반환하고 매체명은 저장 순서(추천순) 그대로 준다")
    void resolvesServiceNameFromOnboarding() {
      UUID comparisonId = UUID.randomUUID();
      UUID onboardingId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      givenMyPage(userId, savedComparison(comparisonId, userId, onboardingId, null));
      given(channelComparisonItemRepository
          .findByComparisonIdInOrderBySortOrderAsc(List.of(comparisonId)))
          .willReturn(List.of(
              savedItem(comparisonId, 1, "세 축 채널"),
              savedItem(comparisonId, 2, "두 축 채널")));
      Onboarding onboarding = matchedOnboarding(userId);
      ReflectionTestUtils.setField(onboarding, "id", onboardingId);
      given(onboardingRepository.findAllById(List.of(onboardingId)))
          .willReturn(List.of(onboarding));

      ChannelComparisonSummaryResponse summary =
          comparisonService.findMyComparisons(userId, pageable).getContent().getFirst();

      assertThat(summary.id()).isEqualTo(comparisonId);
      assertThat(summary.serviceName()).isEqualTo(onboarding.getServiceName());
      assertThat(summary.createdAt()).isEqualTo(createdAt);
      assertThat(summary.channelNames()).containsExactly("세 축 채널", "두 축 채널");
    }

    @Test
    @DisplayName("온보딩 없이 저장한 비교는 저장 당시 입력한 서비스명을 그대로 반환한다")
    void keepsOwnServiceNameWithoutOnboarding() {
      UUID comparisonId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      givenMyPage(userId, savedComparison(comparisonId, userId, null, "채소집"));
      given(channelComparisonItemRepository
          .findByComparisonIdInOrderBySortOrderAsc(List.of(comparisonId)))
          .willReturn(List.of(savedItem(comparisonId, 1, "가매체")));

      ChannelComparisonSummaryResponse summary =
          comparisonService.findMyComparisons(userId, pageable).getContent().getFirst();

      assertThat(summary.serviceName()).isEqualTo("채소집");
      assertThat(summary.channelNames()).containsExactly("가매체");
      verifyNoInteractions(onboardingRepository);
    }

    @Test
    @DisplayName("저장된 결과가 없으면 빈 페이지를 반환하고 항목·온보딩은 조회하지 않는다")
    void returnsEmptyPageWithoutLoadingItems() {
      UUID userId = UUID.randomUUID();
      givenMyPage(userId);

      assertThat(comparisonService.findMyComparisons(userId, pageable)).isEmpty();

      verifyNoInteractions(onboardingRepository);
      verify(channelComparisonItemRepository, never())
          .findByComparisonIdInOrderBySortOrderAsc(anyList());
    }

    private void givenMyPage(UUID userId, ChannelComparison... comparisons) {
      given(channelComparisonRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable))
          .willReturn(new PageImpl<>(List.of(comparisons), pageable, comparisons.length));
    }

    private ChannelComparison savedComparison(UUID id, UUID userId, UUID onboardingId,
        String serviceName) {
      ChannelComparison comparison = ChannelComparison.builder()
          .userId(userId)
          .onboardingId(onboardingId)
          .serviceName(serviceName)
          .build();
      ReflectionTestUtils.setField(comparison, "id", id);
      ReflectionTestUtils.setField(comparison, "createdAt", createdAt);
      return comparison;
    }

    private ChannelComparisonItem savedItem(UUID comparisonId, int sortOrder, String channelName) {
      return ChannelComparisonItem.builder()
          .comparisonId(comparisonId)
          .channelId(UUID.randomUUID())
          .sortOrder(sortOrder)
          .channelName(channelName)
          .build();
    }
  }

  @Test
  @DisplayName("존재하지 않거나 비활성인 채널이 포함되면 404 로 거부한다")
  void rejectsMissingOrInactiveChannel() {
    UUID missingId = UUID.randomUUID();
    given(channelRepository.findAllById(anyList())).willReturn(List.of());
    List<UUID> channelIds = List.of(missingId);

    assertThatThrownBy(() -> comparisonService.compare(channelIds, null, null))
        .isInstanceOf(ChannelNotFoundException.class);
  }

  /**
   * 비교할 채널과 각 채널의 단일 상품·단가를 조회하도록 준비한다.
   */
  private void givenCatalog(CatalogEntry... entries) {
    given(channelRepository.findAllById(anyList()))
        .willReturn(Arrays.stream(entries).map(CatalogEntry::channel).toList());
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
