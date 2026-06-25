package chaeso.zip.server.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 에러 코드 추상화. 공통 에러는 {@link CommonErrorCode}, 도메인별 에러는 각 도메인 패키지의
 * {@code ErrorCode} 구현 enum 으로 정의한다.
 *
 * <p>코드 규칙: {@code <도메인 약어>-<일련번호>} (예: {@code C-001}, {@code SAMPLE-001}).
 */
public interface ErrorCode {

  HttpStatus getHttpStatus();

  String getCode();

  String getMessage();
}
