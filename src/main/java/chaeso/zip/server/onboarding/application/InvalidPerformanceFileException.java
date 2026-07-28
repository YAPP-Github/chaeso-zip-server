package chaeso.zip.server.onboarding.application;

/**
 * 성과 파일 검증 또는 접근 실패 시 발생하는 저장소 예외.
 */
public class InvalidPerformanceFileException extends RuntimeException {

  public InvalidPerformanceFileException(String message) {
    super(message);
  }

  public InvalidPerformanceFileException(String message, Throwable cause) {
    super(message, cause);
  }
}
