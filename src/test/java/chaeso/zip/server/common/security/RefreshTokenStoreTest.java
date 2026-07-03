package chaeso.zip.server.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
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

        JwtProperties props = new JwtProperties(
                "test-secret-0123456789-0123456789-0123456789", Duration.ofMinutes(30), Duration.ofDays(14));
        store = new RefreshTokenStore(template, props);
    }

    @Test
    @DisplayName("저장하면 같은 family로 jti를 조회할 수 있다")
    void saveAndFind() {
        store.save(USER_ID, "fam-1", "jti-1");

        assertThat(store.findJti(USER_ID, "fam-1")).contains("jti-1");
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
    }
}