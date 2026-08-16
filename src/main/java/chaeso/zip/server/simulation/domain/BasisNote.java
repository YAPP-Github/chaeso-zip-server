package chaeso.zip.server.simulation.domain;

/**
 * 시뮬레이션 결과 산출 근거 고지 문구
 */
public final class BasisNote {

  public static final String COMMON = "매체 소개서 기반 / VAT 별도 가정 / CTR 미제공 시 전체 평균 CTR 적용";

  private static final String SEPARATOR = " / ";
  private static final String QUOTE_REQUIRED = "견적 문의 필요 (등록된 단가 정보 없음)";
  private static final String NO_IMPRESSION_DATA = "노출 정보 미제공 상품 (집행 가능 여부만 판단)";
  private static final String BUDGET_SHORTFALL = "집행 예산 부족";
  private static final String NOT_ALLOCATED = "미집행 (배분 예산 0원)";

  private BasisNote() {
  }

  /** 노출·클릭까지 정상 추정한 항목. */
  public static String common() {
    return COMMON;
  }

  /** 단가 정보가 있는 상품이 없어 추정할 수 없는 매체. */
  public static String quoteRequired() {
    return prefixed(QUOTE_REQUIRED);
  }

  /** 단가는 있으나 노출 정보가 없어 집행 가능 여부만 판단한 매체. */
  public static String noImpressionData() {
    return prefixed(NO_IMPRESSION_DATA);
  }

  /**
   * 배분 예산이 최소 집행 금액(없으면 대표 단가)에 못 미쳐 집행할 수 없는 매체.
   * 부족액은 문구에 넣지 않고 {@code shortfallWon} 으로 따로 준다.
   */
  public static String budgetShortfall() {
    return prefixed(BUDGET_SHORTFALL);
  }

  /** 사용자가 예산을 배분하지 않은 매체. */
  public static String notAllocated() {
    return prefixed(NOT_ALLOCATED);
  }

  private static String prefixed(String reason) {
    return reason + SEPARATOR + COMMON;
  }
}
