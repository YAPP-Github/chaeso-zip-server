package chaeso.zip.server.onboarding.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.Month;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdHistoryCommandTest {

  @Test
  @DisplayName("시작일 또는 종료일 중 하나만 있어도 기간 정보로 인정된다")
  void checksPeriodExistence() {
    AdHistoryCommand startOnly = new AdHistoryCommand(
        null, "채널", null, null, null, null, LocalDate.now(), null);
    AdHistoryCommand endOnly = new AdHistoryCommand(
        null, "채널", null, null, null, null, null, LocalDate.now());
    AdHistoryCommand none = new AdHistoryCommand(
        null, "채널", null, null, null, null, null, null);

    assertThat(startOnly.hasPeriod()).isTrue();
    assertThat(endOnly.hasPeriod()).isTrue();
    assertThat(none.hasPeriod()).isFalse();
  }

  @Test
  @DisplayName("수동 입력 지표 개수를 올바르게 계산한다")
  void countsFilledManualFields() {
    // 예산(1) + 기간(1) + 노출수(1) = 3
    AdHistoryCommand command = new AdHistoryCommand(
        null, "채널", 10000L, 500L, null, null,
        LocalDate.of(2025, Month.MAY, 1), null);

    assertThat(command.countFilledManualFields()).isEqualTo(3);
  }
}
