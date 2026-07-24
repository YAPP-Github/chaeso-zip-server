package chaeso.zip.server.onboarding.application.dto;

import chaeso.zip.server.onboarding.domain.entity.Onboarding;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "온보딩 제출 결과")
public record OnboardingSubmitResponse(
    @Schema(description = "생성된 온보딩 id", requiredMode = Schema.RequiredMode.REQUIRED)
    UUID onboardingId,
    @Schema(description = "생성 시각", requiredMode = Schema.RequiredMode.REQUIRED)
    LocalDateTime createdAt) {

  public static OnboardingSubmitResponse from(Onboarding onboarding) {
    return new OnboardingSubmitResponse(onboarding.getId(), onboarding.getCreatedAt());
  }
}
