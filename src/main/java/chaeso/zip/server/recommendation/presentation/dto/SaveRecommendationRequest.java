package chaeso.zip.server.recommendation.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(description = "채널 추천 결과 저장 요청")
public record SaveRecommendationRequest(
    @Schema(description = "추천의 근거가 된 온보딩 응답 식별자",
        example = "550e8400-e29b-41d4-a716-446655440000",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "온보딩 응답 식별자는 필수입니다")
    UUID onboardingId,

    @Schema(description = "광고할 서비스명", example = "채소집", maxLength = 255,
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "서비스명은 필수입니다")
    @Size(max = 255, message = "서비스명은 255자 이하로 입력해 주세요")
    String serviceName) {
}
