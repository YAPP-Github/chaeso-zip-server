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
    @DisplayName("저장된 코드와 일치하면 코드를 소비하고 인증완료 상태로 전환한다")
    void verifyCode_matchingCode_consumesCodeAndMarksVerified() {
        store.saveCode("user@chaeso.zip", "123456");

        assertThat(store.verifyCode("user@chaeso.zip", "123456")).isTrue();
        assertThat(store.findCode("user@chaeso.zip")).isEmpty();
        assertThat(store.isVerified("user@chaeso.zip")).isTrue();
    }

    @Test
    @DisplayName("재발송으로 교체된 이전 코드는 인증에 사용할 수 없다")
    void verifyCode_replacedCode_doesNotConsumeCurrentCode() {
        store.saveCode("user@chaeso.zip", "123456");
        store.saveCode("user@chaeso.zip", "654321");

        assertThat(store.verifyCode("user@chaeso.zip", "123456")).isFalse();
        assertThat(store.findCode("user@chaeso.zip")).contains("654321");
        assertThat(store.isVerified("user@chaeso.zip")).isFalse();
    }

    @Test
    @DisplayName("인증완료 상태를 지우면 더 이상 인증완료가 아니다")
    void clearVerified() {
        store.saveCode("user@chaeso.zip", "123456");
        store.verifyCode("user@chaeso.zip", "123456");

        store.clearVerified("user@chaeso.zip");

        assertThat(store.isVerified("user@chaeso.zip")).isFalse();
    }

    @Test
    @DisplayName("아무 것도 저장하지 않았으면 인증완료 상태가 아니다")
    void isVerified_defaultFalse() {
        assertThat(store.isVerified("none@chaeso.zip")).isFalse();
    }
}
