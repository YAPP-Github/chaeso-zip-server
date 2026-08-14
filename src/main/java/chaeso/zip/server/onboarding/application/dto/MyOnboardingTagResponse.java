package chaeso.zip.server.onboarding.application.dto;

import chaeso.zip.server.channel.domain.vo.AgeBand;
import chaeso.zip.server.channel.domain.vo.CampaignObjective;
import chaeso.zip.server.channel.domain.vo.Category;
import chaeso.zip.server.onboarding.domain.entity.Onboarding;
import chaeso.zip.server.onboarding.domain.vo.AdExperience;
import chaeso.zip.server.onboarding.domain.vo.CampaignPeriod;
import chaeso.zip.server.onboarding.domain.vo.ServiceType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * 마이페이지 최신 집행 온보딩 태그 조회 응답 DTO.
 */
public record MyOnboardingTagResponse(
    @Schema(description = "온보딩 존재 여부", requiredMode = Schema.RequiredMode.REQUIRED)
    boolean hasOnboarding,

    @Schema(description = "온보딩 식별자", requiredMode = Schema.RequiredMode.REQUIRED,
        nullable = true)
    UUID onboardingId,

    @Schema(description = "서비스명", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    String serviceName,

    @Schema(description = "업종", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    Category industry,

    @Schema(description = "서비스 형태", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    ServiceType serviceType,

    @Schema(description = "주요 연령대. 온보딩이 없으면 빈 배열",
        requiredMode = Schema.RequiredMode.REQUIRED)
    List<AgeBand> targetAgeBands,

    @Schema(description = "광고 목표", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    CampaignObjective campaignObjective,

    @Schema(description = "최소 예산(원)", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    Long budgetMin,

    @Schema(description = "최대 예산(원)", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    Long budgetMax,

    @Schema(description = "집행 기간", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    CampaignPeriod period,

    @Schema(description = "집행 경험 여부", requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    AdExperience adExperience
) {

  /**
   * 온보딩 기록이 없는 유저용 빈 응답 객체를 생성한다.
   *
   * @return hasOnboarding이 false인 빈 MyOnboardingTagResponse 객체
   */
  public static MyOnboardingTagResponse empty() {
    return new MyOnboardingTagResponse(
        false, null, null, null, null, List.of(), null, null, null, null, null);
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
        onboarding.getTargetAgeBands() == null ? List.of() : onboarding.getTargetAgeBands(),
        onboarding.getCampaignObjective(),
        onboarding.getBudgetMin(),
        onboarding.getBudgetMax(),
        onboarding.getPeriod(),
        onboarding.getAdExperience()
    );
  }
}
