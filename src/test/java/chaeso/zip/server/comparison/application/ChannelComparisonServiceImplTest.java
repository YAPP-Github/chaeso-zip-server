package chaeso.zip.server.comparison.application;

import static chaeso.zip.server.support.ChannelCatalogFixture.channel;
import static chaeso.zip.server.support.ChannelCatalogFixture.pricing;
import static chaeso.zip.server.support.ChannelCatalogFixture.product;
import static chaeso.zip.server.support.ChannelCatalogFixture.withObjectives;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

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
import chaeso.zip.server.onboarding.domain.OnboardingNotFoundException;
import chaeso.zip.server.onboarding.domain.entity.Onboarding;
import chaeso.zip.server.onboarding.domain.repository.OnboardingRepository;
import chaeso.zip.server.onboarding.domain.vo.CampaignPeriod;
import chaeso.zip.server.support.OnboardingFixture;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChannelComparisonServiceImplTest {

  private static final Category MATCHED_INDUSTRY = Category.MEDICAL_HEALTHCARE;
  private static final CampaignObjective MATCHED_OBJECTIVE = CampaignObjective.AWARENESS;
  private static final AgeBand MATCHED_AGE_BAND = AgeBand.AGE_20S;

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
    comparisonService = new ChannelComparisonServiceImpl(channelRepository, channelProductRepository,
        channelPricingRepository, onboardingRepository, defaultCtrProvider);
    lenient().when(defaultCtrProvider.averageCtrPercent()).thenReturn(AVERAGE_CTR);
  }

  @Nested
  @DisplayName("채널 카탈로그 기준 비교")
  class WithoutOnboarding {

    @Test
    @DisplayName("온보딩이 없으면 채널 정보와 기본 태그 전체를 반환하고 맞춤 지표는 비워 둔다")
    void returnsCatalogFieldsWithoutPersonalization() {
      Channel channel = matchedChannel();
      ReflectionTestUtils.setField(channel, "defaultTags",
          List.of("정적태그1", "정적태그2", "정적태그3"));
      ChannelProduct product = matchedProduct(channel);
      ChannelPricing cpmPricing = pricing(product.getId(), PricingModel.CPM, "3000");
      givenCatalog(channel, product, cpmPricing);

      ChannelComparisonResponse guestResponse =
          comparisonService.compare(List.of(channel.getId()), null, null);
      ChannelComparisonResponse loggedInResponse =
          comparisonService.compare(List.of(channel.getId()), null, UUID.randomUUID());

      for (ChannelComparisonResponse response : List.of(guestResponse, loggedInResponse)) {
        ChannelComparisonItemResponse item = response.items().get(0);
        assertThat(item.channelId()).isEqualTo(channel.getId());
        assertThat(item.channelName()).isEqualTo(channel.getName());
        assertThat(item.audienceSummary()).isEqualTo(channel.getAudienceSummary());
        assertThat(item.adFormats()).isEqualTo(channel.getAdFormats());
        assertThat(item.targetingMethods()).isEqualTo(channel.getTargetingMethods());
        assertThat(item.minBudgetWon()).isEqualTo(channel.getMinBudgetWon());
        assertThat(item.advantages()).containsExactly("빠른 노출");
        assertThat(item.tags()).containsExactly("정적태그1", "정적태그2", "정적태그3");
        assertThat(item.matchRate()).isNull();
        assertThat(item.estImpressions()).isNull();
        assertThat(item.estClicks()).isNull();
      }
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
  }

  @Nested
  @DisplayName("온보딩 기준 맞춤 비교")
  class WithOnboarding {

    @Test
    @DisplayName("게스트 온보딩도 저장된 추천 없이 실시간 계산하고 매칭 태그는 최대 2개만 반환한다")
    void allowsGuestOnboardingWithoutLogin() {
      Channel channel = matchedChannel();
      ReflectionTestUtils.setField(channel, "defaultTags", List.of("빠른매칭", "안정노출"));
      ChannelProduct product = matchedProduct(channel);
      ChannelPricing pricing = pricing(product.getId(), PricingModel.CPM, "3000");
      givenCatalog(channel, product, pricing);

      UUID onboardingId = UUID.randomUUID();
      Onboarding guestOnboarding = matchedOnboarding(null);
      given(onboardingRepository.findById(onboardingId)).willReturn(Optional.of(guestOnboarding));

      ChannelComparisonResponse response =
          comparisonService.compare(List.of(channel.getId()), onboardingId, null);

      ChannelComparisonItemResponse item = response.items().get(0);
      assertThat(item.matchRate()).isEqualTo(100);
      assertThat(item.tags()).containsExactly("CATEGORY", "OBJECTIVE");
      assertThat(item.advantages()).containsExactly("빠른 노출");
    }

    @Test
    @DisplayName("온보딩 예산으로 CPM 채널의 클릭당 비용을 환산하고 최소 예산은 카탈로그 값을 유지한다")
    void convertsCpmToCpcAndKeepsCatalogMinBudget() {
      Channel channel = matchedChannel();
      ChannelProduct product = matchedProduct(channel);
      ChannelPricing cpmPricing = pricing(product.getId(), PricingModel.CPM, "3000");
      givenCatalog(channel, product, cpmPricing);

      UUID onboardingId = UUID.randomUUID();
      Onboarding onboarding = matchedOnboarding(null);
      given(onboardingRepository.findById(onboardingId)).willReturn(Optional.of(onboarding));

      ChannelComparisonResponse response =
          comparisonService.compare(List.of(channel.getId()), onboardingId, null);

      ChannelComparisonItemResponse item = response.items().getFirst();
      assertThat(item.cpmWon()).isEqualByComparingTo("3000");
      assertThat(item.cpcWon()).isNotNull();
      assertThat(item.minBudgetWon()).isEqualTo(1_000_000);
    }

    @Test
    @DisplayName("온보딩 예산이 대표 단가보다 적으면 예상 노출·클릭 수와 환산 CPC를 비우고 고정 CPM은 유지한다")
    void leavesEstimatesEmptyWhenBudgetIsBelowRepresentativePrice() {
      Channel channel = matchedChannel();
      ChannelProduct product = matchedProduct(channel);
      ChannelPricing cpmPricing = pricing(product.getId(), PricingModel.CPM, "5000000");
      givenCatalog(channel, product, cpmPricing);

      UUID onboardingId = UUID.randomUUID();
      given(onboardingRepository.findById(onboardingId))
          .willReturn(Optional.of(matchedOnboarding(null)));

      ChannelComparisonItemResponse item = comparisonService
          .compare(List.of(channel.getId()), onboardingId, null)
          .items().getFirst();

      assertThat(item.matchRate()).isEqualTo(100);
      assertThat(item.tags()).containsExactly("CATEGORY", "OBJECTIVE");
      assertThat(item.cpmWon()).isEqualByComparingTo("5000000");
      assertThat(item.cpcWon()).isNull();
      assertThat(item.estImpressions()).isNull();
      assertThat(item.estClicks()).isNull();
    }

    @Test
    @DisplayName("등록된 상품이 없어 예상 노출·클릭 수를 계산할 수 없으면 적합도만 반환한다")
    void leavesEstimatesEmptyWithoutProduct() {
      Channel channel = matchedChannel();
      given(channelRepository.findByIdAndActiveTrue(channel.getId()))
          .willReturn(Optional.of(channel));
      given(channelProductRepository.findByChannelIdIn(any())).willReturn(List.of());

      UUID onboardingId = UUID.randomUUID();
      given(onboardingRepository.findById(onboardingId))
          .willReturn(Optional.of(matchedOnboarding(null)));

      ChannelComparisonItemResponse item = comparisonService
          .compare(List.of(channel.getId()), onboardingId, null)
          .items().get(0);

      assertThat(item.minBudgetWon()).isEqualTo(1_000_000);
      assertThat(item.estImpressions()).isNull();
      assertThat(item.estClicks()).isNull();
    }

    @Test
    @DisplayName("자신이 제출한 온보딩으로 맞춤 비교할 수 있다")
    void allowsOwnedOnboarding() {
      Channel channel = matchedChannel();
      ChannelProduct product = matchedProduct(channel);
      ChannelPricing pricing = pricing(product.getId(), PricingModel.CPM, "3000");
      givenCatalog(channel, product, pricing);

      UUID userId = UUID.randomUUID();
      UUID onboardingId = UUID.randomUUID();
      Onboarding onboarding = matchedOnboarding(userId);
      given(onboardingRepository.findById(onboardingId)).willReturn(Optional.of(onboarding));

      ChannelComparisonResponse response =
          comparisonService.compare(List.of(channel.getId()), onboardingId, userId);

      assertThat(response.items().getFirst().matchRate()).isEqualTo(100);
    }

    @Test
    @DisplayName("다른 사용자가 제출한 온보딩은 없는 것과 같은 404 로 숨긴다")
    void hidesOtherUsersOnboarding() {
      Channel channel = matchedChannel();
      ChannelProduct product = matchedProduct(channel);
      ChannelPricing pricing = pricing(product.getId(), PricingModel.CPM, "3000");
      givenCatalog(channel, product, pricing);

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
    @DisplayName("비교 결과는 사용자가 선택한 채널 순서를 유지한다")
    void preservesRequestOrder() {
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
   * 채널 하나와 그 채널의 단일 상품·단가를 조회하도록 준비한다.
   */
  private void givenCatalog(Channel channel, ChannelProduct product, ChannelPricing pricing) {
    given(channelRepository.findByIdAndActiveTrue(channel.getId()))
        .willReturn(Optional.of(channel));
    given(channelProductRepository.findByChannelIdIn(any())).willReturn(List.of(product));
    given(channelPricingRepository.findByChannelProductIdIn(any())).willReturn(List.of(pricing));
  }

  /**
   * 업종·목적·연령이 모두 맞고 비교 화면에 필요한 카탈로그 값이 채워진 채널.
   */
  private static Channel matchedChannel() {
    Channel channel = channel(UUID.randomUUID(), "매칭 채널", List.of(MATCHED_INDUSTRY),
        List.of(MATCHED_AGE_BAND), "20대", Gender.FEMALE);
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
