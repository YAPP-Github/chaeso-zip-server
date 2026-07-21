package chaeso.zip.server.channel.application.dto;

import chaeso.zip.server.channel.domain.entity.Channel;
import chaeso.zip.server.channel.domain.vo.Category;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "채널 목록 요약")
public record ChannelListItemResponse(
    @Schema(description = "채널 식별자", example = "550e8400-e29b-41d4-a716-446655440000",
        requiredMode = Schema.RequiredMode.REQUIRED)
    UUID id,
    @Schema(description = "채널명", example = "11번가 광고", requiredMode = Schema.RequiredMode.REQUIRED)
    String name,
    @Schema(description = "로고 이미지 URL",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    String logoUrl,
    @Schema(description = "채널 핵심 요약", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    String description,
    @Schema(description = "대표 업종 코드값", example = "SHOPPING_COMMERCE",
        requiredMode = Schema.RequiredMode.REQUIRED)
    Category primaryCategory) {

  public static ChannelListItemResponse from(Channel channel) {
    return new ChannelListItemResponse(
        channel.getId(),
        channel.getName(),
        channel.getLogoUrl(),
        channel.getDescription(),
        channel.getPrimaryCategory());
  }
}
