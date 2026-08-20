package chaeso.zip.server.comparison.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "저장된 채널 비교")
public record SavedChannelComparisonResponse(
    @Schema(description = "저장된 비교 식별자. 이 값으로 저장된 비교 1건을 가리킨다",
        example = "550e8400-e29b-41d4-a716-446655440000",
        requiredMode = Schema.RequiredMode.REQUIRED)
    UUID comparisonId,
    @Schema(description = "비교에 담긴 채널별 항목. 온보딩이 있으면 적합도순, 없으면 요청 순서",
        requiredMode = Schema.RequiredMode.REQUIRED)
    List<ChannelComparisonItemResponse> items) {

  public static SavedChannelComparisonResponse of(UUID comparisonId,
      List<ChannelComparisonItemResponse> items) {
    return new SavedChannelComparisonResponse(comparisonId, items);
  }
}
