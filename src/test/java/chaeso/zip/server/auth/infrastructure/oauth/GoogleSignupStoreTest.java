package chaeso.zip.server.auth.infrastructure.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import redis.embedded.RedisServer;

/**
 * 구글 가입 pending 저장소 동작 검증. 티켓만 클라이언트로 나가고 이메일·sub 는 Redis 에만 남는지,
 * TTL 로 저절로 소멸하는지를 본다.
 */
class GoogleSignupStoreTest {

  private static final int PORT = 16382;
  private static final Duration SIGNUP_TTL = Duration.ofMinutes(10);
  private static final GoogleIdTokenInfo INFO =
      new GoogleIdTokenInfo("google-sub-1", "user@chaeso.zip", "홍길동");

  private static RedisServer redisServer;

  private StringRedisTemplate template;
  private GoogleSignupStore store;

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
    store = new GoogleSignupStore(template, new ObjectMapper(),
        new GoogleProperties("test-client-id", SIGNUP_TTL));
  }

  private String valueOf(String signupToken) {
    return template.opsForValue().get("google-signup:" + signupToken);
  }

  @Test
  @DisplayName("검증된 구글 클레임을 티켓 키에 보관한다")
  void save_storesClaims() throws Exception {
    String signupToken = store.save(INFO);

    GoogleIdTokenInfo stored = new ObjectMapper().readValue(valueOf(signupToken),
        GoogleIdTokenInfo.class);
    assertThat(stored).isEqualTo(INFO);
  }

  @Test
  @DisplayName("보관은 설정한 TTL 로 만료되어 이탈한 가입이 저절로 사라진다")
  void save_appliesTtl() {
    String signupToken = store.save(INFO);

    Long ttl = template.getExpire("google-signup:" + signupToken, TimeUnit.SECONDS);
    assertThat(ttl).isPositive().isLessThanOrEqualTo(SIGNUP_TTL.toSeconds());
  }

  @Test
  @DisplayName("이름이 없는 구글 계정도 보관한다")
  void save_nullName() throws Exception {
    GoogleIdTokenInfo noName = new GoogleIdTokenInfo("google-sub-1", "user@chaeso.zip", null);

    String signupToken = store.save(noName);

    assertThat(new ObjectMapper().readValue(valueOf(signupToken), GoogleIdTokenInfo.class))
        .isEqualTo(noName);
  }

  @Test
  @DisplayName("티켓은 매번 새로 발급되고 이메일·sub 를 노출하지 않는다")
  void save_tokenIsOpaqueAndUnique() {
    String first = store.save(INFO);
    String second = store.save(INFO);

    assertThat(first)
        .isNotEqualTo(second)
        .doesNotContain("user@chaeso.zip", "google-sub-1");
    assertThat(valueOf(first)).isNotNull();
    assertThat(valueOf(second)).isNotNull();
  }

  @Test
  @DisplayName("저장된 티켓을 조회하면 클레임을 돌려주고 키는 그대로 남는다")
  void find_returnsClaimsWithoutDeleting() {
    String signupToken = store.save(INFO);

    Optional<GoogleIdTokenInfo> found = store.find(signupToken);

    assertThat(found).contains(INFO);
    assertThat(valueOf(signupToken)).isNotNull();
  }

  @Test
  @DisplayName("존재하지 않는 티켓을 조회하면 빈 값을 돌려준다")
  void find_missingTicket_returnsEmpty() {
    assertThat(store.find("no-such-token")).isEmpty();
  }

  @Test
  @DisplayName("티켓을 폐기하면 더 이상 조회되지 않는다")
  void delete_removesTicket() {
    String signupToken = store.save(INFO);

    store.delete(signupToken);

    assertThat(valueOf(signupToken)).isNull();
    assertThat(store.find(signupToken)).isEmpty();
  }
}
