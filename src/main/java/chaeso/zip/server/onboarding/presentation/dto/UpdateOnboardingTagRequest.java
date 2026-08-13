package chaeso.zip.server.onboarding.presentation.dto;

import chaeso.zip.server.channel.domain.vo.AgeBand;
import chaeso.zip.server.channel.domain.vo.CampaignObjective;
import chaeso.zip.server.channel.domain.vo.Category;
import chaeso.zip.server.onboarding.application.dto.UpdateOnboardingTagCommand;
import chaeso.zip.server.onboarding.domain.vo.CampaignPeriod;
import chaeso.zip.server.onboarding.domain.vo.ServiceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 최신 집행 온보딩 태그 수정 요청 DTO.
 */
@Schema(description = "최신 집행 온보딩 태그 수정 요청")
public record UpdateOnboardingTagRequest(
    @Schema(description = "업종", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull Category industry,

    @Schema(description = "서비스 형태", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull ServiceType serviceType,

    @Schema(description = "주요 연령대. 1개 이상. 잘 모르겠어요는 UNDECIDED 단독 선택",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty List<AgeBand> targetAgeBands,

    @Schema(description = "광고 목표(단일 선택). 앱이면 APP_INSTALL/IN_APP_ACTION도 가능",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull CampaignObjective campaignObjective,

    @Schema(description = "최소 예산(원). 0 이상 1,000만 이하", example = "2000000",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull @Min(0) @Max(10_000_000) Long budgetMin,

    @Schema(description = "최대 예산(원). 0 이상 1,000만 이하", example = "10000000",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull @Min(0) @Max(10_000_000) Long budgetMax,

    @Schema(description = "집행 기간", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull CampaignPeriod period
) {

  public UpdateOnboardingTagCommand toCommand() {
    return new UpdateOnboardingTagCommand(
        industry,
        serviceType,
        targetAgeBands,
        campaignObjective,
        budgetMin,
        budgetMax,
        period
    );
  }
}
