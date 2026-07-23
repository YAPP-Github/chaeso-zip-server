package chaeso.zip.server.onboarding.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import chaeso.zip.server.channel.domain.entity.Channel;
import chaeso.zip.server.channel.domain.repository.ChannelRepository;
import chaeso.zip.server.channel.domain.vo.AgeBand;
import chaeso.zip.server.channel.domain.vo.CampaignObjective;
import chaeso.zip.server.channel.domain.vo.Category;
import chaeso.zip.server.onboarding.application.dto.AdHistoryCommand;
import chaeso.zip.server.onboarding.application.dto.SubmitOnboardingCommand;
import chaeso.zip.server.onboarding.domain.OnboardingBusinessException;
import chaeso.zip.server.onboarding.domain.OnboardingErrorCode;
import chaeso.zip.server.onboarding.domain.entity.Onboarding;
import chaeso.zip.server.onboarding.domain.entity.OnboardingAdHistorySnapshot;
import chaeso.zip.server.onboarding.domain.repository.OnboardingAdHistorySnapshotRepository;
import chaeso.zip.server.onboarding.domain.repository.OnboardingRepository;
import chaeso.zip.server.onboarding.domain.vo.AdExperience;
import chaeso.zip.server.onboarding.domain.vo.CampaignPeriod;
import chaeso.zip.server.onboarding.domain.vo.ServiceType;
import chaeso.zip.server.performance.domain.entity.AdPerformance;
import chaeso.zip.server.performance.domain.repository.AdPerformanceRepository;
import chaeso.zip.server.support.OnboardingFixture;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceSubmitTest {

  @Mock
  private OnboardingRepository onboardingRepository;

  @Mock
  private AdPerformanceRepository adPerformanceRepository;

  @Mock
  private OnboardingAdHistorySnapshotRepository onboardingAdHistorySnapshotRepository;

  @Mock
  private ChannelRepository channelRepository;

  @InjectMocks
  private OnboardingServiceImpl onboardingService;

  private static final UUID USER_ID = UUID.randomUUID();

  @Test
  @DisplayName("경험 없음으로 제출하면 활성 응답이 저장된다")
  void savesResponseWithoutHistory() {
    given(onboardingRepository.findByUserIdAndIsActiveTrue(USER_ID)).willReturn(List.of());
    given(onboardingRepository.saveAndFlush(any(Onboarding.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    onboardingService.submit(USER_ID, OnboardingFixture.submitCommand());

    ArgumentCaptor<Onboarding> captor =
        ArgumentCaptor.forClass(Onboarding.class);
    then(onboardingRepository).should().saveAndFlush(captor.capture());
    Onboarding saved = captor.getValue();
    assertThat(saved.getUserId()).isEqualTo(USER_ID);
    assertThat(saved.isActive()).isTrue();
    then(adPerformanceRepository).should().deleteByUserId(USER_ID);
    then(adPerformanceRepository).should().saveAll(List.of());
    then(onboardingAdHistorySnapshotRepository).should().saveAll(List.of());
  }

  @Test
  @DisplayName("재제출하면 이전 활성 응답이 비활성화된다")
  void deactivatesPreviousActiveResponse() {
    Onboarding previous = Onboarding.create(
        USER_ID, "이전", Category.OTHERS, ServiceType.WEB, List.of(AgeBand.AGE_30S),
        CampaignObjective.AWARENESS, 1L, 2L, CampaignPeriod.M1, AdExperience.NONE);
    given(onboardingRepository.findByUserIdAndIsActiveTrue(USER_ID))
        .willReturn(List.of(previous));
    given(onboardingRepository.saveAndFlush(any(Onboarding.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    onboardingService.submit(USER_ID, OnboardingFixture.submitCommand());

    assertThat(previous.isActive()).isFalse();
  }

  @Test
  @DisplayName("최소 예산이 최대 예산보다 크면 ONB-001")
  void rejectsInvertedBudgetRange() {
    SubmitOnboardingCommand command = OnboardingFixture.submitCommand(ServiceType.WEB,
        CampaignObjective.TRAFFIC, 9_000_000L, 1_000_000L, AdExperience.NONE, List.of());

    assertThatThrownBy(() -> onboardingService.submit(USER_ID, command))
        .isInstanceOf(OnboardingBusinessException.class)
        .extracting("errorCode")
        .isEqualTo(OnboardingErrorCode.INVALID_BUDGET_RANGE);
    then(onboardingRepository).should(never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("웹 서비스가 앱 전용 목표를 고르면 ONB-002")
  void rejectsAppOnlyObjectiveForWeb() {
    SubmitOnboardingCommand command = OnboardingFixture.submitCommand(ServiceType.WEB,
        CampaignObjective.APP_INSTALL, 1L, 2L, AdExperience.NONE, List.of());

    assertThatThrownBy(() -> onboardingService.submit(USER_ID, command))
        .isInstanceOf(OnboardingBusinessException.class)
        .extracting("errorCode")
        .isEqualTo(OnboardingErrorCode.OBJECTIVE_NOT_ALLOWED);
  }

  @Test
  @DisplayName("경험 없음인데 집행 내역을 보내면 ONB-003")
  void rejectsHistoryWhenNoExperience() {
    SubmitOnboardingCommand command = OnboardingFixture.submitCommand(ServiceType.WEB,
        CampaignObjective.TRAFFIC, 1L, 2L, AdExperience.NONE,
        List.of(new AdHistoryCommand(null, "인스타그램", 1000L, null, null, null, null, null)));

    assertThatThrownBy(() -> onboardingService.submit(USER_ID, command))
        .isInstanceOf(OnboardingBusinessException.class)
        .extracting("errorCode")
        .isEqualTo(OnboardingErrorCode.AD_EXPERIENCE_MISMATCH);
  }

  @Test
  @DisplayName("경험 있음인데 집행 내역이 비면 ONB-003")
  void rejectsEmptyHistoryWhenExperienced() {
    SubmitOnboardingCommand command = OnboardingFixture.submitCommand(ServiceType.WEB,
        CampaignObjective.TRAFFIC, 1L, 2L, AdExperience.EXPERIENCED, List.of());

    assertThatThrownBy(() -> onboardingService.submit(USER_ID, command))
        .isInstanceOf(OnboardingBusinessException.class)
        .extracting("errorCode")
        .isEqualTo(OnboardingErrorCode.AD_EXPERIENCE_MISMATCH);
  }

  @Test
  @DisplayName("집행 내역이 50건을 넘으면 ONB-004")
  void rejectsTooManyHistoryRows() {
    List<AdHistoryCommand> rows = IntStream.rangeClosed(1, 51)
        .mapToObj(i -> new AdHistoryCommand(null, "채널" + i, 1000L, null, null, null, null, null))
        .toList();
    SubmitOnboardingCommand command = OnboardingFixture.submitCommand(ServiceType.WEB,
        CampaignObjective.TRAFFIC, 1L, 2L, AdExperience.EXPERIENCED, rows);

    assertThatThrownBy(() -> onboardingService.submit(USER_ID, command))
        .isInstanceOf(OnboardingBusinessException.class)
        .extracting("errorCode")
        .isEqualTo(OnboardingErrorCode.TOO_MANY_AD_HISTORY);
  }

  @Test
  @DisplayName("존재하지 않는 channelId를 보내면 ONB-005")
  void rejectsUnknownChannelId() {
    UUID unknown = UUID.randomUUID();
    given(channelRepository.findAllById(List.of(unknown))).willReturn(List.of());
    SubmitOnboardingCommand command = OnboardingFixture.submitCommand(ServiceType.WEB,
        CampaignObjective.TRAFFIC, 1L, 2L, AdExperience.EXPERIENCED,
        List.of(new AdHistoryCommand(unknown, "인스타그램", 1000L, null, null, null, null, null)));

    assertThatThrownBy(() -> onboardingService.submit(USER_ID, command))
        .isInstanceOf(OnboardingBusinessException.class)
        .extracting("errorCode")
        .isEqualTo(OnboardingErrorCode.CHANNEL_NOT_FOUND);
  }

  @Test
  @DisplayName("집행 종료일이 시작일보다 빠르면 ONB-006")
  void rejectsInvertedAdPeriod() {
    UUID channelId = UUID.randomUUID();
    SubmitOnboardingCommand command = OnboardingFixture.submitCommand(ServiceType.WEB,
        CampaignObjective.TRAFFIC, 1L, 2L, AdExperience.EXPERIENCED,
        List.of(new AdHistoryCommand(channelId, "인스타그램", 1000L, null, null, null,
            LocalDate.of(2025, Month.MAY, 31), LocalDate.of(2025, Month.MARCH, 1))));

    assertThatThrownBy(() -> onboardingService.submit(USER_ID, command))
        .isInstanceOf(OnboardingBusinessException.class)
        .extracting("errorCode")
        .isEqualTo(OnboardingErrorCode.INVALID_AD_PERIOD);
    then(onboardingRepository).should(never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("집행 내역이 ad_performances 행으로 저장된다")
  void savesHistoryRows() {
    UUID channelId = UUID.randomUUID();
    Channel channel = mock(Channel.class);
    lenient().when(channel.getId()).thenReturn(channelId);
    given(channelRepository.findAllById(List.of(channelId))).willReturn(List.of(channel));
    given(onboardingRepository.findByUserIdAndIsActiveTrue(USER_ID)).willReturn(List.of());
    given(onboardingRepository.saveAndFlush(any(Onboarding.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    SubmitOnboardingCommand command = OnboardingFixture.submitCommand(ServiceType.WEB,
        CampaignObjective.TRAFFIC, 1L, 2L, AdExperience.EXPERIENCED,
        List.of(new AdHistoryCommand(channelId, "인스타그램", 3_000_000L, 250_000L, 3_000L, 120L,
            LocalDate.of(2025, Month.MARCH, 1), LocalDate.of(2025, Month.MAY, 31))));

    onboardingService.submit(USER_ID, command);

    ArgumentCaptor<List<AdPerformance>> captor = ArgumentCaptor.forClass(List.class);
    then(adPerformanceRepository).should().saveAll(captor.capture());
    assertThat(captor.getValue()).hasSize(1);
    AdPerformance performance = captor.getValue().getFirst();
    assertThat(performance.getUserId()).isEqualTo(USER_ID);
    assertThat(performance.getChannelId()).isEqualTo(channelId);
    assertThat(performance.getExternalChannelName()).isEqualTo("인스타그램");

    ArgumentCaptor<List<OnboardingAdHistorySnapshot>> snapshotCaptor =
        ArgumentCaptor.forClass(List.class);
    then(onboardingAdHistorySnapshotRepository).should().saveAll(snapshotCaptor.capture());
    assertThat(snapshotCaptor.getValue()).hasSize(1);
    OnboardingAdHistorySnapshot snapshot = snapshotCaptor.getValue().getFirst();
    assertThat(snapshot.getChannelId()).isEqualTo(channelId);
    assertThat(snapshot.getChannelNameSnap()).isEqualTo("인스타그램");
    assertThat(snapshot.getBudgetWonSnap()).isEqualTo(3_000_000L);
  }

  @Test
  @DisplayName("재제출해도 이전 스냅샷은 지우지 않는다")
  void doesNotDeletePreviousSnapshotsOnResubmit() {
    given(onboardingRepository.findByUserIdAndIsActiveTrue(USER_ID)).willReturn(List.of());
    given(onboardingRepository.saveAndFlush(any(Onboarding.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    onboardingService.submit(USER_ID, OnboardingFixture.submitCommand());

    then(onboardingAdHistorySnapshotRepository).should(never()).deleteAll();
  }

  @Test
  @DisplayName("동시 재제출로 활성 유니크 제약을 위반하면 ONB-007")
  void rejectsConcurrentSubmission() {
    given(onboardingRepository.findByUserIdAndIsActiveTrue(USER_ID)).willReturn(List.of());
    given(onboardingRepository.saveAndFlush(any(Onboarding.class)))
        .willThrow(new DataIntegrityViolationException("uq_onboarding_active_user"));
    SubmitOnboardingCommand command = OnboardingFixture.submitCommand();

    assertThatThrownBy(() -> onboardingService.submit(USER_ID, command))
        .isInstanceOf(OnboardingBusinessException.class)
        .extracting("errorCode")
        .isEqualTo(OnboardingErrorCode.CONCURRENT_SUBMISSION);
  }
}
