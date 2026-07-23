package chaeso.zip.server.onboarding.presentation.dto;

import chaeso.zip.server.onboarding.application.dto.AdHistoryCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 과거 광고 집행 실적 요청 DTO.
 */
@Schema(description = "과거 광고 집행 실적 1건")
public record AdHistoryRequest(
    @Schema(description = "카탈로그 채널 id. 검색바에서 고른 경우에만 보낸다", nullable = true)
    UUID channelId,

    @Schema(description = "채널명 원문", example = "인스타그램",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Size(max = 255) String channelNameRaw,

    @Schema(description = "집행 예산(원)", example = "3000000", nullable = true)
    @PositiveOrZero Long budgetWon,

    @Schema(description = "노출수", example = "250000", nullable = true)
    @PositiveOrZero Long impressions,

    @Schema(description = "클릭수", example = "3000", nullable = true)
    @PositiveOrZero Long clicks,

    @Schema(description = "전환수", example = "120", nullable = true)
    @PositiveOrZero Long conversions,

    @Schema(description = "집행 시작일", example = "2025-03-01", nullable = true)
    LocalDate startedAt,

    @Schema(description = "집행 종료일", example = "2025-05-31", nullable = true)
    LocalDate endedAt) {

  public AdHistoryCommand toCommand() {
    return new AdHistoryCommand(
        channelId, channelNameRaw, budgetWon, impressions, clicks, conversions,
        startedAt, endedAt);
  }
}
