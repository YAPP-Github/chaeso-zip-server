package chaeso.zip.server.comparison.application.dto;

import chaeso.zip.server.comparison.domain.entity.ChannelComparison;
import chaeso.zip.server.comparison.domain.entity.ChannelComparisonItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(description = "저장된 채널 비교 목록 요약")
public record ChannelComparisonSummaryResponse(
    @Schema(description = "저장된 채널 비교 id", requiredMode = Schema.RequiredMode.REQUIRED)
    UUID id,
    @Schema(description = "서비스명", requiredMode = Schema.RequiredMode.REQUIRED)
    String serviceName,
    @Schema(description = "저장 시각", requiredMode = Schema.RequiredMode.REQUIRED)
    LocalDateTime createdAt,
    @Schema(description = "비교한 매체명 리스트. 온보딩 있으면 적합도(추천)순, 없으면 저장 당시 요청 순서",
        requiredMode = Schema.RequiredMode.REQUIRED)
    List<String> channelNames) {

  /**
   * 온보딩이 있으면 온보딩의 서비스명, 없으면 저장 당시 입력한 서비스명을 사용
   */
  public static ChannelComparisonSummaryResponse from(ChannelComparison comparison,
      List<ChannelComparisonItem> items, Map<UUID, String> onboardingServiceNames) {
    String resolvedServiceName = comparison.getOnboardingId() == null
        ? comparison.getServiceName()
        : onboardingServiceNames.get(comparison.getOnboardingId());
    return new ChannelComparisonSummaryResponse(
        comparison.getId(),
        resolvedServiceName,
        comparison.getCreatedAt(),
        items.stream().map(ChannelComparisonItem::getChannelName).toList());
  }
}
