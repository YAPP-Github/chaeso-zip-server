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
  VERIFICATION_CODE_SEND_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "AUTH-008", "인증 코드는 잠시 후 다시 요청해 주세요.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
