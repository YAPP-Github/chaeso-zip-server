package chaeso.zip.server.onboarding.application.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 과거 광고 집행 실적 입력 커맨드.
 */
public record AdHistoryCommand(
    UUID channelId,
    String channelNameRaw,
    Long budgetWon,
    Long impressions,
    Long clicks,
    Long conversions,
    LocalDate startedAt,
    LocalDate endedAt) {
}
