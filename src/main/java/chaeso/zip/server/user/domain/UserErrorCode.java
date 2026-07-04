package chaeso.zip.server.user.domain;

import chaeso.zip.server.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

  EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "USER-001", "이미 사용 중인 이메일입니다."),
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "USER-002", "이메일 또는 비밀번호가 올바르지 않습니다."),
  INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "USER-003", "유효하지 않은 refresh 토큰입니다."),
  REFRESH_TOKEN_REUSE_DETECTED(HttpStatus.UNAUTHORIZED, "USER-004", "재사용이 감지되어 모든 세션이 만료되었습니다. 다시 로그인하세요."),
  EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "USER-005", "이메일 인증이 필요합니다."),
  VERIFICATION_CODE_INVALID(HttpStatus.BAD_REQUEST, "USER-006", "인증 코드가 올바르지 않거나 만료되었습니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
