package chaeso.zip.server.common.security;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 회원가입용 이메일 인증 상태를 Redis에 저장한다.
 * 인증코드는 {@code email-verify:{email}} 키에, 인증 완료 표시는
 * {@code email-verify-ok:{email}} 키에 보관한다.
 */
@Component
public class EmailVerificationCodeStore {

    private static final String CODE_PREFIX = "email-verify:";
    private static final String VERIFIED_PREFIX = "email-verify-ok:";
    private static final RedisScript<Long> VERIFY_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('GET', KEYS[1]) "
                    + "if current == false or current ~= ARGV[1] then return 0 end "
                    + "redis.call('DEL', KEYS[1]) "
                    + "redis.call('SET', KEYS[2], '1', 'PX', ARGV[2]) "
                    + "return 1",
            Long.class);

    private final StringRedisTemplate redis;
    private final Duration codeTtl;
    private final Duration verifiedTtl;

    public EmailVerificationCodeStore(StringRedisTemplate redis, EmailVerificationProperties properties) {
        this.redis = redis;
        this.codeTtl = properties.codeTtl();
        this.verifiedTtl = properties.verifiedTtl();
    }

    public void saveCode(String email, String code) {
        redis.opsForValue().set(CODE_PREFIX + email, code, codeTtl);
    }

    public Optional<String> findCode(String email) {
        return Optional.ofNullable(redis.opsForValue().get(CODE_PREFIX + email));
    }

    public boolean verifyCode(String email, String code) {
        Long result = redis.execute(
                VERIFY_SCRIPT,
                List.of(CODE_PREFIX + email, VERIFIED_PREFIX + email),
                code,
                String.valueOf(verifiedTtl.toMillis()));
        return Long.valueOf(1).equals(result);
    }

    public boolean isVerified(String email) {
        return Boolean.TRUE.equals(redis.hasKey(VERIFIED_PREFIX + email));
    }

    public void clearVerified(String email) {
        redis.delete(VERIFIED_PREFIX + email);
    }
}
