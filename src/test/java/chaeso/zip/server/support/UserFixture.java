package chaeso.zip.server.support;

import chaeso.zip.server.user.domain.ConsentVersions;
import chaeso.zip.server.user.domain.Occupation;
import chaeso.zip.server.user.domain.User;
import java.time.LocalDateTime;

/** 테스트용 User 생성 헬퍼.*/
public final class UserFixture {

  private UserFixture() {
  }

  public static User user() {
    return user("user@chaeso.zip");
  }

  public static User user(String email) {
    return user(email, false);
  }

  public static User user(boolean marketingAgreed) {
    return user("user@chaeso.zip", marketingAgreed);
  }

  public static User user(String email, boolean marketingAgreed) {
    return User.create(email, "채소러버", "채소컴퍼니", Occupation.DEVELOPMENT, true, marketingAgreed,
        consentVersions(), LocalDateTime.now());
  }

  public static ConsentVersions consentVersions() {
    return new ConsentVersions("v1.0");
  }
}