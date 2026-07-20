package chaeso.zip.server.channel.domain.vo;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum CampaignObjective {
  AWARENESS("브랜드 인지·노출 확대"),
  VIDEO_VIEW("영상 조회·바이럴 확산"),
  TRAFFIC("클릭·트래픽 유입"),
  LEAD("회원가입·리드 수집"),
  CONVERSION("구매·결제 전환"),

  // 앱 선택 시
  APP_INSTALL("앱 설치"),
  IN_APP_ACTION("인앱 구매·활동");

  private final String description;
}
