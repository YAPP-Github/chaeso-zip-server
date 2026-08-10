package chaeso.zip.server.auth.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import chaeso.zip.server.support.redis.EmbeddedRedisTest;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

@EmbeddedRedisTest(port = 16384)
class LoginMethodLookupLimiterTest {

  private static final String IP = "203.0.113.7";

  private StringRedisTemplate template;

  @BeforeEach
  void setUp(StringRedisTemplate template) {
    this.template = template;
  }

  private LoginMethodLookupLimiter limiter(int maxAttempts, Duration window) {
    return new LoginMethodLookupLimiter(
        template, new LoginMethodLookupProperties(maxAttempts, window));
  }

  @Test
  @DisplayName("한도까지는 허용하고 한도를 넘은 호출부터 거부한다")
  void limitBoundary() {
    LoginMethodLookupLimiter limiter = limiter(2, Duration.ofMinutes(1));

    assertThat(limiter.tryAcquire(IP)).isTrue();
    assertThat(limiter.tryAcquire(IP)).isTrue();
    assertThat(limiter.tryAcquire(IP)).isFalse();
  }

  @Test
  @DisplayName("첫 호출에서 윈도우 TTL 이 설정된다")
  void firstCall_setsTtl() {
    LoginMethodLookupLimiter limiter = limiter(5, Duration.ofSeconds(30));
    limiter.tryAcquire(IP);

    Long ttl = template.getExpire("login-methods-rl:" + IP);
    assertThat(ttl).isGreaterThan(0L).isLessThanOrEqualTo(30L);
  }
}
