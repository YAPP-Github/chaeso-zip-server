package chaeso.zip.server.auth.domain;

import chaeso.zip.server.common.exception.BusinessException;

/**
 * 인증/인가 도메인에서 발생하는 비즈니스 예외.
 */
public class AuthBusinessException extends BusinessException {

  public AuthBusinessException(AuthErrorCode errorCode) {
    super(errorCode);
  }

  public AuthBusinessException(AuthErrorCode errorCode, String message) {
    super(errorCode, message);
  }

  public AuthBusinessException(AuthErrorCode errorCode, String message, Throwable cause) {
    super(errorCode, message, cause);
  }
}