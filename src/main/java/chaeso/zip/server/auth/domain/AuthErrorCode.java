package chaeso.zip.server.auth.domain;

import chaeso.zip.server.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

  INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH-001", "유효하지 않은 토큰입니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
