package chaeso.zip.server.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import redis.embedded.RedisServer;

class EmailVerificationCodeStoreTest {

    private static final int PORT = 16380;
    private static RedisServer redisServer;

    private StringRedisTemplate template;
    private EmailVerificationCodeStore store;

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
        store = new EmailVerificationCodeStore(
                template,
                new EmailVerificationProperties("no-reply@chaeso.zip", Duration.ofMinutes(5), Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("코드를 저장하면 같은 이메일로 조회된다")
    void saveAndFindCode() {
        store.saveCode("user@chaeso.zip", "123456");

        assertThat(store.findCode("user@chaeso.zip")).contains("123456");
    }

    @Test
    @DisplayName("인증완료로 마킹하면 코드는 삭제되고 인증완료 상태가 된다")
    void markVerified_clearsCodeAndSetsVerified() {
        store.saveCode("user@chaeso.zip", "123456");

        store.markVerified("user@chaeso.zip");

        assertThat(store.findCode("user@chaeso.zip")).isEmpty();
        assertThat(store.isVerified("user@chaeso.zip")).isTrue();
    }

    @Test
    @DisplayName("인증완료 상태를 지우면 더 이상 인증완료가 아니다")
    void clearVerified() {
        store.saveCode("user@chaeso.zip", "123456");
        store.markVerified("user@chaeso.zip");

        store.clearVerified("user@chaeso.zip");

        assertThat(store.isVerified("user@chaeso.zip")).isFalse();
    }

    @Test
    @DisplayName("아무 것도 저장하지 않았으면 인증완료 상태가 아니다")
    void isVerified_defaultFalse() {
        assertThat(store.isVerified("none@chaeso.zip")).isFalse();
    }
}
