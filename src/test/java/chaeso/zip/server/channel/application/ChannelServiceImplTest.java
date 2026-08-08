package chaeso.zip.server.channel.application;

import static chaeso.zip.server.support.ChannelCatalogFixture.channel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import chaeso.zip.server.channel.application.dto.ChannelDetailResponse;
import chaeso.zip.server.channel.application.dto.RecommendationBasisResponse;
import chaeso.zip.server.channel.domain.ChannelNotFoundException;
import chaeso.zip.server.channel.domain.entity.Channel;
import chaeso.zip.server.channel.domain.repository.ChannelAudienceMetricRepository;
import chaeso.zip.server.channel.domain.repository.ChannelProductRepository;
import chaeso.zip.server.channel.domain.repository.ChannelReferenceRepository;
import chaeso.zip.server.channel.domain.repository.ChannelRepository;
import chaeso.zip.server.channel.domain.vo.AgeBand;
import chaeso.zip.server.channel.domain.vo.CampaignObjective;
import chaeso.zip.server.channel.domain.vo.Category;
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

  @Mock
  private ChannelRepository channelRepository;
  @Mock
  private ChannelProductRepository channelProductRepository;
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
      Onboarding onboarding = OnboardingFixture.onboarding(Category.MEDICAL_HEALTHCARE,
          CampaignObjective.TRAFFIC, List.of(AgeBand.AGE_20S), 1_000_000L, 3_000_000L,
          CampaignPeriod.M1);
      given(channelRecommendationRepository
          .existsByOnboardingIdAndChannelId(ONBOARDING_ID, CHANNEL_ID)).willReturn(true);
      given(onboardingRepository.findById(ONBOARDING_ID)).willReturn(Optional.of(onboarding));

      ChannelDetailResponse detail = channelService.getChannel(CHANNEL_ID, ONBOARDING_ID);

      assertThat(detail.recommendationBasis()).isEqualTo(new RecommendationBasisResponse(
          CampaignObjective.TRAFFIC, Category.MEDICAL_HEALTHCARE, 1_000_000L, 3_000_000L));
    }

    @Test
    @DisplayName("전체 채널 비교로 열면 온보딩을 보지 않고 근거를 비운다")
    void skipsBasisWithoutOnboardingId() {
      ChannelDetailResponse detail = channelService.getChannel(CHANNEL_ID, null);

      assertThat(detail.recommendationBasis()).isNull();
      verifyNoInteractions(channelRecommendationRepository, onboardingRepository);
    }

    @Test
    @DisplayName("그 추천에 없던 채널이면 온보딩을 조회하지 않고 근거를 비운다")
    void skipsBasisForChannelOutsideRecommendation() {
      given(channelRecommendationRepository
          .existsByOnboardingIdAndChannelId(ONBOARDING_ID, CHANNEL_ID)).willReturn(false);

      ChannelDetailResponse detail = channelService.getChannel(CHANNEL_ID, ONBOARDING_ID);

      assertThat(detail.recommendationBasis()).isNull();
      verifyNoInteractions(onboardingRepository);
    }

    @Test
    @DisplayName("온보딩이 사라졌으면 상세는 그대로 주고 근거만 비운다")
    void skipsBasisWhenOnboardingMissing() {
      given(channelRecommendationRepository
          .existsByOnboardingIdAndChannelId(ONBOARDING_ID, CHANNEL_ID)).willReturn(true);
      given(onboardingRepository.findById(ONBOARDING_ID)).willReturn(Optional.empty());

      ChannelDetailResponse detail = channelService.getChannel(CHANNEL_ID, ONBOARDING_ID);

      assertThat(detail.id()).isEqualTo(CHANNEL_ID);
      assertThat(detail.recommendationBasis()).isNull();
    }
  }

  @Test
  @DisplayName("존재하지 않는 채널은 추천 근거를 따지기 전에 거부한다")
  void rejectsUnknownChannel() {
    given(channelRepository.findByIdAndActiveTrue(CHANNEL_ID)).willReturn(Optional.empty());

    assertThatThrownBy(() -> channelService.getChannel(CHANNEL_ID, ONBOARDING_ID))
        .isInstanceOf(ChannelNotFoundException.class);

    verifyNoInteractions(channelRecommendationRepository, onboardingRepository);
  }
}
