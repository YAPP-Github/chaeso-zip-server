package chaeso.zip.server.onboarding.application;

import chaeso.zip.server.channel.domain.ChannelNotFoundException;
import chaeso.zip.server.channel.domain.entity.Channel;
import chaeso.zip.server.channel.domain.repository.ChannelRepository;
import chaeso.zip.server.onboarding.application.dto.AdHistoryCommand;
import chaeso.zip.server.onboarding.application.dto.OnboardingSubmitResponse;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 온보딩 애플리케이션 서비스 구현체.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OnboardingServiceImpl implements OnboardingService {

  private static final int MAX_AD_HISTORY_ROWS = 50;

  private final OnboardingRepository onboardingRepository;
  private final AdPerformanceRepository adPerformanceRepository;
  private final OnboardingAdHistorySnapshotRepository onboardingAdHistorySnapshotRepository;
  private final ChannelRepository channelRepository;

  @Override
  @Transactional
  public OnboardingSubmitResponse submit(UUID userId, SubmitOnboardingCommand command) {
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
        command.adExperience());
    validateAdHistory(command.adExperience(), command.adHistory());

    onboardingRepository.findByUserIdAndIsActiveTrue(userId)
        .forEach(Onboarding::deactivate);

    Onboarding saved = saveResponse(response);

    adPerformanceRepository.saveAll(command.adHistory().stream()
        .map(row -> AdPerformance.fromOnboarding(userId, PerfSource.MANUAL, row.channelId(),
            row.channelNameRaw(), row.budgetWon(), row.impressions(), row.clicks(),
            row.conversions(), row.startedAt(), row.endedAt(), null))
        .toList());

    onboardingAdHistorySnapshotRepository.saveAll(command.adHistory().stream()
        .map(row -> OnboardingAdHistorySnapshot.snapshot(saved.getId(), row.channelId(),
            row.channelNameRaw(), row.budgetWon(), row.impressions(), row.clicks(),
            row.conversions(), row.startedAt(), row.endedAt()))
        .toList());

    return OnboardingSubmitResponse.from(saved);
  }

  private Onboarding saveResponse(Onboarding response) {
    try {
      return onboardingRepository.saveAndFlush(response);
    } catch (DataIntegrityViolationException e) {
      throw new OnboardingBusinessException(OnboardingErrorCode.CONCURRENT_SUBMISSION);
    }
  }

  private void validateAdHistory(AdExperience adExperience, List<AdHistoryCommand> adHistory) {
    boolean experienced = adExperience == AdExperience.EXPERIENCED;
    if (experienced == adHistory.isEmpty()) {
      throw new OnboardingBusinessException(OnboardingErrorCode.AD_EXPERIENCE_MISMATCH);
    }
    if (adHistory.size() > MAX_AD_HISTORY_ROWS) {
      throw new OnboardingBusinessException(OnboardingErrorCode.TOO_MANY_AD_HISTORY);
    }

    boolean invertedPeriod = adHistory.stream()
        .anyMatch(row -> row.startedAt() != null && row.endedAt() != null
            && row.endedAt().isBefore(row.startedAt()));
    if (invertedPeriod) {
      throw new OnboardingBusinessException(OnboardingErrorCode.INVALID_AD_PERIOD);
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
}
