package chaeso.zip.server.common.security;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Refresh 토큰을 Redis에 저장하고 세션 family 단위로 관리한다.
 * 신규 세션은 {@link #save(UUID, String, String)}로 저장하고, 재발급 시에는
 * {@link #rotate(UUID, String, String, String)}로 jti를 원자적으로 교체한다.
 */
@Component
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "refresh:";
    private static final String FAMILY_INDEX_PREFIX = "refresh-families:";

    private static final RedisScript<Long> SAVE_SCRIPT = new DefaultRedisScript<>(
            "redis.call('SADD', KEYS[2], ARGV[1]) "
                    + "redis.call('PEXPIRE', KEYS[2], ARGV[3]) "
                    + "redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3]) "
                    + "return 1",
            Long.class);

    /**
     * 저장된 jti 가 oldJti 와 일치할 때만 newJti 로 교체(+TTL 갱신)하는 compare-and-set.
     * GET/SET 을 원자적으로 실행해 동시 재발급 요청 중 정확히 하나만 회전에 성공하게 한다.
     * 반환값: 1=회전 성공, 0=jti 불일치(재사용 의심), -1=키 없음(만료/로그아웃).
     */
    private static final RedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('GET', KEYS[1]) "
                    + "if current == false then return -1 end "
                    + "if current ~= ARGV[1] then return 0 end "
                    + "redis.call('SADD', KEYS[2], ARGV[4]) "
                    + "redis.call('PEXPIRE', KEYS[2], ARGV[3]) "
                    + "redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3]) "
                    + "return 1",
            Long.class);

    private static final RedisScript<Long> DELETE_FAMILY_SCRIPT = new DefaultRedisScript<>(
            "local deleted = redis.call('DEL', KEYS[1]) "
                    + "redis.call('SREM', KEYS[2], ARGV[1]) "
                    + "if redis.call('SCARD', KEYS[2]) == 0 then redis.call('DEL', KEYS[2]) end "
                    + "return deleted",
            Long.class);

    public enum RotateResult { ROTATED, REUSE, MISSING }

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RefreshTokenStore(StringRedisTemplate redis, JwtProperties properties) {
        this.redis = redis;
        this.ttl = properties.refreshTtl();
    }

    public void save(UUID userId, String familyId, String jti) {
        redis.execute(SAVE_SCRIPT, List.of(key(userId, familyId), familyIndexKey(userId)),
                familyId, jti, String.valueOf(ttl.toMillis()));
    }

    public RotateResult rotate(UUID userId, String familyId, String oldJti, String newJti) {
        Long result = redis.execute(ROTATE_SCRIPT, List.of(key(userId, familyId), familyIndexKey(userId)),
                oldJti, newJti, String.valueOf(ttl.toMillis()), familyId);
        long code = result == null ? -1 : result;
        if (code == 1) {
            return RotateResult.ROTATED;
        }
        return code == 0 ? RotateResult.REUSE : RotateResult.MISSING;
    }

    public Optional<String> findJti(UUID userId, String familyId) {
        return Optional.ofNullable(redis.opsForValue().get(key(userId, familyId)));
    }

    public void deleteFamily(UUID userId, String familyId) {
        redis.execute(DELETE_FAMILY_SCRIPT, List.of(key(userId, familyId), familyIndexKey(userId)), familyId);
    }

    public void deleteAllForUser(UUID userId) {
        String familyIndexKey = familyIndexKey(userId);
        Set<String> familyIds = redis.opsForSet().members(familyIndexKey);
        if (familyIds == null || familyIds.isEmpty()) {
            redis.delete(familyIndexKey);
            return;
        }
        List<String> keys = new ArrayList<>(familyIds.size() + 1);
        familyIds.forEach(familyId -> keys.add(key(userId, familyId)));
        keys.add(familyIndexKey);
        redis.delete(keys);
    }

    private String key(UUID userId, String familyId) {
        return KEY_PREFIX + userId + ":" + familyId;
    }

    private String familyIndexKey(UUID userId) {
        return FAMILY_INDEX_PREFIX + userId;
    }
}
