package chaeso.zip.server.channel.domain.vo;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum PricingModel {
  CPM("노출당 과금"),
  CPC("클릭당 과금"),
  CPA("행동당 과금"),
  CPI("설치당 과금"),
  CPV("조회당 과금"),
  CPP("기간당 과금"),
  DB("DB수집단가"),
  SLOT("구좌"),
  FLAT("월고정"),
  PACKAGE("패키지"),
  PER_UNIT("장당"),
  OTHER("기타");

  private final String description;
}
