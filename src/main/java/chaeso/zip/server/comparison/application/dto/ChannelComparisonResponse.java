package chaeso.zip.server.comparison.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "채널 비교 응답")
public record ChannelComparisonResponse(
    @Schema(description = "채널별 비교 목록", requiredMode = Schema.RequiredMode.REQUIRED)
    List<ChannelComparisonItemResponse> items) {

  /** 선택한 채널의 비교를 마친 결과. */
  public static ChannelComparisonResponse of(List<ChannelComparisonItemResponse> items) {
    return new ChannelComparisonResponse(items);
  }
}
