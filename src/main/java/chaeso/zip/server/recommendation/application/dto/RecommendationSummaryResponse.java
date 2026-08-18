package chaeso.zip.server.recommendation.application.dto;

import chaeso.zip.server.recommendation.domain.entity.ChannelRecommendation;
import chaeso.zip.server.recommendation.domain.entity.ChannelRecommendationResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "저장된 채널 추천 목록 요약. 채널별 상세는 상세 조회에서 받는다")
public record RecommendationSummaryResponse(
    @Schema(description = "저장된 추천 id", example = "3f8e2b1a-6c4d-4e9a-9f2b-1a2b3c4d5e6f",
        requiredMode = Schema.RequiredMode.REQUIRED)
    UUID id,
    @Schema(description = "저장 요청시 입력받은 서비스명", example = "채소집",
        requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    String serviceName,
    @Schema(description = "저장 시각", requiredMode = Schema.RequiredMode.REQUIRED)
    LocalDateTime createdAt,
    @Schema(description = "추천된 매체명 리스트. 추천 순위 순이며 null 이 아닌 배열",
        example = "[\"11번가 광고\", \"당근마켓 광고\"]",
        requiredMode = Schema.RequiredMode.REQUIRED)
    List<String> channelNames) {

  public static RecommendationSummaryResponse from(ChannelRecommendationResult result,
      List<ChannelRecommendation> items) {
    return new RecommendationSummaryResponse(
        result.getId(),
        result.getServiceName(),
        result.getCreatedAt(),
        items.stream().map(ChannelRecommendation::getChannelName).toList());
  }
}
