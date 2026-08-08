package chaeso.zip.server.channel.application.dto;

import chaeso.zip.server.channel.domain.vo.CampaignObjective;
import chaeso.zip.server.channel.domain.vo.Category;
import chaeso.zip.server.onboarding.domain.entity.Onboarding;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "추천 근거가 된 온보딩 선택지")
public record RecommendationBasisResponse(
    @Schema(description = "광고 목표 코드값", example = "TRAFFIC",
        requiredMode = Schema.RequiredMode.REQUIRED)
    CampaignObjective objective,
    @Schema(description = "업종 코드값", example = "SHOPPING_COMMERCE",
        requiredMode = Schema.RequiredMode.REQUIRED)
    Category category,
    @Schema(description = "예산 하한(원)", example = "1000000",
        requiredMode = Schema.RequiredMode.REQUIRED)
    Long budgetMin,
    @Schema(description = "예산 상한(원)", example = "3000000",
        requiredMode = Schema.RequiredMode.REQUIRED)
    Long budgetMax) {

  public static RecommendationBasisResponse from(Onboarding onboarding) {
    return new RecommendationBasisResponse(
        onboarding.getCampaignObjective(),
        onboarding.getIndustry(),
        onboarding.getBudgetMin(),
        onboarding.getBudgetMax());
  }
}
