package chaeso.zip.server.onboarding.application.dto;

import chaeso.zip.server.channel.domain.vo.AgeBand;
import chaeso.zip.server.channel.domain.vo.CampaignObjective;
import chaeso.zip.server.channel.domain.vo.Category;
import chaeso.zip.server.onboarding.domain.vo.AdExperience;
import chaeso.zip.server.onboarding.domain.vo.CampaignPeriod;
import chaeso.zip.server.onboarding.domain.vo.ServiceType;
import java.util.List;

/**
 * 온보딩 제출 유스케이스의 입력 커맨드.
 */
public record SubmitOnboardingCommand(
    String serviceName,
    Category industry,
    ServiceType serviceType,
    List<AgeBand> targetAgeBands,
    CampaignObjective campaignObjective,
    Long budgetMin,
    Long budgetMax,
    CampaignPeriod period,
    AdExperience adExperience,
    List<AdHistoryCommand> adHistory) {
}
