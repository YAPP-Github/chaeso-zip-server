package chaeso.zip.server.auth.infrastructure.verification;

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
        new EmailVerificationProperties(
            "no-reply@chaeso.zip", Duration.ofMinutes(5), Duration.ofMinutes(30), 3, Duration.ofSeconds(60)));
  }

  @Test
  @DisplayName("코드를 저장하면 같은 이메일로 조회된다")
  void saveAndFindCode() {
    store.saveCode("user@chaeso.zip", "123456");
    assertThat(store.findCode("user@chaeso.zip")).contains("123456");
  }

  @Test
  @DisplayName("일치하는 코드는 소비되고 인증완료 상태로 전환된다")
  void verifyCode_matching_consumesAndMarksVerified() {
    store.saveCode("user@chaeso.zip", "123456");

    assertThat(store.verifyCode("user@chaeso.zip", "123456")).isTrue();
    assertThat(store.findCode("user@chaeso.zip")).isEmpty();
    assertThat(store.isVerified("user@chaeso.zip")).isTrue();
  }

  @Test
  @DisplayName("재발송으로 교체된 이전 코드는 인증에 쓸 수 없다")
  void verifyCode_replaced_fails() {
    store.saveCode("user@chaeso.zip", "123456");
    store.saveCode("user@chaeso.zip", "654321");

    assertThat(store.verifyCode("user@chaeso.zip", "123456")).isFalse();
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
  @DisplayName("아무 것도 저장하지 않으면 인증완료가 아니다")
  void isVerified_defaultFalse() {
    assertThat(store.isVerified("none@chaeso.zip")).isFalse();
  }

  @Test
  @DisplayName("틀린 코드로 검증하면 실패하고 기존 코드는 소비되지 않는다")
  void verifyCode_wrongCode_doesNotConsume() {
    store.saveCode("user@chaeso.zip", "123456");

    assertThat(store.verifyCode("user@chaeso.zip", "000000")).isFalse();
    assertThat(store.findCode("user@chaeso.zip")).contains("123456");
    assertThat(store.isVerified("user@chaeso.zip")).isFalse();
  }

  @Test
  @DisplayName("허용 횟수를 초과해 틀리면 코드가 무효화되어 정답으로도 검증되지 않는다")
  void verifyCode_exceedsMaxAttempts_invalidatesCode() {
    store.saveCode("user@chaeso.zip", "123456");

    for (int i = 0; i < 3; i++) {
      assertThat(store.verifyCode("user@chaeso.zip", "000000")).isFalse();
    }

    assertThat(store.findCode("user@chaeso.zip")).isEmpty();
    assertThat(store.verifyCode("user@chaeso.zip", "123456")).isFalse();
  }

  @Test
  @DisplayName("이미 검증에 성공한 코드는 재사용할 수 없다")
  void verifyCode_replay_afterSuccessFails() {
    store.saveCode("user@chaeso.zip", "123456");
    store.verifyCode("user@chaeso.zip", "123456");

    assertThat(store.verifyCode("user@chaeso.zip", "123456")).isFalse();
  }

  @Test
  @DisplayName("쿨다운 내 재발송 슬롯은 한 번만 선점된다")
  void tryAcquireSendSlot_withinCooldown_onlyOnce() {
    assertThat(store.tryAcquireSendSlot("user@chaeso.zip")).isTrue();
    assertThat(store.tryAcquireSendSlot("user@chaeso.zip")).isFalse();
  }
}
