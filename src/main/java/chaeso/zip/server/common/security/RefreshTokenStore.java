package chaeso.zip.server.common.security;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Refresh 토큰을 Redis 로 관리한다. 키: {@code refresh:{userId}:{familyId}} -> 현재 유효 jti.
 * 같은 family 로 다시 save 하면 회전(덮어쓰기 + TTL 갱신)된다.
 */
@Component
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "refresh:";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RefreshTokenStore(StringRedisTemplate redis, JwtProperties properties) {
        this.redis = redis;
        this.ttl = properties.refreshTtl();
    }

    public void save(UUID userId, String familyId, String jti) {
        redis.opsForValue().set(key(userId, familyId), jti, ttl);
    }

    public Optional<String> findJti(UUID userId, String familyId) {
        return Optional.ofNullable(redis.opsForValue().get(key(userId, familyId)));
    }

    public void deleteFamily(UUID userId, String familyId) {
        redis.delete(key(userId, familyId));
    }

    public void deleteAllForUser(UUID userId) {
        Set<String> keys = redis.keys(KEY_PREFIX + userId + ":*");
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private String key(UUID userId, String familyId) {
        return KEY_PREFIX + userId + ":" + familyId;
    }
}
