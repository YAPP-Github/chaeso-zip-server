package chaeso.zip.server.auth.infrastructure.jwt;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Refresh Token 세션(family)을 Redis에 저장한다.
 * {@code refresh:{userId}:{familyId}} 키 하나에 {@code {jti}|{family 절대만료 epoch millis}} 값을 저장해서,
 * 회전은 값 교체로, family 폐기는 키 삭제로 처리한다.
 * rotate 는 jti 검증과 교체를 Lua 스크립트로 원자적으로 수행한다.
 * 키 TTL 은 min(refresh-ttl, 절대만료까지 남은 시간)으로 설정되어 회전할 때마다 갱신되지만 절대만료 시각은 넘지 않는다.
 * refresh JWT의 exp 는 Redis 조회 전에 빠르게 거절하기 위한 용도라 실제 만료보다 더 길게 설정될 수 있다.
 */
@Component
public class RefreshTokenStore {

  /** 회전 시도 결과. REUSED 는 재사용을 탐지해 family 를 폐기했다는 뜻. */
  public enum RotateResult {
    ROTATED,
    INVALID,
    REUSED
  }

  /** 회전 결과. {@code ttl} 은 ROTATED 일 때 키에 걸린 TTL 이고, 나머지 결과에서는 null 이다. */
  public record RotateOutcome(RotateResult result, Duration ttl) {

  }

  private static final String KEY_PREFIX = "refresh:";
  private static final String DELIMITER = "|";
  private static final long REUSED = -2L;

  /**
   * 값은 마지막 {@code |} 기준으로 자른다. 첫 구분자로 자르면 구분자를 포함한 jti 가 쪼개져
   * 정상 토큰이 재사용으로 오탐될 수 있다.
   * deadline 비교에는 tonumber 를 쓰되 저장은 원본 문자열 그대로 한다. Lua(5.1)에서 큰 수를
   * 문자열로 되돌리면 표기가 달라질 수 있기 때문이다.
   */
  private static final RedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>(
          "local current = redis.call('GET', KEYS[1]) "
                  + "if current == false then return -1 end "
                  + "local jti, deadline = string.match(current, '^(.*)|([^|]*)$') "
                  + "if jti == nil then redis.call('DEL', KEYS[1]) return -1 end "
                  + "if jti ~= ARGV[1] then redis.call('DEL', KEYS[1]) return -2 end "
                  + "local remaining = tonumber(deadline) - tonumber(ARGV[3]) "
                  + "local px = tonumber(ARGV[4]) "
                  + "if remaining < px then px = remaining end "
                  + "px = math.floor(px) "
                  + "if px < 1 then redis.call('DEL', KEYS[1]) return -1 end "
                  + "redis.call('SET', KEYS[1], ARGV[2] .. '|' .. deadline, 'PX', tostring(px)) "
                  + "return px",
          Long.class);

  private final StringRedisTemplate redis;
  private final Duration refreshTtl;
  private final Duration absoluteTtl;
  private final Clock clock;

  @Autowired
  public RefreshTokenStore(StringRedisTemplate redis, JwtProperties properties) {
    this(redis, properties, Clock.systemUTC());
  }

  RefreshTokenStore(StringRedisTemplate redis, JwtProperties properties, Clock clock) {
    this.redis = redis;
    this.refreshTtl = properties.refreshTtl();
    this.absoluteTtl = properties.refreshAbsoluteTtl();
    this.clock = clock;
  }

  /**
   * 로그인으로 새 family 를 연다. 절대만료 시각은 이 시점부터 계산한다.
   * 적용한 키 TTL 을 반환한다.
   */
  public Duration save(UUID userId, String familyId, String jti) {
    long deadline = clock.instant().plus(absoluteTtl).toEpochMilli();
    Duration ttl = refreshTtl.compareTo(absoluteTtl) <= 0 ? refreshTtl : absoluteTtl;
    redis.opsForValue().set(key(userId, familyId), jti + DELIMITER + deadline, ttl);
    return ttl;
  }

  /**
   * {@code oldJti} 가 현재 값과 일치할 때만 {@code newJti} 로 교체한다.
   * 일치하지 않으면 재사용으로 간주해 family 를 폐기한다.
   */
  public RotateOutcome rotate(UUID userId, String familyId, String oldJti, String newJti) {
    Long result = redis.execute(
            ROTATE_SCRIPT,
            List.of(key(userId, familyId)),
            oldJti,
            newJti,
            String.valueOf(clock.instant().toEpochMilli()),
            String.valueOf(refreshTtl.toMillis()));
    if (result > 0) {
      return new RotateOutcome(RotateResult.ROTATED, Duration.ofMillis(result));
    }
    RotateResult failure =
            Long.valueOf(REUSED).equals(result) ? RotateResult.REUSED : RotateResult.INVALID;
    return new RotateOutcome(failure, null);
  }

  /** family 세션을 폐기한다. 존재하지 않는 세션이어도 그냥 통과한다(로그아웃 멱등성). */
  public void revoke(UUID userId, String familyId) {
    redis.delete(key(userId, familyId));
  }

  private String key(UUID userId, String familyId) {
    return KEY_PREFIX + userId + ":" + familyId;
  }
}
