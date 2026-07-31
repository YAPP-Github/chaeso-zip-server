package chaeso.zip.server.common.ratelimit;

import java.time.Duration;

/** rate limit 규칙 하나.
 *
 * name은 {@code app.rate-limit.rules} 의 키와 1:1로 대응.
 * failOpen이 false면 Redis 장애 시 요청을 차단한다(Fail-Closed).
 */
public record RateLimitRule(String name, int limit, Duration window, boolean failOpen) {
}
