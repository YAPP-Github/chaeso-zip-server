package chaeso.zip.server.common.exception;

import java.time.Duration;
import lombok.Getter;

/** 재시도 시점 안내가 필요한 비즈니스 예외.
 *
 * {@code retryAfter} 이후 재시도를 권장하며, {@link GlobalExceptionHandler}가
 * Retry-After 헤더로 응답에 반영한다.
 */
@Getter
public class RetryAfterBusinessException extends BusinessException implements RetryAfterAware {

  private final transient Duration retryAfter;

  public RetryAfterBusinessException(ErrorCode errorCode, Duration retryAfter) {
    super(errorCode);
    this.retryAfter = retryAfter;
  }
}
