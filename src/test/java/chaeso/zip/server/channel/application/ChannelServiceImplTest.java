package chaeso.zip.server.channel.application;

import static chaeso.zip.server.support.ChannelCatalogFixture.channel;
import static chaeso.zip.server.support.ChannelCatalogFixture.pricing;
import static chaeso.zip.server.support.ChannelCatalogFixture.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import chaeso.zip.server.channel.application.dto.ChannelDetailResponse;
import chaeso.zip.server.channel.application.dto.ProductResponse;
import chaeso.zip.server.channel.application.dto.RecommendationBasisResponse;
import chaeso.zip.server.channel.domain.ChannelNotFoundException;
import chaeso.zip.server.channel.domain.entity.Channel;
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
import chaeso.zip.server.recommendation.domain.repository.ChannelRecommendationRepository;
import chaeso.zip.server.support.OnboardingFixture;
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
  private ChannelRecommendationRepository channelRecommendationRepository;
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
      verifyNoInteractions(channelRecommendationRepository, onboardingRepository);
    }

    @Test
    @DisplayName("비로그인 요청은 온보딩을 볼 자격이 없어 근거를 비운다")
    void skipsBasisForAnonymousRequest() {
      ChannelDetailResponse detail = channelService.getChannel(CHANNEL_ID, ONBOARDING_ID, null);

      assertThat(detail.recommendationBasis()).isNull();
      verifyNoInteractions(channelRecommendationRepository, onboardingRepository);
    }

    @Test
    @DisplayName("남이 제출한 온보딩이면 추천 여부를 따지기 전에 근거를 비운다")
    void skipsBasisForOnboardingOfAnotherUser() {
      given(onboardingRepository.findById(ONBOARDING_ID))
          .willReturn(Optional.of(onboarding(OWNER_ID)));

      ChannelDetailResponse detail =
          channelService.getChannel(CHANNEL_ID, ONBOARDING_ID, UUID.randomUUID());

      assertThat(detail.recommendationBasis()).isNull();
      verifyNoInteractions(channelRecommendationRepository);
    }

    @Test
    @DisplayName("주인이 없는 온보딩(비로그인 제출)은 누구에게도 근거를 주지 않는다")
    void skipsBasisForOwnerlessOnboarding() {
      given(onboardingRepository.findById(ONBOARDING_ID)).willReturn(Optional.of(onboarding(null)));

      ChannelDetailResponse detail = channelService.getChannel(CHANNEL_ID, ONBOARDING_ID, OWNER_ID);

      assertThat(detail.recommendationBasis()).isNull();
      verifyNoInteractions(channelRecommendationRepository);
    }

    @Test
    @DisplayName("그 추천에 없던 채널이면 근거를 비운다")
    void skipsBasisForChannelOutsideRecommendation() {
      given(channelRecommendationRepository
          .existsByOnboardingIdAndChannelId(ONBOARDING_ID, CHANNEL_ID)).willReturn(false);
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
      verifyNoInteractions(channelRecommendationRepository);
    }

    private void givenRecommended() {
      given(channelRecommendationRepository
          .existsByOnboardingIdAndChannelId(ONBOARDING_ID, CHANNEL_ID)).willReturn(true);
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
      return OnboardingFixture.onboarding(userId, Category.MEDICAL_HEALTHCARE,
          CampaignObjective.TRAFFIC, List.of(AgeBand.AGE_20S), 1_000_000L, 3_000_000L,
          CampaignPeriod.M1);
    }
  }

  @Test
  @DisplayName("존재하지 않는 채널은 추천 근거를 따지기 전에 거부한다")
  void rejectsUnknownChannel() {
    given(channelRepository.findByIdAndActiveTrue(CHANNEL_ID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> channelService.getChannel(CHANNEL_ID, ONBOARDING_ID, OWNER_ID))
        .isInstanceOf(ChannelNotFoundException.class);

    verifyNoInteractions(channelRecommendationRepository, onboardingRepository);
  }
}
