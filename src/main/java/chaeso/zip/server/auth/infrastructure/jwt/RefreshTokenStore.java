package chaeso.zip.server.auth.infrastructure.jwt;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Refresh Token 세션(family)을 Redis에 저장한다.
 *
 * <p>키 구조는 {@code refresh:{userId}:{familyId}}, 값은 {@code {jti}|{절대만료 epoch millis}}이다.
 * {@code rotate()}는 Lua 스크립트로 jti 검증 및 교체를 원자적으로 수행한다.
 *
 * <p>키 TTL은 회전 시마다 갱신되지만 절대 만료 시각을 초과할 수 없으며,
 * Refresh JWT의 {@code exp}는 Redis 조회 전 조기 거절(fast-fail) 용도로 사용한다.
 */
@Component
public class RefreshTokenStore {

  /** 토큰 회전 결과 (REUSED: 토큰 재사용 감지로 세션 폐기). */
  public enum RotateResult {
    ROTATED,
    INVALID,
    REUSED
  }

  /** 세션 회전 결과.
   *
   * {@code ttl}은 ROTATED 시 적용된 키 TTL이며, 그 외 결과에서는 null
   */
  public record RotateOutcome(RotateResult result, Duration ttl) {

  }

  private static final String KEY_PREFIX = "refresh:";
  private static final String DELIMITER = "|";
  private static final long REUSED = -2L;

  /**
   * jti 파싱 오탐을 막기 위해 마지막 {@code |} 기준으로 분할한다.
   * Lua(5.1)의 숫자 변환 오차를 방지하도록 deadline은 원본 문자열로 저장한다.
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

  public RefreshTokenStore(StringRedisTemplate redis, JwtProperties properties, Clock clock) {
    this.redis = redis;
    this.refreshTtl = properties.refreshTtl();
    this.absoluteTtl = properties.refreshAbsoluteTtl();
    this.clock = clock;
  }

  /** 신규 토큰 세션(family)을 생성하여 저장하고, 적용된 키 TTL을 반환한다. */
  public Duration save(UUID userId, String familyId, String jti) {
    long deadline = clock.instant().plus(absoluteTtl).toEpochMilli();
    Duration ttl = refreshTtl.compareTo(absoluteTtl) <= 0 ? refreshTtl : absoluteTtl;
    redis.opsForValue().set(key(userId, familyId), jti + DELIMITER + deadline, ttl);
    return ttl;
  }

  /**
   * {@code oldJti} 검증 성공 시 {@code newJti}로 회전한다.
   * 불일치 시 토큰 재사용으로 판단하여 해당 세션을 폐기한다.
   */
  public RotateOutcome rotate(UUID userId, String familyId, String oldJti, String newJti) {
    Long result = redis.execute(
            ROTATE_SCRIPT,
            List.of(key(userId, familyId)),
            oldJti,
            newJti,
            String.valueOf(clock.instant().toEpochMilli()),
            String.valueOf(refreshTtl.toMillis()));
    if (result != null && result > 0) {
      return new RotateOutcome(RotateResult.ROTATED, Duration.ofMillis(result));
    }
    RotateResult failure =
            Long.valueOf(REUSED).equals(result) ? RotateResult.REUSED : RotateResult.INVALID;
    return new RotateOutcome(failure, null);
  }

  /** 토큰 세션(family)을 폐기한다. 존재하지 않는 세션이어도 멱등성이 보장된다. */
  public void revoke(UUID userId, String familyId) {
    redis.delete(key(userId, familyId));
  }

  private String key(UUID userId, String familyId) {
    return KEY_PREFIX + userId + ":" + familyId;
  }
}
