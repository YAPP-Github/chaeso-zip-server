package chaeso.zip.server.onboarding.presentation.dto;

import chaeso.zip.server.channel.domain.vo.AgeBand;
import chaeso.zip.server.channel.domain.vo.CampaignObjective;
import chaeso.zip.server.channel.domain.vo.Category;
import chaeso.zip.server.onboarding.application.dto.SubmitOnboardingCommand;
import chaeso.zip.server.onboarding.domain.vo.AdExperience;
import chaeso.zip.server.onboarding.domain.vo.CampaignPeriod;
import chaeso.zip.server.onboarding.domain.vo.ServiceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 온보딩 제출 요청 DTO.
 */
@Schema(description = "온보딩 제출 요청")
public record SubmitOnboardingRequest(
    @Schema(description = "서비스명", example = "채소집",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Size(max = 255) String serviceName,

    @Schema(description = "업종", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull Category industry,

    @Schema(description = "서비스 형태", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull ServiceType serviceType,

    @Schema(description = "주요 연령대. 1개 이상", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty List<AgeBand> targetAgeBands,

    @Schema(description = "광고 목표(단일 선택). 서비스 형태가 앱이면 APP_INSTALL/IN_APP_ACTION 도 선택할 수 있다",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull CampaignObjective campaignObjective,

    @Schema(description = "최소 예산(원)", example = "3000000",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull @PositiveOrZero Long budgetMin,

    @Schema(description = "최대 예산(원)", example = "10000000",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull @PositiveOrZero Long budgetMax,

    @Schema(description = "집행 기간", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull CampaignPeriod period,

    @Schema(description = "집행 경험 여부", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull AdExperience adExperience,

    @Schema(description = "집행 내역. 경험 없음이면 빈 배열",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull @Valid List<AdHistoryRequest> adHistory) {

  public SubmitOnboardingCommand toCommand() {
    return new SubmitOnboardingCommand(
        serviceName,
        industry,
        serviceType,
        targetAgeBands,
        campaignObjective,
        budgetMin,
        budgetMax,
        period,
        adExperience,
        adHistory.stream().map(AdHistoryRequest::toCommand).toList());
  }
}
