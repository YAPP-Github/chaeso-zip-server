package chaeso.zip.server.channel.domain.vo;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum PriceType {
  LIST("공시가"),
  SALE("판매가"),
  DISCOUNT("할인가"),
  UNKNOWN("불명");

  private final String description;
}
