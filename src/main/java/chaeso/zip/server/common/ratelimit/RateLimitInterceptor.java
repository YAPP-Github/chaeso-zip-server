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

  // 거부 시 RateLimitExceededException을 던져 GlobalExceptionHandler가 처리
  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) { //NOSONAR
    if (!(handler instanceof HandlerMethod handlerMethod)) {
      return true;
    }
    RateLimited annotation = handlerMethod.getMethodAnnotation(RateLimited.class);
    if (annotation == null) {
      return true;
    }
    RateLimitRule rule = properties.rule(annotation.value());
    String key = rule.name() + ":" + clientIp(request);
    RateLimitResult result = rateLimiter.tryConsume(key, rule);
    if (result != null && !result.allowed()) {
      throw new RateLimitExceededException(result.retryAfter());
    }
    return true;
  }

  /** 프록시/로드밸런서 경유 시 실제 IP 확인을 위해 X-Forwarded-For를 우선 참조한다. */
  private static String clientIp(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}