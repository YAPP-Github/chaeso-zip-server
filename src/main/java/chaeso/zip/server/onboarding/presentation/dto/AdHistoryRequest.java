package chaeso.zip.server.onboarding.presentation.dto;

import chaeso.zip.server.onboarding.application.dto.AdHistoryCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * 과거 광고 집행 실적 수동입력 요청 DTO.
 */
@Schema(description = "과거 광고 집행 실적 수동입력 1건")
public record AdHistoryRequest(
    @Schema(description = "카탈로그 채널 id. 검색바에서 고른 경우에만 보낸다",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
    UUID channelId,

    @Schema(description = "채널명 원문", example = "인스타그램",
        requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank @Size(max = 255) String channelNameRaw,

    @Schema(description = "집행 예산(원)", example = "3000000",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
    @PositiveOrZero Long budgetWon,

    @Schema(description = "노출수", example = "250000",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
    @PositiveOrZero Long impressions,

    @Schema(description = "클릭수", example = "3000",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
    @PositiveOrZero Long clicks,

    @Schema(description = "전환수", example = "120",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
    @PositiveOrZero Long conversions,

    @Schema(description = "집행 기간(일수). 오늘 기준 최근 N일", example = "30",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
    @Positive @Max(3650) Integer periodDays) {

  /** periodDays를 오늘을 종료일로 삼아 시작일/종료일로 환산. periodDays가 없으면 둘 다 {@code null}. */
  public AdHistoryCommand toCommand() {
    LocalDate endedAt = periodDays == null ? null : LocalDate.now(ZoneOffset.UTC);
    LocalDate startedAt = periodDays == null ? null : endedAt.minusDays((long) periodDays - 1);
    return new AdHistoryCommand(
        channelId, channelNameRaw, budgetWon, impressions, clicks, conversions,
        startedAt, endedAt);
  }
}
