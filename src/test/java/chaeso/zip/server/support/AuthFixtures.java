package chaeso.zip.server.support;

import chaeso.zip.server.common.security.JwtProperties;
import chaeso.zip.server.user.application.dto.LoginCommand;
import chaeso.zip.server.user.application.dto.SignupCommand;
import chaeso.zip.server.user.domain.EmploymentStatus;
import chaeso.zip.server.user.domain.Occupation;
import chaeso.zip.server.user.domain.User;
import java.time.Duration;

/**
 * 테스트 공용 픽스처. 기본값(happy-path)을 제공하고, 변형이 필요한 테스트만 해당 값을 직접 만든다.
 */
public final class AuthFixtures {

  public static final String EMAIL = "user@chaeso.zip";
  public static final String RAW_PASSWORD = "rawPw";
  public static final String JWT_SECRET = "test-secret-0123456789-0123456789-0123456789";

  private AuthFixtures() {
  }

  public static SignupCommand signupCommand() {
    return new SignupCommand(EMAIL, RAW_PASSWORD, "채소러버", EmploymentStatus.EMPLOYEE,
        null, null, true, "v1.0", false);
  }

  public static User user() {
    return User.create(EMAIL, "채소러버", EmploymentStatus.EMPLOYEE, null, Occupation.DEVELOPMENT,
        true, "v1.0", false);
  }

  public static LoginCommand loginCommand() {
    return new LoginCommand(EMAIL, RAW_PASSWORD);
  }

  public static JwtProperties jwtProperties() {
    return jwtProperties(Duration.ofMinutes(30), Duration.ofDays(14));
  }

  public static JwtProperties jwtProperties(Duration accessTtl, Duration refreshTtl) {
    return new JwtProperties(JWT_SECRET, accessTtl, refreshTtl);
  }
}
