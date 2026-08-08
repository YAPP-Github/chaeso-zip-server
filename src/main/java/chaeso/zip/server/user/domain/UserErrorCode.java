package chaeso.zip.server.user.domain;

import chaeso.zip.server.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

  USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER-001", "존재하지 않는 회원입니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
