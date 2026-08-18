package chaeso.zip.server.recommendation.application.dto;

import chaeso.zip.server.recommendation.domain.RecommendationSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "저장된 채널 추천")
public record SavedRecommendationResponse(
    @Schema(description = "저장된 추천 id",
        example = "3f8e2b1a-6c4d-4e9a-9f2b-1a2b3c4d5e6f",
        requiredMode = Schema.RequiredMode.REQUIRED)
    UUID id,
    @Schema(description = "추천의 근거가 된 온보딩 응답 식별자",
        example = "550e8400-e29b-41d4-a716-446655440000",
        requiredMode = Schema.RequiredMode.REQUIRED)
    UUID onboardingId,
    @Schema(description = "저장된 채널 수. 추천된 채널이 없으면 0",
        example = "8", requiredMode = Schema.RequiredMode.REQUIRED)
    int channelCount,
    @Schema(description = "저장된 추천 채널. 적합도 순이며 순위는 배열 순서와 같다. null 이 아닌 배열",
        requiredMode = Schema.RequiredMode.REQUIRED)
    List<RecommendationItemResponse> items) {

  public static SavedRecommendationResponse of(UUID id, UUID onboardingId,
      List<RecommendationSnapshot> snapshots) {
    return new SavedRecommendationResponse(id, onboardingId, snapshots.size(),
        snapshots.stream().map(RecommendationItemResponse::from).toList());
  }
}
