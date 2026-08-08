package chaeso.zip.server.performance.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import chaeso.zip.server.support.AdPerformanceFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdPerformanceTest {

  @Test
  @DisplayName("분자/분모가 모두 있으면 비율을 계산한다")
  void computesRatiosWhenAllValuesPresent() {
    AdPerformance performance = AdPerformanceFixture.builder().build();

    assertThat(performance.getCtrActual()).isEqualByComparingTo("0.02");
    assertThat(performance.getCpcActual()).isEqualByComparingTo("500");
    assertThat(performance.getCpaActual()).isEqualByComparingTo("100000");
  }

  @Test
  @DisplayName("분자가 없으면 비율은 null이다")
  void returnsNullRatioWhenNumeratorIsNull() {
    AdPerformance performance = AdPerformanceFixture.builder()
        .budgetWon(null)
        .build();

    assertThat(performance.getCtrActual()).isEqualByComparingTo("0.02");
    assertThat(performance.getCpcActual()).isNull();
    assertThat(performance.getCpaActual()).isNull();
  }

  @Test
  @DisplayName("분모가 없으면 비율은 null이다")
  void returnsNullRatioWhenDenominatorIsNull() {
    AdPerformance performance = AdPerformanceFixture.builder()
        .impressions(null)
        .conversions(null)
        .build();

    assertThat(performance.getCtrActual()).isNull();
    assertThat(performance.getCpcActual()).isEqualByComparingTo("500");
    assertThat(performance.getCpaActual()).isNull();
  }

  @Test
  @DisplayName("분모가 0이면 비율은 null이다")
  void returnsNullRatioWhenDenominatorIsZero() {
    AdPerformance performance = AdPerformanceFixture.builder()
        .impressions(0L)
        .clicks(0L)
        .conversions(0L)
        .build();

    assertThat(performance.getCtrActual()).isNull();
    assertThat(performance.getCpcActual()).isNull();
    assertThat(performance.getCpaActual()).isNull();
  }
}
