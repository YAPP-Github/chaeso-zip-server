package chaeso.zip.server.common.ratelimit;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 애플리케이션에서 사용하는 rate limit 정책.
 *
 * <p>{@code key}는 {@code app.rate-limit.rules}의 설정 키와 대응한다.
 */
@Getter
@RequiredArgsConstructor
public enum RateLimitPolicy {

  LOGIN_METHOD_LOOKUP("login-method-lookup"),
  AUTH_LOGIN("auth-login"),
  AUTH_LOGIN_EMAIL("auth-login-email"),
  AUTH_SIGNUP("auth-signup"),
  AUTH_EMAIL_CODE("auth-email-code"),
  AUTH_GOOGLE("auth-google"),
  ONBOARDING_SUBMIT("onboarding-submit"),
  ONBOARDING_PRESIGN("onboarding-presign");

  private final String key;
}
