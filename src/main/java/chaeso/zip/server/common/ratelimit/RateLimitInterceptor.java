package chaeso.zip.server.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * {@link RateLimited} 가 붙은 컨트롤러 메서드에 IP 기준 rate limit을 적용한다.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

  private final RateLimiter rateLimiter;
  private final RateLimitProperties properties;

  public RateLimitInterceptor(RateLimiter rateLimiter, RateLimitProperties properties) {
    this.rateLimiter = rateLimiter;
    this.properties = properties;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!(handler instanceof HandlerMethod handlerMethod)) {
      return true;
    }
    RateLimited annotation = handlerMethod.getMethodAnnotation(RateLimited.class);
    if (annotation == null) {
      return true;
    }
    RateLimitRule rule = properties.rule(annotation.value());
    String key = rule.name() + ":" + request.getRemoteAddr();
    RateLimitResult result = rateLimiter.tryConsume(key, rule);
    if (result != null && !result.allowed()) {
      throw new RateLimitExceededException(result.retryAfter());
    }
    return true;
  }
}