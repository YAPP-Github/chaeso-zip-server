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
    return Onboarding.createBuilder()
        .userId(userId)
        .serviceName(SERVICE_NAME)
        .industry(INDUSTRY)
        .serviceType(ServiceType.MOBILE_APP)
        .targetAgeBands(List.of(AgeBand.AGE_20S, AgeBand.AGE_30S))
        .campaignObjective(CampaignObjective.IN_APP_ACTION)
        .budgetMin(3_000_000L)
        .budgetMax(10_000_000L)
        .period(CampaignPeriod.M2_3)
        .adExperience(AdExperience.EXPERIENCED)
        .rawFileUrls(List.of())
        .build();
  }

  /**
   * 매칭 축(업종·목표·연령)과 예산·기간을 직접 지정하는 온보딩.
   * MOBILE_APP 이라 모든 광고 목표를 쓸 수 있다.
   */
  public static Onboarding onboarding(Category industry, CampaignObjective campaignObjective,
      List<AgeBand> targetAgeBands, Long budgetMin, Long budgetMax, CampaignPeriod period) {
    return onboarding(null, industry, campaignObjective, targetAgeBands, budgetMin, budgetMax,
        period);
  }

  /** 매칭 축·예산·기간에 제출자까지 지정하는 온보딩. 소유권을 따지는 테스트에 쓴다. */
  public static Onboarding onboarding(UUID userId, Category industry,
      CampaignObjective campaignObjective, List<AgeBand> targetAgeBands, Long budgetMin,
      Long budgetMax, CampaignPeriod period) {
    return Onboarding.createBuilder()
        .userId(userId)
        .serviceName(SERVICE_NAME)
        .industry(industry)
        .serviceType(ServiceType.MOBILE_APP)
        .targetAgeBands(targetAgeBands)
        .campaignObjective(campaignObjective)
        .budgetMin(budgetMin)
        .budgetMax(budgetMax)
        .period(period)
        .adExperience(AdExperience.NONE)
        .rawFileUrls(List.of())
        .build();
  }

  /** WEB/TRAFFIC/100만~500만원/NONE, 집행 내역 없음. */
  public static SubmitOnboardingCommand submitCommand() {
    return submitCommand(ServiceType.WEB, CampaignObjective.TRAFFIC,
        1_000_000L, 5_000_000L, AdExperience.NONE, List.of());
  }

  public static SubmitOnboardingCommand submitCommand(ServiceType serviceType,
      CampaignObjective campaignObjective, Long budgetMin, Long budgetMax,
      AdExperience adExperience, List<AdHistoryCommand> adHistory) {
    return submitCommand(serviceType, campaignObjective, budgetMin, budgetMax, adExperience,
        adHistory, List.of());
  }

  public static SubmitOnboardingCommand submitCommand(ServiceType serviceType,
      CampaignObjective campaignObjective, Long budgetMin, Long budgetMax,
      AdExperience adExperience, List<AdHistoryCommand> adHistory, List<String> rawFileKeys) {
    return submitCommand(serviceType, List.of(AgeBand.AGE_20S), campaignObjective, budgetMin,
        budgetMax, adExperience, adHistory, rawFileKeys);
  }

  /** 연령대를 직접 지정하는 온보딩 제출 커맨드. */
  public static SubmitOnboardingCommand submitCommand(ServiceType serviceType,
      List<AgeBand> targetAgeBands, CampaignObjective campaignObjective, Long budgetMin,
      Long budgetMax, AdExperience adExperience, List<AdHistoryCommand> adHistory,
      List<String> rawFileKeys) {
    return SubmitOnboardingCommand.builder()
        .serviceName(SERVICE_NAME)
        .industry(INDUSTRY)
        .serviceType(serviceType)
        .targetAgeBands(targetAgeBands)
        .campaignObjective(campaignObjective)
        .budgetMin(budgetMin)
        .budgetMax(budgetMax)
        .period(CampaignPeriod.M1)
        .adExperience(adExperience)
        .adHistory(adHistory)
        .rawFileKeys(rawFileKeys)
        .build();
  }

  /** WEB/TRAFFIC/300만~1000만원/NONE, 20·30대 대상, 집행 내역 없음.*/
  public static SubmitOnboardingRequest submitRequest() {
    return submitRequest(3_000_000L, 10_000_000L);
  }

  /** 최소·최대 예산을 직접 지정하는 온보딩 제출 요청. */
  public static SubmitOnboardingRequest submitRequest(Long budgetMin, Long budgetMax) {
    return new SubmitOnboardingRequest(SERVICE_NAME, INDUSTRY, ServiceType.WEB,
        List.of(AgeBand.AGE_20S, AgeBand.AGE_30S), CampaignObjective.TRAFFIC,
        budgetMin, budgetMax, CampaignPeriod.M2_3, AdExperience.NONE, List.of(), List.of());
  }
}
