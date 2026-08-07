package chaeso.zip.server.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * IP 기준 rate limit을 적용한다.
 *
 * {@code value}는 {@code app.rate-limit.rules} 설정과 대응하는 정책이어야 한다
 * (대응하는 설정이 없으면 기동 시점에 {@link IllegalStateException} 발생)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {

  RateLimitPolicy value();
}