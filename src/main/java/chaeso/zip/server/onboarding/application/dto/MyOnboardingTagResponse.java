package chaeso.zip.server.onboarding.application.dto;

import chaeso.zip.server.channel.domain.vo.AgeBand;
import chaeso.zip.server.channel.domain.vo.CampaignObjective;
import chaeso.zip.server.channel.domain.vo.Category;
import chaeso.zip.server.onboarding.domain.entity.Onboarding;
import chaeso.zip.server.onboarding.domain.vo.AdExperience;
import chaeso.zip.server.onboarding.domain.vo.CampaignPeriod;
import chaeso.zip.server.onboarding.domain.vo.ServiceType;
import java.util.List;
import java.util.UUID;

/**
 * 마이페이지 최신 집행 온보딩 태그 조회 응답 DTO.
 */
public record MyOnboardingTagResponse(
    boolean hasOnboarding,
    UUID onboardingId,
    String serviceName,
    Category industry,
    ServiceType serviceType,
    List<AgeBand> targetAgeBands,
    CampaignObjective campaignObjective,
    Long budgetMin,
    Long budgetMax,
    CampaignPeriod period,
    AdExperience adExperience
) {

  /**
   * 온보딩 기록이 없는 유저용 빈 응답 객체를 생성한다.
   *
   * @return hasOnboarding이 false인 빈 MyOnboardingTagResponse 객체
   */
  public static MyOnboardingTagResponse empty() {
    return new MyOnboardingTagResponse(false, null, null, null, null, null, null, null, null, null, null);
  }

  /**
   * 온보딩 엔티티를 마이페이지 온보딩 태그 응답 DTO로 변환한다.
   *
   * @param onboarding 온보딩 엔티티 (null인 경우 empty() 반환)
   * @return 변환된 MyOnboardingTagResponse 객체
   */
  public static MyOnboardingTagResponse from(Onboarding onboarding) {
    if (onboarding == null) {
      return empty();
    }
    return new MyOnboardingTagResponse(
        true,
        onboarding.getId(),
        onboarding.getServiceName(),
        onboarding.getIndustry(),
        onboarding.getServiceType(),
        onboarding.getTargetAgeBands(),
        onboarding.getCampaignObjective(),
        onboarding.getBudgetMin(),
        onboarding.getBudgetMax(),
        onboarding.getPeriod(),
        onboarding.getAdExperience()
    );
  }
}
