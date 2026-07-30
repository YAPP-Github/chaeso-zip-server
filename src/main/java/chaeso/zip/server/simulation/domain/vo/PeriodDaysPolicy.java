package chaeso.zip.server.simulation.domain.vo;

import chaeso.zip.server.onboarding.domain.vo.CampaignPeriod;

/**
 * 집행 기간 구간을 노출·클릭 계산에 쓸 대표 일수로 바꾼다.
 *
 * 추정에 쓰기 위한 정책이며, 프론트의 하루 예산 계산과 반드시 같은 값을 써야한다.
 */
public final class PeriodDaysPolicy {

  /** 1주 이하 → 7일. 구간 상한. */
  public static final int LE_1W_DAYS = 7;

  /** 2-3주 → 17일. 중간값 17.5 를 반올림. */
  public static final int W2_3_DAYS = 17;

  /** 1개월 → 30일. */
  public static final int M1_DAYS = 30;

  /** 2-3개월 → 75일. 중간값 2.5개월. */
  public static final int M2_3_DAYS = 75;

  /** 3개월 이상 → 90일. 상한이 없어 하한으로 근사. */
  public static final int GE_3M_DAYS = 90;

  private PeriodDaysPolicy() {
  }

  /** 구간의 대표 일수. */
  public static int daysOf(CampaignPeriod period) {
    return switch (period) {
      case LE_1W -> LE_1W_DAYS;
      case W2_3 -> W2_3_DAYS;
      case M1 -> M1_DAYS;
      case M2_3 -> M2_3_DAYS;
      case GE_3M -> GE_3M_DAYS;
    };
  }
}
