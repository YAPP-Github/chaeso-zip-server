package chaeso.zip.server.common.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 고정 윈도 방식 Redis 구현체.
 *
 * INCR/PEXPIRE/허용 여부/TTL 계산을 Lua 스크립트 하나로 원자적으로 처리
 */
@Slf4j
@Component
public class RedisRateLimiter implements RateLimiter {

  @SuppressWarnings("rawtypes")
  private static final RedisScript<List> ACQUIRE_SCRIPT = new DefaultRedisScript<>(
      "local count = redis.call('INCR', KEYS[1]) "
          + "if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end "
          + "local ttl = redis.call('PTTL', KEYS[1]) "
          + "if count > tonumber(ARGV[2]) then return {0, ttl} end "
          + "return {1, ttl}",
      List.class);

  private final StringRedisTemplate redis;
  private final MeterRegistry meterRegistry;

  public RedisRateLimiter(StringRedisTemplate redis, MeterRegistry meterRegistry) {
    this.redis = redis;
    this.meterRegistry = meterRegistry;
  }

  @Override
  @SuppressWarnings("unchecked")
  public RateLimitResult tryConsume(String key, RateLimitRule rule) {
    try {
      List<Long> result = redis.execute(
          ACQUIRE_SCRIPT,
          List.of(key),
          String.valueOf(rule.window().toMillis()),
          String.valueOf(rule.limit()));
      boolean allowed = result.get(0) == 1L;
      Duration retryAfter = Duration.ofMillis(result.get(1));
      return allowed ? RateLimitResult.allow() : RateLimitResult.deny(retryAfter);
    } catch (RedisConnectionFailureException | RedisSystemException e) {
      log.warn("Redis 장애(연결 실패 또는 메모리 초과/시스템 오류)로 rate limit을 통과시킵니다 (fail-open). rule={}", rule.name(), e);
      meterRegistry.counter("rate_limit_fail_open_total", "rule", rule.name()).increment();
      return RateLimitResult.allow();
    }
  }
}