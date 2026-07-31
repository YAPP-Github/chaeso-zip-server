package chaeso.zip.server.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * IP 기준 rate limit을 적용한다.
 *
 * {@code value}는 {@code app.rate-limit.rules}의 규칙 이름과 정확히 일치해야 한다
 * (불일치 시 요청 시점에 {@link IllegalStateException} 발생)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {

  String value();
}