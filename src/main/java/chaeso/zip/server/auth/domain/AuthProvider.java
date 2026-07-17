package chaeso.zip.server.auth.domain;

import lombok.RequiredArgsConstructor;

/** 로그인 종류 */
@RequiredArgsConstructor
public enum AuthProvider {
  LOCAL("로컬"),
  GOOGLE("구글");

  private final String description;
}
