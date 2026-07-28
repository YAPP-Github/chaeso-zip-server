package chaeso.zip.server.onboarding.application;

/**
 * 성과 파일 저장소 인프라 호출(예: S3)이 실패했을 때 발생하는 예외.
 */
public class PerformanceFileStorageException extends RuntimeException {

  public PerformanceFileStorageException(String message, Throwable cause) {
    super(message, cause);
  }
}
