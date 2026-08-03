package chaeso.zip.server.estimation.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;

import chaeso.zip.server.onboarding.domain.vo.CampaignPeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class PeriodDaysPolicyTest {

  @ParameterizedTest
  @CsvSource({
      "LE_1W, 7",    // 1주 이하 → 구간 상한
      "W2_3,  17",   // 2-3주 → 중간값 17.5 반올림
      "M1,    30",
      "M2_3,  75",   // 2-3개월 → 중간값 2.5개월
      "GE_3M, 90",   // 3개월 이상 → 상한이 없어 하한으로 근사
  })
  @DisplayName("구간별 대표 일수는 프론트와 합의한 값과 일치한다")
  void mapsEachPeriodToItsRepresentativeDays(CampaignPeriod period, int expectedDays) {
    assertThat(PeriodDaysPolicy.daysOf(period)).isEqualTo(expectedDays);
  }

  @ParameterizedTest
  @EnumSource(CampaignPeriod.class)
  @DisplayName("모든 구간이 양수 일수로 변환된다")
  void everyPeriodMapsToPositiveDays(CampaignPeriod period) {
    // 0 이하가 나오면 EstimationService 가 기간을 거부한다
    assertThat(PeriodDaysPolicy.daysOf(period)).isPositive();
  }

  @Test
  @DisplayName("대표 일수는 구간 순서대로 증가한다")
  void daysIncreaseWithPeriodLength() {
    assertThat(CampaignPeriod.values())
        .extracting(PeriodDaysPolicy::daysOf)
        .isSorted();
  }
}
