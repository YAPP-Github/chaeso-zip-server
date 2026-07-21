package chaeso.zip.server.auth.domain;

import chaeso.zip.server.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

  INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH-001", "유효하지 않은 토큰입니다."),
  EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "AUTH-002", "이미 사용 중인 이메일입니다."),
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH-003", "이메일 또는 비밀번호가 올바르지 않습니다."),
  INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH-004", "유효하지 않은 refresh 토큰입니다."),
  REFRESH_TOKEN_REUSE_DETECTED(HttpStatus.UNAUTHORIZED, "AUTH-005", "재사용이 감지되어 해당 세션이 만료되었습니다. 다시 로그인하세요."),
  EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "AUTH-006", "이메일 인증이 필요합니다."),
  VERIFICATION_CODE_INVALID(HttpStatus.BAD_REQUEST, "AUTH-007", "인증 코드가 올바르지 않거나 만료되었습니다."),
  VERIFICATION_CODE_SEND_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "AUTH-008", "인증 코드는 잠시 후 다시 요청해 주세요."),
  GOOGLE_AUTH_FAILED(HttpStatus.UNAUTHORIZED, "AUTH-009", "Google 인증에 실패했습니다. 다시 시도해 주세요."),
  ACCOUNT_REGISTERED_WITH_GOOGLE(HttpStatus.UNAUTHORIZED, "AUTH-010", "Google 계정으로 가입된 이메일입니다. Google 로그인을 이용해 주세요."),
  GOOGLE_SIGNUP_SESSION_INVALID(HttpStatus.BAD_REQUEST, "AUTH-011", "가입 세션이 만료되었습니다. Google 로그인을 다시 시도해 주세요."),
  LOGIN_METHOD_LOOKUP_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "AUTH-012", "조회 요청이 많습니다. 잠시 후 다시 시도해 주세요.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
