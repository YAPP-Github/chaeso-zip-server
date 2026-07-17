package chaeso.zip.server.support;

import chaeso.zip.server.user.domain.ConsentVersions;
import chaeso.zip.server.user.domain.Occupation;
import chaeso.zip.server.user.domain.User;

/** 테스트용 User 생성 헬퍼.*/
public final class UserFixture {

  private UserFixture() {
  }

  public static User user() {
    return user("user@chaeso.zip");
  }

  public static User user(String email) {
    return User.create(email, "채소러버", "채소컴퍼니", Occupation.DEVELOPMENT, true, false,
        consentVersions());
  }

  public static ConsentVersions consentVersions() {
    return new ConsentVersions("v1.0");
  }
}