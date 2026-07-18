package chaeso.zip.server.onboarding.domain.vo;

import lombok.RequiredArgsConstructor;

/** 온보딩: 서비스 형태. */
@RequiredArgsConstructor
public enum ServiceType {
  MOBILE_APP("ios/Android"),
  WEB("pc, 모바일 브라우저"),
  WEB_AND_APP("웹+앱 모두"),
  OTHER("기타");

  private final String description;
}
