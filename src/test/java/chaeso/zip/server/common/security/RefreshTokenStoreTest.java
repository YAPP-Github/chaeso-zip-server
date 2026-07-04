package chaeso.zip.server.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import chaeso.zip.server.support.AuthFixtures;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import redis.embedded.RedisServer;

class RefreshTokenStoreTest {

    private static final int PORT = 16379;
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static RedisServer redisServer;

    private StringRedisTemplate template;
    private RefreshTokenStore store;

    @BeforeAll
    static void startRedis() throws IOException {
        redisServer = RedisServer.newRedisServer().port(PORT).build();
        redisServer.start();
    }

    @AfterAll
    static void stopRedis() throws IOException {
        redisServer.stop();
    }

    @BeforeEach
    void setUp() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory("localhost", PORT);
        factory.afterPropertiesSet();
        template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        template.getConnectionFactory().getConnection().serverCommands().flushAll();

        store = new RefreshTokenStore(template, AuthFixtures.jwtProperties());
    }

    @Test
    @DisplayName("저장하면 같은 family로 jti를 조회할 수 있다")
    void saveAndFind() {
        store.save(USER_ID, "fam-1", "jti-1");

        assertThat(store.findJti(USER_ID, "fam-1")).contains("jti-1");
    }

    @Test
    @DisplayName("저장하면 유저별 family 인덱스에도 family가 추가된다")
    void saveIndexesFamilyForUser() {
        store.save(USER_ID, "fam-1", "jti-1");

        assertThat(template.opsForSet().members("refresh-families:" + USER_ID))
                .containsExactly("fam-1");
    }

    @Test
    @DisplayName("같은 family로 다시 저장하면 jti가 덮어써진다")
    void rotateOverwrites() {
        store.save(USER_ID, "fam-1", "jti-old");
        store.save(USER_ID, "fam-1", "jti-new");

        assertThat(store.findJti(USER_ID, "fam-1")).contains("jti-new");
    }

    @Test
    @DisplayName("family를 삭제하면 조회되지 않는다")
    void deleteFamily() {
        store.save(USER_ID, "fam-1", "jti-1");

        store.deleteFamily(USER_ID, "fam-1");

        assertThat(store.findJti(USER_ID, "fam-1")).isEmpty();
    }

    @Test
    @DisplayName("유저의 모든 family를 삭제하면 어떤 family도 조회되지 않는다")
    void deleteAllForUser() {
        store.save(USER_ID, "fam-1", "jti-1");
        store.save(USER_ID, "fam-2", "jti-2");

        store.deleteAllForUser(USER_ID);

        assertThat(store.findJti(USER_ID, "fam-1")).isEmpty();
        assertThat(store.findJti(USER_ID, "fam-2")).isEmpty();
        assertThat(template.hasKey("refresh-families:" + USER_ID)).isFalse();
    }

    @Test
    @DisplayName("저장된 jti와 일치하면 원자적으로 회전(ROTATED)하고 새 jti가 저장된다")
    void rotateSuccess() {
        store.save(USER_ID, "fam-1", "jti-1");

        assertThat(store.rotate(USER_ID, "fam-1", "jti-1", "jti-2"))
                .isEqualTo(RefreshTokenStore.RotateResult.ROTATED);
        assertThat(store.findJti(USER_ID, "fam-1")).contains("jti-2");
    }

    @Test
    @DisplayName("회전하면 토큰과 family 인덱스 TTL이 함께 갱신된다")
    void rotateRefreshesTokenAndFamilyIndexTtl() {
        String tokenKey = "refresh:" + USER_ID + ":fam-1";
        String familyIndexKey = "refresh-families:" + USER_ID;
        store.save(USER_ID, "fam-1", "jti-1");
        template.expire(tokenKey, Duration.ofMinutes(1));
        template.expire(familyIndexKey, Duration.ofMinutes(1));

        assertThat(store.rotate(USER_ID, "fam-1", "jti-1", "jti-2"))
                .isEqualTo(RefreshTokenStore.RotateResult.ROTATED);

        assertThat(template.getExpire(tokenKey)).isGreaterThan(Duration.ofDays(13).toSeconds());
        assertThat(template.getExpire(familyIndexKey)).isGreaterThan(Duration.ofDays(13).toSeconds());
    }

    @Test
    @DisplayName("저장된 jti와 다르면 REUSE를 반환하고 값을 바꾸지 않는다")
    void rotateReuse() {
        store.save(USER_ID, "fam-1", "jti-current");

        assertThat(store.rotate(USER_ID, "fam-1", "jti-stale", "jti-new"))
                .isEqualTo(RefreshTokenStore.RotateResult.REUSE);
        assertThat(store.findJti(USER_ID, "fam-1")).contains("jti-current");
    }

    @Test
    @DisplayName("키가 없으면 MISSING을 반환한다")
    void rotateMissing() {
        assertThat(store.rotate(USER_ID, "fam-1", "jti-1", "jti-2"))
                .isEqualTo(RefreshTokenStore.RotateResult.MISSING);
    }

    @Test
    @DisplayName("같은 토큰으로 동시 회전 시 정확히 한 번만 성공한다")
    void rotateIsAtomicUnderConcurrency() throws Exception {
        store.save(USER_ID, "fam-1", "jti-1");
        int threads = 16;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger rotated = new java.util.concurrent.atomic.AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            String newJti = "jti-new-" + i;
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                    if (store.rotate(USER_ID, "fam-1", "jti-1", newJti)
                            == RefreshTokenStore.RotateResult.ROTATED) {
                        rotated.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        for (Future<?> future : futures) {
            future.get();
        }

        assertThat(rotated.get()).isEqualTo(1);
    }
}