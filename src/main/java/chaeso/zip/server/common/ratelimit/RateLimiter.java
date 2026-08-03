package chaeso.zip.server.common.ratelimit;

/**
 * Rate limit 포트.
 */
public interface RateLimiter {

  RateLimitResult tryConsume(String key, RateLimitRule rule);
}
