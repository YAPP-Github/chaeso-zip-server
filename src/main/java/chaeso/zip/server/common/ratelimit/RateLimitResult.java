package chaeso.zip.server.common.ratelimit;

import java.time.Duration;

/** {@link RateLimiter#tryConsume} 의 결과.
 *
 * 거부 시 {@code retryAfter} 이후 재시도를 권장
 */
public record RateLimitResult(boolean allowed, Duration retryAfter) {

  public static RateLimitResult allow() {
    return new RateLimitResult(true, Duration.ZERO);
  }

  public static RateLimitResult deny(Duration retryAfter) {
    return new RateLimitResult(false, retryAfter);
  }
}
