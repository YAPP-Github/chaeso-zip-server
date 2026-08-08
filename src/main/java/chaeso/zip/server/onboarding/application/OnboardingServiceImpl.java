package chaeso.zip.server.onboarding.application;

import chaeso.zip.server.channel.domain.ChannelNotFoundException;
import chaeso.zip.server.channel.domain.entity.Channel;
import chaeso.zip.server.channel.domain.repository.ChannelRepository;
import chaeso.zip.server.onboarding.application.dto.AdHistoryCommand;
import chaeso.zip.server.onboarding.application.dto.OnboardingSubmitResponse;
import chaeso.zip.server.onboarding.application.dto.PresignPerformanceFileCommand;
import chaeso.zip.server.onboarding.application.dto.PresignedFileUploadResult;
import chaeso.zip.server.onboarding.application.dto.SubmitOnboardingCommand;
import chaeso.zip.server.onboarding.domain.OnboardingBusinessException;
import chaeso.zip.server.onboarding.domain.OnboardingErrorCode;
import chaeso.zip.server.onboarding.domain.entity.Onboarding;
import chaeso.zip.server.onboarding.domain.entity.OnboardingAdHistorySnapshot;
import chaeso.zip.server.onboarding.domain.repository.OnboardingAdHistorySnapshotRepository;
import chaeso.zip.server.onboarding.domain.repository.OnboardingRepository;
import chaeso.zip.server.onboarding.domain.vo.AdExperience;
import chaeso.zip.server.performance.domain.entity.AdPerformance;
import chaeso.zip.server.performance.domain.repository.AdPerformanceRepository;
import chaeso.zip.server.performance.domain.vo.PerfSource;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 온보딩 애플리케이션 서비스 구현체.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OnboardingServiceImpl implements OnboardingService {

  private static final int MIN_MANUAL_FIELDS = 2;

  private final OnboardingRepository onboardingRepository;
  private final AdPerformanceRepository adPerformanceRepository;
  private final OnboardingAdHistorySnapshotRepository onboardingAdHistorySnapshotRepository;
  private final ChannelRepository channelRepository;
  private final PerformanceFileStorage performanceFileStorage;
  private final PlatformTransactionManager transactionManager;

  @Override
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public OnboardingSubmitResponse submit(UUID userId, SubmitOnboardingCommand command) {
    validateSubmission(command.adExperience(), command.adHistory(), command.rawFileKeys());

    List<String> fileUrls = verifyPerformanceFiles(command.rawFileKeys());

    OnboardingSubmitResponse response = new TransactionTemplate(transactionManager)
        .execute(status -> saveOnboarding(userId, command, fileUrls));

    confirmPerformanceFiles(command.rawFileKeys());

    return response;
  }

  private OnboardingSubmitResponse saveOnboarding(UUID userId, SubmitOnboardingCommand command,
      List<String> confirmedFileUrls) {
    Onboarding response = Onboarding.create(
        userId,
        command.serviceName(),
        command.industry(),
        command.serviceType(),
        command.targetAgeBands(),
        command.campaignObjective(),
        command.budgetMin(),
        command.budgetMax(),
        command.period(),
        command.adExperience(),
        confirmedFileUrls);

    if (userId != null) {
      onboardingRepository.findByUserIdAndIsActiveTrue(userId)
          .forEach(Onboarding::deactivate);
      onboardingRepository.flush();
    }

    Onboarding saved = saveResponse(response);

    adPerformanceRepository.saveAll(command.adHistory().stream()
        .map(row -> AdPerformance.builder()
            .userId(userId)
            .sourceType(PerfSource.MANUAL)
            .channelId(row.channelId())
            .externalChannelName(row.channelNameRaw())
            .budgetWon(row.budgetWon())
            .impressions(row.impressions())
            .clicks(row.clicks())
            .conversions(row.conversions())
            .startedAt(row.startedAt())
            .endedAt(row.endedAt())
            .build())
        .toList());

    onboardingAdHistorySnapshotRepository.saveAll(command.adHistory().stream()
        .map(row -> OnboardingAdHistorySnapshot.snapshot(saved.getId(), row.channelId(),
            row.channelNameRaw(), row.budgetWon(), row.impressions(), row.clicks(),
            row.conversions(), row.startedAt(), row.endedAt()))
        .toList());

    return OnboardingSubmitResponse.from(saved);
  }

  @Override
  public List<PresignedFileUploadResult> issuePresignedUrls(
      List<PresignPerformanceFileCommand> files) {
    return performanceFileStorage.presign(files);
  }

  private Onboarding saveResponse(Onboarding response) {
    try {
      return onboardingRepository.saveAndFlush(response);
    } catch (DataIntegrityViolationException e) {
      throw new OnboardingBusinessException(OnboardingErrorCode.CONCURRENT_SUBMISSION);
    }
  }

  /**
   * adHistory/rawFileKeys의 관계 규칙을 검증한다.
   */
  private void validateSubmission(AdExperience adExperience, List<AdHistoryCommand> adHistory,
      List<String> rawFileKeys) {
    boolean experienced = adExperience == AdExperience.EXPERIENCED;
    boolean hasAnyHistory = !adHistory.isEmpty() || !rawFileKeys.isEmpty();
    if (experienced != hasAnyHistory) {
      throw new OnboardingBusinessException(OnboardingErrorCode.AD_EXPERIENCE_MISMATCH);
    }
    boolean hasUnderfilledManualRow = adHistory.stream()
        .anyMatch(row -> row.countFilledManualFields() < MIN_MANUAL_FIELDS);
    if (hasUnderfilledManualRow) {
      throw new OnboardingBusinessException(OnboardingErrorCode.TOO_FEW_MANUAL_FIELDS);
    }
    List<UUID> channelIds = adHistory.stream()
        .map(AdHistoryCommand::channelId)
        .filter(Objects::nonNull)
        .distinct()
        .toList();
    if (!channelIds.isEmpty()) {
      List<Channel> existingChannels = channelRepository.findAllById(channelIds);
      if (existingChannels.size() != channelIds.size()) {
        Set<UUID> existingIds = existingChannels.stream()
            .map(Channel::getId)
            .collect(Collectors.toSet());
        UUID missingId = channelIds.stream()
            .filter(id -> !existingIds.contains(id))
            .findFirst()
            .orElseThrow();
        throw new ChannelNotFoundException(missingId);
      }
    }
  }

  /**
   * 성과파일이 유효한지 확인한다.
   *
   * @throws OnboardingBusinessException 파일 확인에 실패한 경우(PERFORMANCE_FILE_INVALID)
   */
  private List<String> verifyPerformanceFiles(List<String> rawFileKeys) {
    return rawFileKeys.stream().map(this::verifyPerformanceFile).toList();
  }

  private String verifyPerformanceFile(String rawFileKey) {
    try {
      return performanceFileStorage.verify(rawFileKey);
    } catch (InvalidPerformanceFileException ignored) {
      throw new OnboardingBusinessException(OnboardingErrorCode.PERFORMANCE_FILE_INVALID);
    }
  }

  /**
   * 성과파일의 삭제 방지 태그를 지운다.
   */
  private void confirmPerformanceFiles(List<String> rawFileKeys) {
    rawFileKeys.forEach(this::confirmPerformanceFile);
  }

  private void confirmPerformanceFile(String key) {
    try {
      performanceFileStorage.confirm(key);
    } catch (PerformanceFileStorageException e) {
      log.error("성과파일 태그 확정에 실패했습니다. 1일 뒤 lifecycle로 객체가 삭제될 수 있습니다. "
          + "key={}", key, e);
    }
  }
}
