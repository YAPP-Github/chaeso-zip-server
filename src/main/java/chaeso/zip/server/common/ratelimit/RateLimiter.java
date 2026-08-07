package chaeso.zip.server.common.ratelimit;

/**
 * Rate limit 포트.
 */
public interface RateLimiter {

  RateLimitResult check(String key, RateLimitRule rule);

  RateLimitResult tryConsume(String key, RateLimitRule rule);

  void reset(String key, RateLimitRule rule);
}
