package chaeso.zip.server.user.domain;

import lombok.RequiredArgsConstructor;

/** 로그인 종류 */
@RequiredArgsConstructor
public enum AuthProvider {
  LOCAL("로컬"),
  GOOGLE("구글");

  private final String description;
}
