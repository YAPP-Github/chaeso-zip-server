package chaeso.zip.server.sample.domain;

import chaeso.zip.server.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 샘플 도메인 전용 에러 코드. 도메인별 에러 코드 정의 컨벤션 예시.
 */
@Getter
@RequiredArgsConstructor
public enum SampleErrorCode implements ErrorCode {

  SAMPLE_NOT_FOUND(HttpStatus.NOT_FOUND, "SAMPLE-001", "샘플을 찾을 수 없습니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
