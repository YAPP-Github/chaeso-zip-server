package chaeso.zip.server.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import chaeso.zip.server.support.redis.EmbeddedRedisTest;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@EmbeddedRedisTest(port = 16385)
class RedisRateLimiterTest {

  private static final Duration FAIL_CLOSED_RETRY_AFTER = Duration.ofSeconds(5);
  private static final RateLimitProperties PROPERTIES =
      new RateLimitProperties(Map.of(), FAIL_CLOSED_RETRY_AFTER);

  private RedisRateLimiter limiter;
  private io.micrometer.core.instrument.simple.SimpleMeterRegistry meterRegistry;

  @BeforeEach
  void setUp(StringRedisTemplate template) {
    meterRegistry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
    limiter = new RedisRateLimiter(template, meterRegistry, PROPERTIES);
  }

  @Nested
  @DisplayName("고정 윈도우 요청 허용/차단")
  class TryConsumeTest {

    @Test
    @DisplayName("설정된 한도 횟수까지 요청을 허용하고, 한도를 초과하면 차단한다")
    void limitBoundary() {
      RateLimitRule rule = new RateLimitRule("test-rule", 2, Duration.ofMinutes(1), true);

      assertThat(limiter.tryConsume("ip:203.0.113.7", rule).allowed()).isTrue();
      assertThat(limiter.tryConsume("ip:203.0.113.7", rule).allowed()).isTrue();
      assertThat(limiter.tryConsume("ip:203.0.113.7", rule).allowed()).isFalse();
    }

    @Test
    @DisplayName("요청이 차단되면 남은 윈도우 시간(retryAfter)을 함께 반환한다")
    void deniedResult_hasPositiveRetryAfterWithinWindow() {
      RateLimitRule rule = new RateLimitRule("test-rule", 1, Duration.ofSeconds(30), true);

      limiter.tryConsume("ip:203.0.113.8", rule);
      RateLimitResult result = limiter.tryConsume("ip:203.0.113.8", rule);

      assertThat(result.allowed()).isFalse();
      assertThat(result.retryAfter()).isPositive();
      assertThat(result.retryAfter()).isLessThanOrEqualTo(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("Redis 연결이 끊어지면 요청을 허용(Fail-Open)하고 실패 메트릭을 기록한다")
    void redisDown_failsOpen_andIncrementsMetric() {
      LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
          .commandTimeout(Duration.ofMillis(300))
          .build();
      LettuceConnectionFactory brokenFactory = new LettuceConnectionFactory(
          new RedisStandaloneConfiguration("localhost", 1), clientConfig);
      brokenFactory.afterPropertiesSet();
      StringRedisTemplate brokenTemplate = new StringRedisTemplate(brokenFactory);
      brokenTemplate.afterPropertiesSet();
      RedisRateLimiter brokenLimiter =
          new RedisRateLimiter(brokenTemplate, meterRegistry, PROPERTIES);

      RateLimitResult result = brokenLimiter.tryConsume(
          "ip:203.0.113.9", new RateLimitRule("test-rule", 1, Duration.ofMinutes(1), true));

      assertThat(result.allowed()).isTrue();
      assertThat(meterRegistry.get("rate_limit_fail_total")
          .tag("rule", "test-rule").tag("strategy", "open").counter().count()).isEqualTo(1.0);
      brokenFactory.destroy();
    }

    @Test
    @DisplayName("Redis 메모리가 가득 차 커맨드가 거부(OOM)되면 요청을 허용(Fail-Open)하고 실패 메트릭을 기록한다")
    void redisSystemError_failsOpen_andIncrementsMetric() {
      StringRedisTemplate mockTemplate = mock(StringRedisTemplate.class);
      given(mockTemplate.execute(any(), anyList(), any(), any()))
          .willThrow(new RedisSystemException("OOM command not allowed when used memory > 'maxmemory'", new RuntimeException()));

      RedisRateLimiter systemErrorLimiter =
          new RedisRateLimiter(mockTemplate, meterRegistry, PROPERTIES);

      RateLimitResult result = systemErrorLimiter.tryConsume(
          "ip:203.0.113.10", new RateLimitRule("test-rule", 1, Duration.ofMinutes(1), true));

      assertThat(result.allowed()).isTrue();
      assertThat(meterRegistry.get("rate_limit_fail_total")
          .tag("rule", "test-rule").tag("strategy", "open").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("failOpen이 false인 규칙은 Redis 장애 시 요청을 차단(Fail-Closed)하고 실패 메트릭을 기록한다")
    void redisDown_failClosedRule_blocksAndIncrementsMetric() {
      StringRedisTemplate mockTemplate = mock(StringRedisTemplate.class);
      given(mockTemplate.execute(any(), anyList(), any(), any()))
          .willThrow(new RedisSystemException("OOM command not allowed when used memory > 'maxmemory'", new RuntimeException()));

      RedisRateLimiter systemErrorLimiter =
          new RedisRateLimiter(mockTemplate, meterRegistry, PROPERTIES);

      RateLimitResult result = systemErrorLimiter.tryConsume(
          "ip:203.0.113.11", new RateLimitRule("auth-login", 1, Duration.ofMinutes(1), false));

      assertThat(result.allowed()).isFalse();
      assertThat(result.retryAfter()).isEqualTo(FAIL_CLOSED_RETRY_AFTER);
      assertThat(meterRegistry.get("rate_limit_fail_total")
          .tag("rule", "auth-login").tag("strategy", "closed").counter().count()).isEqualTo(1.0);
    }
  }
}