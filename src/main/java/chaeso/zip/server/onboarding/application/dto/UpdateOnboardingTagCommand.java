package chaeso.zip.server.onboarding.application.dto;

import chaeso.zip.server.channel.domain.vo.AgeBand;
import chaeso.zip.server.channel.domain.vo.CampaignObjective;
import chaeso.zip.server.channel.domain.vo.Category;
import chaeso.zip.server.onboarding.domain.vo.BudgetRange;
import chaeso.zip.server.onboarding.domain.vo.CampaignPeriod;
import chaeso.zip.server.onboarding.domain.vo.ServiceType;
import java.util.List;

/**
 * 마이페이지 최신 집행 온보딩 태그 수정 커맨드 DTO.
 */
public record UpdateOnboardingTagCommand(
    Category industry,
    ServiceType serviceType,
    List<AgeBand> targetAgeBands,
    CampaignObjective campaignObjective,
    BudgetRange budgetRange,
    CampaignPeriod period
) {
}
