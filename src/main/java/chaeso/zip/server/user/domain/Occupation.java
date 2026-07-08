package chaeso.zip.server.user.domain;

import lombok.RequiredArgsConstructor;

/** 직무 대분류. 광고주(회원)가 속한 직무 영역. */
@RequiredArgsConstructor
public enum Occupation {
  DEVELOPMENT("개발"),
  DESIGN("디자인"),
  MARKETING("마케팅"),
  PLANNING("기획"),
  SALES("영업"),
  DATA("데이터"),
  MANAGEMENT("경영"),
  ETC("기타");

  private final String description;
}
