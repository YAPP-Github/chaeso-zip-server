package chaeso.zip.server.onboarding.application.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 과거 광고 집행 수동 실적 입력 커맨드.
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

  public boolean hasPeriod() {
    return startedAt != null || endedAt != null;
  }

  /**
   * 예산/기간/노출수/클릭수/전환수 중 값이 채워진 항목 수를 계산한다.
   */
  public int countFilledManualFields() {
    int count = 0;
    if (budgetWon != null) {
      count++;
    }
    if (hasPeriod()) {
      count++;
    }
    if (impressions != null) {
      count++;
    }
    if (clicks != null) {
      count++;
    }
    if (conversions != null) {
      count++;
    }
    return count;
  }
}
