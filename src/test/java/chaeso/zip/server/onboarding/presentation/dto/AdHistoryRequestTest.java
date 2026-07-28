package chaeso.zip.server.onboarding.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;

import chaeso.zip.server.onboarding.application.dto.AdHistoryCommand;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdHistoryRequestTest {

  @Test
  @DisplayName("집행 기간(일수)이 있으면 오늘을 종료일로, 그만큼 앞선 날을 시작일로 계산한다")
  void toCommandComputesPeriodFromDays() {
    AdHistoryRequest request = new AdHistoryRequest(null, "인스타그램", 1000L, null, null, null,
        30);

    AdHistoryCommand command = request.toCommand();

    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    assertThat(command.endedAt()).isEqualTo(today);
    assertThat(command.startedAt()).isEqualTo(today.minusDays(29));
  }

  @Test
  @DisplayName("집행 기간이 하루면 시작일과 종료일이 같다")
  void toCommandHandlesSingleDayPeriod() {
    AdHistoryRequest request = new AdHistoryRequest(null, "인스타그램", 1000L, null, null, null,
        1);

    AdHistoryCommand command = request.toCommand();

    assertThat(command.startedAt()).isEqualTo(command.endedAt());
  }

  @Test
  @DisplayName("집행 기간이 없으면 시작일/종료일 모두 null이다")
  void toCommandAllowsNullPeriodDays() {
    AdHistoryRequest request = new AdHistoryRequest(null, "인스타그램", 1000L, null, null, null,
        null);

    AdHistoryCommand command = request.toCommand();

    assertThat(command.startedAt()).isNull();
    assertThat(command.endedAt()).isNull();
  }
}
