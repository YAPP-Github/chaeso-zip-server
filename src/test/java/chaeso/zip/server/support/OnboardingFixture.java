package chaeso.zip.server.support;

import chaeso.zip.server.channel.domain.vo.AgeBand;
import chaeso.zip.server.channel.domain.vo.CampaignObjective;
import chaeso.zip.server.channel.domain.vo.Category;
import chaeso.zip.server.onboarding.application.dto.AdHistoryCommand;
import chaeso.zip.server.onboarding.application.dto.SubmitOnboardingCommand;
import chaeso.zip.server.onboarding.domain.entity.Onboarding;
import chaeso.zip.server.onboarding.domain.vo.AdExperience;
import chaeso.zip.server.onboarding.domain.vo.CampaignPeriod;
import chaeso.zip.server.onboarding.domain.vo.ServiceType;
import chaeso.zip.server.onboarding.presentation.dto.SubmitOnboardingRequest;
import java.util.List;
import java.util.UUID;

/** 테스트용 온보딩 데이터 생성 헬퍼.*/
public final class OnboardingFixture {

  private static final String SERVICE_NAME = "채소집";
  private static final Category INDUSTRY = Category.SHOPPING_COMMERCE;

  private OnboardingFixture() {
  }

  /** MOBILE_APP/IN_APP_ACTION/300만~1000만원/EXPERIENCED, 20·30대 대상. */
  public static Onboarding onboarding(UUID userId) {
    return Onboarding.create(userId, SERVICE_NAME, INDUSTRY, ServiceType.MOBILE_APP,
        List.of(AgeBand.AGE_20S, AgeBand.AGE_30S), CampaignObjective.IN_APP_ACTION,
        3_000_000L, 10_000_000L, CampaignPeriod.M2_3, AdExperience.EXPERIENCED);
  }

  /** WEB/TRAFFIC/100만~500만원/NONE, 집행 내역 없음. */
  public static SubmitOnboardingCommand submitCommand() {
    return submitCommand(ServiceType.WEB, CampaignObjective.TRAFFIC,
        1_000_000L, 5_000_000L, AdExperience.NONE, List.of());
  }

  public static SubmitOnboardingCommand submitCommand(ServiceType serviceType,
      CampaignObjective campaignObjective, Long budgetMin, Long budgetMax,
      AdExperience adExperience, List<AdHistoryCommand> adHistory) {
    return new SubmitOnboardingCommand(SERVICE_NAME, INDUSTRY, serviceType,
        List.of(AgeBand.AGE_20S), campaignObjective, budgetMin, budgetMax,
        CampaignPeriod.M1, adExperience, adHistory);
  }

  /** WEB/TRAFFIC/300만~1000만원/NONE, 20·30대 대상, 집행 내역 없음.*/
  public static SubmitOnboardingRequest submitRequest() {
    return new SubmitOnboardingRequest(SERVICE_NAME, INDUSTRY, ServiceType.WEB,
        List.of(AgeBand.AGE_20S, AgeBand.AGE_30S), CampaignObjective.TRAFFIC,
        3_000_000L, 10_000_000L, CampaignPeriod.M2_3, AdExperience.NONE, List.of());
  }
}
