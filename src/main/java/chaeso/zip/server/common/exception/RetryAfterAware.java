package chaeso.zip.server.common.exception;

import java.time.Duration;

/** Retry-After 응답 헤더에 사용할 재시도 대기 시간을 제공한다. */
public interface RetryAfterAware {

  Duration getRetryAfter();
}
