package chaeso.zip.server.auth.infrastructure.ratelimit;

import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * IP별 로그인 수단 조회 횟수 제한 (고정 윈도우).
 * 키 하나({@code login-methods-rl:{ip}})에 호출 수를 세고, INCR 직후 첫 호출에만 PEXPIRE 를 건다.
 */
@Component
public class LoginMethodLookupLimiter {

  private static final String KEY_PREFIX = "login-methods-rl:";
  private static final RedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>(
      "local count = redis.call('INCR', KEYS[1]) "
          + "if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end "
          + "if count > tonumber(ARGV[2]) then return 0 end "
          + "return 1",
      Long.class);

  private final StringRedisTemplate redis;
  private final int maxAttempts;
  private final long windowMillis;

  public LoginMethodLookupLimiter(StringRedisTemplate redis,
      LoginMethodLookupProperties properties) {
    this.redis = redis;
    this.maxAttempts = properties.maxAttempts();
    this.windowMillis = properties.window().toMillis();
  }

  public boolean tryAcquire(String clientIp) {
    Long allowed = redis.execute(
        ACQUIRE_SCRIPT,
        List.of(KEY_PREFIX + clientIp),
        String.valueOf(windowMillis),
        String.valueOf(maxAttempts));
    return Long.valueOf(1).equals(allowed);
  }
}
