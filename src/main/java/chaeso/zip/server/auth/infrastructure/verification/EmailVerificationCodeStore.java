package chaeso.zip.server.auth.infrastructure.verification;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 회원가입용 이메일 인증 상태를 Redis에 저장한다.
 * 인증코드는 {@code email-verify:{email}} 키에, 인증완료 표시는 {@code email-verify-ok:{email}} 키에 보관한다.
 * verifyCode 는 코드 확인, 실패 횟수 증가, 코드 삭제, 인증완료 SET 을 Lua 로 원자적으로 처리한다.
 */
@Component
public class EmailVerificationCodeStore {

  private static final String CODE_PREFIX = "email-verify:";
  private static final String VERIFIED_PREFIX = "email-verify-ok:";
  private static final String ATTEMPTS_PREFIX = "email-verify-att:";
  private static final String COOLDOWN_PREFIX = "email-verify-cd:";
  private static final RedisScript<Long> VERIFY_SCRIPT = new DefaultRedisScript<>(
      "local current = redis.call('GET', KEYS[1]) "
          + "if current == false then return 0 end "
          + "if current ~= ARGV[1] then "
          + "  local attempts = redis.call('INCR', KEYS[3]) "
          + "  if attempts == 1 then redis.call('PEXPIRE', KEYS[3], ARGV[4]) end "
          + "  if attempts >= tonumber(ARGV[3]) then redis.call('DEL', KEYS[1]) end "
          + "  return 0 "
          + "end "
          + "redis.call('DEL', KEYS[1]) "
          + "redis.call('DEL', KEYS[3]) "
          + "redis.call('SET', KEYS[2], '1', 'PX', ARGV[2]) "
          + "return 1",
      Long.class);

  private final StringRedisTemplate redis;
  private final Duration codeTtl;
  private final Duration verifiedTtl;
  private final int maxVerifyAttempts;
  private final Duration sendCooldown;

  public EmailVerificationCodeStore(StringRedisTemplate redis, EmailVerificationProperties properties) {
    this.redis = redis;
    this.codeTtl = properties.codeTtl();
    this.verifiedTtl = properties.verifiedTtl();
    this.maxVerifyAttempts = properties.maxVerifyAttempts();
    this.sendCooldown = properties.sendCooldown();
  }

  public void saveCode(String email, String code) {
    redis.opsForValue().set(CODE_PREFIX + email, code, codeTtl);
    redis.delete(ATTEMPTS_PREFIX + email);
  }

  public Optional<String> findCode(String email) {
    return Optional.ofNullable(redis.opsForValue().get(CODE_PREFIX + email));
  }

  public boolean verifyCode(String email, String code) {
    if (code == null) {
      return false;
    }
    Long result = redis.execute(
        VERIFY_SCRIPT,
        List.of(CODE_PREFIX + email, VERIFIED_PREFIX + email, ATTEMPTS_PREFIX + email),
        code,
        String.valueOf(verifiedTtl.toMillis()),
        String.valueOf(maxVerifyAttempts),
        String.valueOf(codeTtl.toMillis()));
    return Long.valueOf(1).equals(result);
  }

  public boolean isVerified(String email) {
    return Boolean.TRUE.equals(redis.hasKey(VERIFIED_PREFIX + email));
  }

  public void clearVerified(String email) {
    redis.delete(VERIFIED_PREFIX + email);
  }

  /** per-email 발송 쿨다운 슬롯을 선점한다. 이미 쿨다운 중이면 false. */
  public boolean tryAcquireSendSlot(String email) {
    Boolean acquired = redis.opsForValue().setIfAbsent(COOLDOWN_PREFIX + email, "1", sendCooldown);
    return Boolean.TRUE.equals(acquired);
  }

  /** 메일 발송 실패 시 쿨다운 슬롯을 되돌려 즉시 재요청을 허용한다. */
  public void releaseSendSlot(String email) {
    redis.delete(COOLDOWN_PREFIX + email);
  }
}
