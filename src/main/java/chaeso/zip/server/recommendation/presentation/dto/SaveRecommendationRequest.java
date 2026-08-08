package chaeso.zip.server.recommendation.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "채널 추천 결과 저장 요청")
public record SaveRecommendationRequest(
    @Schema(description = "추천의 근거가 된 온보딩 응답 식별자",
        example = "550e8400-e29b-41d4-a716-446655440000",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "온보딩 응답 식별자는 필수입니다")
    UUID onboardingId) {
}
