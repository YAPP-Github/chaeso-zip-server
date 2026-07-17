package chaeso.zip.server.onboarding.domain.vo;

import lombok.RequiredArgsConstructor;

/** 온보딩: 집행 기간. */
@RequiredArgsConstructor
public enum CampaignPeriod {
  LE_1W("1주 이하"),
  W2_3("2-3주"),
  M1("1개월"),
  M2_3("2-3개월"),
  GE_3M("3개월 이상");

  private final String description;
}
