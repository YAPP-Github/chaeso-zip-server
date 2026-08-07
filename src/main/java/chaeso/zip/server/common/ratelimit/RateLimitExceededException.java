package chaeso.zip.server.common.ratelimit;

import chaeso.zip.server.common.exception.BusinessException;
import chaeso.zip.server.common.exception.CommonErrorCode;
import chaeso.zip.server.common.exception.RetryAfterAware;
import java.time.Duration;
import lombok.Getter;

/** rate limit 초과 시 던지는 예외.
 *
 * {@link CommonErrorCode#TOO_MANY_REQUESTS} 로 고정
 */
@Getter
public class RateLimitExceededException extends BusinessException implements RetryAfterAware {

  private final transient Duration retryAfter;

  public RateLimitExceededException(Duration retryAfter) {
    super(CommonErrorCode.TOO_MANY_REQUESTS);
    this.retryAfter = retryAfter;
  }
}