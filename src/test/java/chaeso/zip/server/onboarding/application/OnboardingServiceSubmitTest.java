package chaeso.zip.server.onboarding.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import chaeso.zip.server.channel.domain.ChannelErrorCode;
import chaeso.zip.server.channel.domain.ChannelNotFoundException;
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
import chaeso.zip.server.performance.domain.vo.PerfSource;
import chaeso.zip.server.support.OnboardingFixture;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

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

  @Mock
  private PerformanceFileStorage performanceFileStorage;

  @Mock
  private PlatformTransactionManager transactionManager;

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
    then(adPerformanceRepository).should().saveAll(List.of());
    then(onboardingAdHistorySnapshotRepository).should().saveAll(List.of());
  }

  @Test
  @DisplayName("userId가 없으면 익명으로 저장되고 기존 활성 응답 조회는 건너뛴다")
  void savesResponseAnonymouslyWhenUserIdIsNull() {
    given(onboardingRepository.saveAndFlush(any(Onboarding.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    onboardingService.submit(null, OnboardingFixture.submitCommand());

    ArgumentCaptor<Onboarding> captor = ArgumentCaptor.forClass(Onboarding.class);
    then(onboardingRepository).should().saveAndFlush(captor.capture());
    assertThat(captor.getValue().getUserId()).isNull();
    then(onboardingRepository).should(never()).findByUserIdAndIsActiveTrue(any());
  }

  @Test
  @DisplayName("재제출하면 이전 활성 응답이 비활성화된다")
  void deactivatesPreviousActiveResponse() {
    Onboarding previous = Onboarding.create(
        USER_ID, "이전", Category.OTHERS, ServiceType.WEB, List.of(AgeBand.AGE_30S),
        CampaignObjective.AWARENESS, 1L, 2L, CampaignPeriod.M1, AdExperience.NONE, List.of());
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
  @DisplayName("예산 범위 값이 null이면 ONB-001")
  void rejectsNullBudgetRange() {
    SubmitOnboardingCommand command = OnboardingFixture.submitCommand(ServiceType.WEB,
        CampaignObjective.TRAFFIC, null, 1_000_000L, AdExperience.NONE, List.of());

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
  @DisplayName("경험 있음인데 집행 내역도 성과파일도 없으면 ONB-003")
  void rejectsEmptyHistoryWhenExperienced() {
    SubmitOnboardingCommand command = OnboardingFixture.submitCommand(ServiceType.WEB,
        CampaignObjective.TRAFFIC, 1L, 2L, AdExperience.EXPERIENCED, List.of());

    assertThatThrownBy(() -> onboardingService.submit(USER_ID, command))
        .isInstanceOf(OnboardingBusinessException.class)
        .extracting("errorCode")
        .isEqualTo(OnboardingErrorCode.AD_EXPERIENCE_MISMATCH);
  }

  @Test
  @DisplayName("경험 있음이어도 수동 입력 없이 성과파일만 있으면 통과한다")
  void allowsExperiencedWithOnlyFileKeysNoManualHistory() {
    given(performanceFileStorage.verify("ad-history/abc.xlsx"))
        .willReturn("s3://bucket/ad-history/abc.xlsx");
    given(onboardingRepository.findByUserIdAndIsActiveTrue(USER_ID)).willReturn(List.of());
    given(onboardingRepository.saveAndFlush(any(Onboarding.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    SubmitOnboardingCommand command = OnboardingFixture.submitCommand(ServiceType.WEB,
        CampaignObjective.TRAFFIC, 1L, 2L, AdExperience.EXPERIENCED, List.of(),
        List.of("ad-history/abc.xlsx"));

    onboardingService.submit(USER_ID, command);

    then(onboardingRepository).should().saveAndFlush(any(Onboarding.class));
    then(adPerformanceRepository).should().saveAll(List.of());
  }

  @Test
  @DisplayName("파일 없이 수동 입력 필드가 1개뿐이면 ONB-010")
  void rejectsManualRowWithOnlyOneField() {
    SubmitOnboardingCommand command = OnboardingFixture.submitCommand(ServiceType.WEB,
        CampaignObjective.TRAFFIC, 1L, 2L, AdExperience.EXPERIENCED,
        List.of(new AdHistoryCommand(null, "인스타그램", 1000L, null, null, null, null, null)));

    assertThatThrownBy(() -> onboardingService.submit(USER_ID, command))
        .isInstanceOf(OnboardingBusinessException.class)
        .extracting("errorCode")
        .isEqualTo(OnboardingErrorCode.TOO_FEW_MANUAL_FIELDS);
    then(onboardingRepository).should(never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("파일 없이 수동 입력 필드가 하나도 없으면 ONB-010")
  void rejectsManualRowWithNoFields() {
    SubmitOnboardingCommand command = OnboardingFixture.submitCommand(ServiceType.WEB,
        CampaignObjective.TRAFFIC, 1L, 2L, AdExperience.EXPERIENCED,
        List.of(new AdHistoryCommand(null, "인스타그램", null, null, null, null, null, null)));

    assertThatThrownBy(() -> onboardingService.submit(USER_ID, command))
        .isInstanceOf(OnboardingBusinessException.class)
        .extracting("errorCode")
        .isEqualTo(OnboardingErrorCode.TOO_FEW_MANUAL_FIELDS);
  }

  @Test
  @DisplayName("파일 없이 수동 입력 필드가 정확히 2개면 통과한다")
  void allowsManualRowWithExactlyTwoFields() {
    given(onboardingRepository.findByUserIdAndIsActiveTrue(USER_ID)).willReturn(List.of());
    given(onboardingRepository.saveAndFlush(any(Onboarding.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    SubmitOnboardingCommand command = OnboardingFixture.submitCommand(ServiceType.WEB,
        CampaignObjective.TRAFFIC, 1L, 2L, AdExperience.EXPERIENCED,
        List.of(new AdHistoryCommand(null, "인스타그램", 1000L, null, null, null,
            LocalDate.of(2025, Month.MARCH, 1), null)));

    onboardingService.submit(USER_ID, command);

    then(onboardingRepository).should().saveAndFlush(any(Onboarding.class));
  }

  @Test
  @DisplayName("수동 입력 3건과 성과파일 5건을 함께 제출하면 통과하고, ad_performances는 수동 3건만 저장된다")
  void allowsManualHistoryAndFileKeysTogether() {
    List<AdHistoryCommand> manualRows = List.of(
        new AdHistoryCommand(null, "채널1", 1000L, 10_000L, null, null, null, null),
        new AdHistoryCommand(null, "채널2", 1000L, 10_000L, null, null, null, null),
        new AdHistoryCommand(null, "채널3", 1000L, 10_000L, null, null, null, null));
    List<String> rawFileKeys = List.of("ad-history/1.xlsx", "ad-history/2.xlsx",
        "ad-history/3.xlsx", "ad-history/4.xlsx", "ad-history/5.xlsx");
    rawFileKeys.forEach(key -> given(performanceFileStorage.verify(key))
        .willReturn("s3://bucket/" + key));
    given(onboardingRepository.findByUserIdAndIsActiveTrue(USER_ID)).willReturn(List.of());
    given(onboardingRepository.saveAndFlush(any(Onboarding.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    SubmitOnboardingCommand command = OnboardingFixture.submitCommand(ServiceType.WEB,
        CampaignObjective.TRAFFIC, 1L, 2L, AdExperience.EXPERIENCED, manualRows, rawFileKeys);

    onboardingService.submit(USER_ID, command);

    ArgumentCaptor<List<AdPerformance>> captor = ArgumentCaptor.forClass(List.class);
    then(adPerformanceRepository).should().saveAll(captor.capture());
    assertThat(captor.getValue()).hasSize(3);
    rawFileKeys.forEach(key -> {
      then(performanceFileStorage).should().verify(key);
      then(performanceFileStorage).should().confirm(key);
    });
  }

  @Test
  @DisplayName("성과파일 key를 검증/확정하고 온보딩의 raw_file_urls로 저장한다")
  void confirmsRawFileKeysAndStoresUrlsOnOnboarding() {
    given(performanceFileStorage.verify("ad-history/abc.xlsx"))
        .willReturn("s3://bucket/ad-history/abc.xlsx");
    given(onboardingRepository.findByUserIdAndIsActiveTrue(USER_ID)).willReturn(List.of());
    given(onboardingRepository.saveAndFlush(any(Onboarding.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    SubmitOnboardingCommand command = OnboardingFixture.submitCommand(ServiceType.WEB,
        CampaignObjective.TRAFFIC, 1L, 2L, AdExperience.EXPERIENCED, List.of(),
        List.of("ad-history/abc.xlsx"));

    onboardingService.submit(USER_ID, command);

    ArgumentCaptor<Onboarding> captor = ArgumentCaptor.forClass(Onboarding.class);
    then(onboardingRepository).should().saveAndFlush(captor.capture());
    assertThat(captor.getValue().getRawFileUrls())
        .containsExactly("s3://bucket/ad-history/abc.xlsx");
  }

  @Test
  @DisplayName("성과파일 검증에 실패하면 ONB-008이고 저장되지 않는다")
  void rejectsInvalidPerformanceFile() {
    given(performanceFileStorage.verify("ad-history/bad.xlsx"))
        .willThrow(new InvalidPerformanceFileException("invalid file"));
    SubmitOnboardingCommand command = OnboardingFixture.submitCommand(ServiceType.WEB,
        CampaignObjective.TRAFFIC, 1L, 2L, AdExperience.EXPERIENCED, List.of(),
        List.of("ad-history/bad.xlsx"));

    assertThatThrownBy(() -> onboardingService.submit(USER_ID, command))
        .isInstanceOf(OnboardingBusinessException.class)
        .extracting("errorCode")
        .isEqualTo(OnboardingErrorCode.PERFORMANCE_FILE_INVALID);
    then(onboardingRepository).should(never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("존재하지 않는 channelId를 보내면 CH-001")
  void rejectsUnknownChannelId() {
    UUID unknown = UUID.randomUUID();
    given(channelRepository.findAllById(List.of(unknown))).willReturn(List.of());
    SubmitOnboardingCommand command = OnboardingFixture.submitCommand(ServiceType.WEB,
        CampaignObjective.TRAFFIC, 1L, 2L, AdExperience.EXPERIENCED,
        List.of(new AdHistoryCommand(unknown, "인스타그램", 1000L, 10_000L, null, null, null,
            null)));

    assertThatThrownBy(() -> onboardingService.submit(USER_ID, command))
        .isInstanceOf(ChannelNotFoundException.class)
        .extracting("errorCode")
        .isEqualTo(ChannelErrorCode.CHANNEL_NOT_FOUND);
  }

  @Test
  @DisplayName("집행 내역이 ad_performances 행으로 MANUAL sourceType으로 저장된다")
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
    assertThat(performance.getSourceType()).isEqualTo(PerfSource.MANUAL);
    assertThat(performance.getChannelId()).isEqualTo(channelId);
    assertThat(performance.getExternalChannelName()).isEqualTo("인스타그램");
    assertThat(performance.getRawFileUrl()).isNull();
    then(performanceFileStorage).should(never()).verify(any());
    then(performanceFileStorage).should(never()).confirm(any());

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
  @DisplayName("동시 재제출로 활성 유니크 제약을 위반하면 ONB-006")
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
