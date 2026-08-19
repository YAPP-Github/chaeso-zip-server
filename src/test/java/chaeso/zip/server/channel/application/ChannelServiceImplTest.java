package chaeso.zip.server.channel.application;

import static chaeso.zip.server.support.ChannelCatalogFixture.audienceMetric;
import static chaeso.zip.server.support.ChannelCatalogFixture.channel;
import static chaeso.zip.server.support.ChannelCatalogFixture.channelWithDefaultTags;
import static chaeso.zip.server.support.ChannelCatalogFixture.pricing;
import static chaeso.zip.server.support.ChannelCatalogFixture.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import chaeso.zip.server.channel.application.dto.AudienceMetricResponse;
import chaeso.zip.server.channel.application.dto.ChannelDetailResponse;
import chaeso.zip.server.channel.application.dto.ProductResponse;
import chaeso.zip.server.channel.application.dto.RecommendationBasisResponse;
import chaeso.zip.server.channel.domain.ChannelNotFoundException;
import chaeso.zip.server.channel.domain.entity.Channel;
import chaeso.zip.server.channel.domain.entity.ChannelAudienceMetric;
import chaeso.zip.server.channel.domain.entity.ChannelPricing;
import chaeso.zip.server.channel.domain.repository.ChannelAudienceMetricRepository;
import chaeso.zip.server.channel.domain.repository.ChannelPricingRepository;
import chaeso.zip.server.channel.domain.repository.ChannelProductRepository;
import chaeso.zip.server.channel.domain.repository.ChannelReferenceRepository;
import chaeso.zip.server.channel.domain.repository.ChannelRepository;
import chaeso.zip.server.channel.domain.vo.AgeBand;
import chaeso.zip.server.channel.domain.vo.CampaignObjective;
import chaeso.zip.server.channel.domain.vo.Category;
import chaeso.zip.server.channel.domain.vo.PricingModel;
import chaeso.zip.server.onboarding.domain.entity.Onboarding;
import chaeso.zip.server.onboarding.domain.repository.OnboardingRepository;
import chaeso.zip.server.onboarding.domain.vo.CampaignPeriod;
import chaeso.zip.server.recommendation.application.RecommendationService;
import chaeso.zip.server.recommendation.application.dto.RecommendationItemResponse;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChannelServiceImplTest {

  private static final UUID CHANNEL_ID = UUID.randomUUID();
  private static final UUID ONBOARDING_ID = UUID.randomUUID();
  private static final UUID OWNER_ID = UUID.randomUUID();

  @Mock
  private ChannelRepository channelRepository;
  @Mock
  private ChannelProductRepository channelProductRepository;
  @Mock
  private ChannelPricingRepository channelPricingRepository;
  @Mock
  private ChannelAudienceMetricRepository channelAudienceMetricRepository;
  @Mock
  private ChannelReferenceRepository channelReferenceRepository;
  @Mock
  private RecommendationService recommendationService;
  @Mock
  private OnboardingRepository onboardingRepository;

  @InjectMocks
  private ChannelServiceImpl channelService;

  @Nested
  @DisplayName("추천 근거")
  class Basis {

    @BeforeEach
    void givenChannel() {
      Channel channel = channel(CHANNEL_ID, "11번가 광고");
      given(channelRepository.findByIdAndActiveTrue(CHANNEL_ID)).willReturn(Optional.of(channel));
      given(channelProductRepository.findByChannelId(CHANNEL_ID)).willReturn(List.of());
      given(channelAudienceMetricRepository.findByChannelId(CHANNEL_ID)).willReturn(List.of());
      given(channelReferenceRepository.findByChannelId(CHANNEL_ID)).willReturn(List.of());
    }

    @Test
    @DisplayName("추천에 포함된 채널이면 온보딩의 목표/업종/예산을 근거로 채운다")
    void fillsBasisForRecommendedChannel() {
      givenRecommended();
      given(onboardingRepository.findById(ONBOARDING_ID))
          .willReturn(Optional.of(onboarding(OWNER_ID)));

      ChannelDetailResponse detail = channelService.getChannel(CHANNEL_ID, ONBOARDING_ID, OWNER_ID);

      assertThat(detail.recommendationBasis()).isEqualTo(new RecommendationBasisResponse(
          CampaignObjective.TRAFFIC, Category.MEDICAL_HEALTHCARE, 1_000_000L, 3_000_000L));
    }

    @Test
    @DisplayName("전체 채널 비교로 열면 온보딩을 보지 않고 근거를 비운다")
    void skipsBasisWithoutOnboardingId() {
      ChannelDetailResponse detail = channelService.getChannel(CHANNEL_ID, null, OWNER_ID);

      assertThat(detail.recommendationBasis()).isNull();
      verifyNoInteractions(recommendationService, onboardingRepository);
    }

    @Test
    @DisplayName("비로그인 요청은 온보딩을 볼 자격이 없어 근거를 비운다")
    void skipsBasisForAnonymousRequest() {
      ChannelDetailResponse detail = channelService.getChannel(CHANNEL_ID, ONBOARDING_ID, null);

      assertThat(detail.recommendationBasis()).isNull();
      verifyNoInteractions(recommendationService, onboardingRepository);
    }

    @Test
    @DisplayName("남이 제출한 온보딩이면 추천 여부를 따지기 전에 근거를 비운다")
    void skipsBasisForOnboardingOfAnotherUser() {
      given(onboardingRepository.findById(ONBOARDING_ID))
          .willReturn(Optional.of(onboarding(OWNER_ID)));

      ChannelDetailResponse detail =
          channelService.getChannel(CHANNEL_ID, ONBOARDING_ID, UUID.randomUUID());

      assertThat(detail.recommendationBasis()).isNull();
      verifyNoInteractions(recommendationService);
    }

    @Test
    @DisplayName("주인이 없는 온보딩(비로그인 제출)은 누구에게도 근거를 주지 않는다")
    void skipsBasisForOwnerlessOnboarding() {
      given(onboardingRepository.findById(ONBOARDING_ID)).willReturn(Optional.of(onboarding(null)));

      ChannelDetailResponse detail = channelService.getChannel(CHANNEL_ID, ONBOARDING_ID, OWNER_ID);

      assertThat(detail.recommendationBasis()).isNull();
      verifyNoInteractions(recommendationService);
    }

    @Test
    @DisplayName("그 추천에 없던 채널이면 근거를 비운다")
    void skipsBasisForChannelOutsideRecommendation() {
      given(recommendationService.recommend(ONBOARDING_ID))
          .willReturn(List.of(recommendationItem(UUID.randomUUID())));
      given(onboardingRepository.findById(ONBOARDING_ID))
          .willReturn(Optional.of(onboarding(OWNER_ID)));

      ChannelDetailResponse detail = channelService.getChannel(CHANNEL_ID, ONBOARDING_ID, OWNER_ID);

      assertThat(detail.recommendationBasis()).isNull();
    }

    @Test
    @DisplayName("온보딩이 사라졌으면 상세는 그대로 주고 근거만 비운다")
    void skipsBasisWhenOnboardingMissing() {
      given(onboardingRepository.findById(ONBOARDING_ID)).willReturn(Optional.empty());

      ChannelDetailResponse detail = channelService.getChannel(CHANNEL_ID, ONBOARDING_ID, OWNER_ID);

      assertThat(detail.id()).isEqualTo(CHANNEL_ID);
      assertThat(detail.recommendationBasis()).isNull();
      verifyNoInteractions(recommendationService);
    }

    private void givenRecommended() {
      given(recommendationService.recommend(ONBOARDING_ID))
          .willReturn(List.of(recommendationItem(CHANNEL_ID)));
    }

    private RecommendationItemResponse recommendationItem(UUID channelId) {
      return new RecommendationItemResponse(channelId, "채널", 0, "근거", "타깃",
          null, null, null, null, null, false, null);
    }

    private Onboarding onboarding(UUID userId) {
      return OnboardingFixture.onboarding(userId, Category.MEDICAL_HEALTHCARE,
          CampaignObjective.TRAFFIC, List.of(AgeBand.AGE_20S), 1_000_000L, 3_000_000L,
          CampaignPeriod.M1);
    }
  }

  @Nested
  @DisplayName("상품 집행 가능 여부")
  class Executability {

    private static final UUID CHEAP_PRODUCT_ID = UUID.randomUUID();
    private static final UUID PRICEY_PRODUCT_ID = UUID.randomUUID();

    @BeforeEach
    void givenChannelWithProducts() {
      Channel channel = channel(CHANNEL_ID, "11번가 광고");
      given(channelRepository.findByIdAndActiveTrue(CHANNEL_ID)).willReturn(Optional.of(channel));
      given(channelProductRepository.findByChannelId(CHANNEL_ID)).willReturn(List.of(
          product(CHEAP_PRODUCT_ID, CHANNEL_ID), product(PRICEY_PRODUCT_ID, CHANNEL_ID)));
      given(channelAudienceMetricRepository.findByChannelId(CHANNEL_ID)).willReturn(List.of());
      given(channelReferenceRepository.findByChannelId(CHANNEL_ID)).willReturn(List.of());
    }

    @Test
    @DisplayName("온보딩 예산 상한을 기준으로 상품마다 따로 판정한다")
    void judgesEachProductAgainstOnboardingBudget() {
      givenPricedProducts();
      givenOwnedOnboarding();

      List<ProductResponse> products =
          channelService.getChannel(CHANNEL_ID, ONBOARDING_ID, OWNER_ID).products();

      assertThat(products).extracting(ProductResponse::id, ProductResponse::isExecutable)
          .containsExactly(
              tuple(CHEAP_PRODUCT_ID, true),      // 예산 3,000,000 >= 단가 1,000,000
              tuple(PRICEY_PRODUCT_ID, false));   // 예산 3,000,000 <  단가 5,000,000
    }

    @Test
    @DisplayName("비교 경로는 판정 기준이 없으므로 전부 비운다")
    void leavesAllUnjudgedWithoutOnboardingId() {
      givenPricedProducts();

      List<ProductResponse> products =
          channelService.getChannel(CHANNEL_ID, null, OWNER_ID).products();

      assertThat(products).extracting(ProductResponse::isExecutable).containsOnlyNulls();
      verifyNoInteractions(onboardingRepository);
    }

    @Test
    @DisplayName("남의 온보딩으로는 예산을 빌려 쓸 수 없어 판정하지 않는다")
    void leavesUnjudgedForOnboardingOfAnotherUser() {
      givenPricedProducts();
      given(onboardingRepository.findById(ONBOARDING_ID))
          .willReturn(Optional.of(onboarding(UUID.randomUUID())));

      List<ProductResponse> products =
          channelService.getChannel(CHANNEL_ID, ONBOARDING_ID, OWNER_ID).products();

      assertThat(products).extracting(ProductResponse::isExecutable).containsOnlyNulls();
    }

    @Test
    @DisplayName("값이 있는 단가가 없는 상품은 기준 단가를 정할 수 없어 판정하지 않는다")
    void leavesUnjudgedWhenProductHasNoPricedPricing() {
      givenOwnedOnboarding();
      givenPricings(pricing(CHEAP_PRODUCT_ID, PricingModel.CPM, "1000000"),
          pricing(PRICEY_PRODUCT_ID, PricingModel.CPM, null));

      List<ProductResponse> products =
          channelService.getChannel(CHANNEL_ID, ONBOARDING_ID, OWNER_ID).products();

      assertThat(products).extracting(ProductResponse::id, ProductResponse::isExecutable)
          .containsExactly(tuple(CHEAP_PRODUCT_ID, true), tuple(PRICEY_PRODUCT_ID, null));
    }

    @Test
    @DisplayName("예산이 0 원이면 판정 불가가 아니라 전부 집행 불가로 확정한다")
    void judgesEverythingUnexecutableOnZeroBudget() {
      givenPricedProducts();
      given(onboardingRepository.findById(ONBOARDING_ID))
          .willReturn(Optional.of(onboarding(OWNER_ID, 0L, 0L)));

      List<ProductResponse> products =
          channelService.getChannel(CHANNEL_ID, ONBOARDING_ID, OWNER_ID).products();

      // 값이 있는 단가는 모두 0 보다 크므로 0 원 예산으로는 어느 상품도 집행할 수 없다
      assertThat(products).extracting(ProductResponse::isExecutable)
          .containsOnly(false);
    }

    private void givenPricedProducts() {
      givenPricings(pricing(CHEAP_PRODUCT_ID, PricingModel.CPM, "1000000"),
          pricing(PRICEY_PRODUCT_ID, PricingModel.CPM, "5000000"));
    }

    private void givenPricings(ChannelPricing... pricings) {
      given(channelPricingRepository.findByChannelProductIdIn(
          List.of(CHEAP_PRODUCT_ID, PRICEY_PRODUCT_ID))).willReturn(List.of(pricings));
    }

    private void givenOwnedOnboarding() {
      given(onboardingRepository.findById(ONBOARDING_ID))
          .willReturn(Optional.of(onboarding(OWNER_ID)));
    }

    private Onboarding onboarding(UUID userId) {
      return onboarding(userId, 1_000_000L, 3_000_000L);
    }

    private Onboarding onboarding(UUID userId, long budgetMin, long budgetMax) {
      return OnboardingFixture.onboarding(userId, Category.MEDICAL_HEALTHCARE,
          CampaignObjective.TRAFFIC, List.of(AgeBand.AGE_20S), budgetMin, budgetMax,
          CampaignPeriod.M1);
    }
  }

  @Nested
  @DisplayName("매체 키워드")
  class Tags {

    private static final List<String> DEFAULT_TAGS = List.of("KPI 최적", "입문자 추천");

    @BeforeEach
    void givenChannelProducts() {
      given(channelProductRepository.findByChannelId(CHANNEL_ID)).willReturn(List.of());
      given(channelAudienceMetricRepository.findByChannelId(CHANNEL_ID)).willReturn(List.of());
      given(channelReferenceRepository.findByChannelId(CHANNEL_ID)).willReturn(List.of());
    }

    @Test
    @DisplayName("전체 채널 비교로 열면 채널 고유의 키워드를 준다")
    void usesChannelTagsWithoutOnboardingId() {
      givenChannelTags(DEFAULT_TAGS);

      assertThat(tagsOf(null, OWNER_ID)).isEqualTo(DEFAULT_TAGS);
      verifyNoInteractions(recommendationService, onboardingRepository);
    }

    @Test
    @DisplayName("맞춤 채널로 열어도 채널 고유의 키워드를 그대로 준다")
    void usesSameChannelTagsForRecommendedChannel() {
      givenChannelTags(DEFAULT_TAGS);
      given(onboardingRepository.findById(ONBOARDING_ID))
          .willReturn(Optional.of(OnboardingFixture.onboarding(OWNER_ID,
              Category.MEDICAL_HEALTHCARE, CampaignObjective.TRAFFIC, List.of(AgeBand.AGE_20S),
              1_000_000L, 3_000_000L, CampaignPeriod.M1)));
      given(recommendationService.recommend(ONBOARDING_ID))
          .willReturn(List.of(new RecommendationItemResponse(CHANNEL_ID, "채널", 0, "근거", "타깃",
              null, null, null, null, null, false, null)));

      assertThat(tagsOf(ONBOARDING_ID, OWNER_ID)).isEqualTo(DEFAULT_TAGS);
    }

    @Test
    @DisplayName("키워드가 셋 이상이어도 화면이 담는 두 개까지만 준다")
    void limitsTagsToTwo() {
      givenChannelTags(List.of("KPI 최적", "입문자 추천", "커머스 특화"));

      assertThat(tagsOf(null, OWNER_ID)).containsExactly("KPI 최적", "입문자 추천");
    }

    @Test
    @DisplayName("키워드가 없으면 null 이 아니라 빈 배열을 준다")
    void givesEmptyListWhenChannelHasNoTags() {
      givenChannelTags(null);

      assertThat(tagsOf(null, OWNER_ID)).isEmpty();
    }

    private void givenChannelTags(List<String> defaultTags) {
      given(channelRepository.findByIdAndActiveTrue(CHANNEL_ID))
          .willReturn(Optional.of(channelWithDefaultTags(CHANNEL_ID, "11번가 광고", defaultTags)));
    }

    private List<String> tagsOf(UUID onboardingId, UUID requesterId) {
      return channelService.getChannel(CHANNEL_ID, onboardingId, requesterId).tags();
    }
  }

  @Nested
  @DisplayName("대표 오디언스 규모 지표")
  class AudienceMetrics {

    @BeforeEach
    void givenChannelWithoutProducts() {
      given(channelRepository.findByIdAndActiveTrue(CHANNEL_ID))
          .willReturn(Optional.of(channel(CHANNEL_ID, "11번가 광고")));
      given(channelProductRepository.findByChannelId(CHANNEL_ID)).willReturn(List.of());
      given(channelReferenceRepository.findByChannelId(CHANNEL_ID)).willReturn(List.of());
    }

    @Test
    @DisplayName("카테고리 우선순위가 높은 지표부터 서로 다른 카테고리로 두 개를 고른다")
    void picksTwoMetricsFromDifferentCategoriesByPriority() {
      givenMetrics(audienceMetric(CHANNEL_ID, "팔로워 수"), audienceMetric(CHANNEL_ID, "누적 회원 수"),
          audienceMetric(CHANNEL_ID, "글로벌 MAU"), audienceMetric(CHANNEL_ID, "앱 DAU"));

      assertThat(metricNames()).containsExactly("글로벌 MAU", "앱 DAU");
    }

    @Test
    @DisplayName("MAU/DAU 가 없는 채널은 회원 수·방문자 수 같은 하위 카테고리로 채운다")
    void fallsBackToLowerCategoriesWithoutActiveUserMetrics() {
      givenMetrics(audienceMetric(CHANNEL_ID, "구독자 수"), audienceMetric(CHANNEL_ID, "월 방문자 수"),
          audienceMetric(CHANNEL_ID, "가입자수"));

      assertThat(metricNames()).containsExactly("가입자수", "월 방문자 수");
    }

    @Test
    @DisplayName("같은 카테고리 지표가 여럿이어도 다른 카테고리를 먼저 채워 다양성을 지킨다")
    void prefersAnotherCategoryOverSecondMetricOfTopCategory() {
      givenMetrics(audienceMetric(CHANNEL_ID, "국내 MAU"), audienceMetric(CHANNEL_ID, "해외 MAU"),
          audienceMetric(CHANNEL_ID, "누적 가입자"));

      assertThat(metricNames()).containsExactly("국내 MAU", "누적 가입자");
    }

    @Test
    @DisplayName("카테고리가 하나뿐이면 그 카테고리에서 규모가 큰 두 개를 채운다")
    void fillsFromSingleCategoryWhenNoOtherCategoryExists() {
      givenMetrics(audienceMetric(CHANNEL_ID, "국내 MAU", "1000000"),
          audienceMetric(CHANNEL_ID, "해외 MAU", "3000000"),
          audienceMetric(CHANNEL_ID, "앱 MAU", "2000000"));

      assertThat(metricNames()).containsExactly("해외 MAU", "앱 MAU");
    }

    @Test
    @DisplayName("같은 카테고리에서는 수치가 큰 지표를 대표로 고르고, 수치가 없는 지표는 뒤로 보낸다")
    void prefersLargerValueAndDefersValuelessMetricWithinCategory() {
      givenMetrics(audienceMetric(CHANNEL_ID, "제휴 회원 수"),
          audienceMetric(CHANNEL_ID, "누적 회원 수", "5000000"),
          audienceMetric(CHANNEL_ID, "월 방문자 수"));

      assertThat(metricNames()).containsExactly("누적 회원 수", "월 방문자 수");
    }

    @Test
    @DisplayName("조회 순서가 뒤바뀌어도 같은 지표를 고른다")
    void picksSameMetricsRegardlessOfQueryOrder() {
      ChannelAudienceMetric member = audienceMetric(CHANNEL_ID, "누적 회원 수", "5000000");
      ChannelAudienceMetric smallerMember = audienceMetric(CHANNEL_ID, "제휴 회원 수", "1000000");
      ChannelAudienceMetric visitor = audienceMetric(CHANNEL_ID, "월 방문자 수", "9000000");

      givenMetrics(visitor, smallerMember, member);
      assertThat(metricNames()).containsExactly("누적 회원 수", "월 방문자 수");

      givenMetrics(smallerMember, member, visitor);
      assertThat(metricNames()).containsExactly("누적 회원 수", "월 방문자 수");
    }

    @Test
    @DisplayName("분류되지 않는 지표만 가진 채널도 그 지표를 그대로 대표로 준다")
    void picksUnclassifiedMetricsAsRepresentative() {
      givenMetrics(audienceMetric(CHANNEL_ID, "누적 거래액"), audienceMetric(CHANNEL_ID, "제휴사 수"));

      assertThat(metricNames()).containsExactly("누적 거래액", "제휴사 수");
    }

    @Test
    @DisplayName("지표가 하나뿐이면 하나만 준다")
    void givesSingleMetricWhenChannelHasOne() {
      givenMetrics(audienceMetric(CHANNEL_ID, "MAU", "1000000"));

      assertThat(audienceMetrics()).containsExactly(
          new AudienceMetricResponse("MAU", new BigDecimal("1000000"), null, null, null));
    }

    @Test
    @DisplayName("지표가 없으면 null 이 아니라 빈 배열을 준다")
    void givesEmptyListWhenChannelHasNoMetrics() {
      givenMetrics();

      assertThat(audienceMetrics()).isEmpty();
    }

    private void givenMetrics(ChannelAudienceMetric... metrics) {
      given(channelAudienceMetricRepository.findByChannelId(CHANNEL_ID))
          .willReturn(List.of(metrics));
    }

    private List<AudienceMetricResponse> audienceMetrics() {
      return channelService.getChannel(CHANNEL_ID, null, OWNER_ID).audienceMetrics();
    }

    private List<String> metricNames() {
      return audienceMetrics().stream().map(AudienceMetricResponse::metricName).toList();
    }
  }

  @Test
  @DisplayName("존재하지 않는 채널은 추천 근거를 따지기 전에 거부한다")
  void rejectsUnknownChannel() {
    given(channelRepository.findByIdAndActiveTrue(CHANNEL_ID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> channelService.getChannel(CHANNEL_ID, ONBOARDING_ID, OWNER_ID))
        .isInstanceOf(ChannelNotFoundException.class);

    verifyNoInteractions(recommendationService, onboardingRepository);
  }
}
