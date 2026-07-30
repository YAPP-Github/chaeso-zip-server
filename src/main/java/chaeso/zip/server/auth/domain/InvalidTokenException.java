package chaeso.zip.server.auth.domain;

/**
 * 토큰이 유효하지 않을 때 발생하는 도메인 예외.
 */
@SuppressWarnings("java:S110")
public class InvalidTokenException extends AuthBusinessException {

  public InvalidTokenException(String message) {
    super(AuthErrorCode.INVALID_TOKEN, message);
  }

  public InvalidTokenException(String message, Throwable cause) {
    super(AuthErrorCode.INVALID_TOKEN, message, cause);
  }
}
