package chaeso.zip.server.auth.infrastructure.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.data.Offset.offset;

import chaeso.zip.server.auth.infrastructure.jwt.RefreshTokenStore.RotateResult;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
 * Refresh Token family 저장소 동작 검증
 */
class RefreshTokenStoreTest {

  private static final int PORT = 16381;
  private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final String FAMILY_ID = "family-1";
  private static final Instant T0 = Instant.parse("2026-07-05T00:00:00Z");
  private static final Duration REFRESH_TTL = Duration.ofDays(14);
  private static final Duration ABSOLUTE_TTL = Duration.ofDays(90);

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
    store = storeAt(T0);
  }

  private RefreshTokenStore storeAt(Instant now) {
    return new RefreshTokenStore(
        template,
        new JwtProperties("dummy-secret", Duration.ofMinutes(30), REFRESH_TTL, ABSOLUTE_TTL),
        Clock.fixed(now, ZoneOffset.UTC));
  }

  private Long expireMillis() {
    return template.getExpire("refresh:" + USER_ID + ":" + FAMILY_ID, TimeUnit.MILLISECONDS);
  }

  @Test
  @DisplayName("저장한 jti로 회전하면 성공하고 새 jti가 저장된다")
  void rotate_matchingJti_succeeds() {
    store.save(USER_ID, FAMILY_ID, "jti-1");

    assertThat(store.rotate(USER_ID, FAMILY_ID, "jti-1", "jti-2").result())
        .isEqualTo(RotateResult.ROTATED);
    assertThat(store.rotate(USER_ID, FAMILY_ID, "jti-2", "jti-3").result())
        .isEqualTo(RotateResult.ROTATED);
  }

  @Test
  @DisplayName("저장된 적 없는 family로 회전하면 INVALID를 반환한다")
  void rotate_unknownFamily_isInvalid() {
    assertThat(store.rotate(USER_ID, "no-such-family", "jti-1", "jti-2").result())
        .isEqualTo(RotateResult.INVALID);
  }

  @Test
  @DisplayName("이미 회전된 옛 jti를 다시 쓰면 REUSED를 반환하고 family 전체를 폐기한다")
  void rotate_replayedJti_isReusedAndRevokesFamily() {
    store.save(USER_ID, FAMILY_ID, "jti-1");
    store.rotate(USER_ID, FAMILY_ID, "jti-1", "jti-2");

    assertThat(store.rotate(USER_ID, FAMILY_ID, "jti-1", "jti-3").result())
        .isEqualTo(RotateResult.REUSED);
    assertThat(store.rotate(USER_ID, FAMILY_ID, "jti-2", "jti-4").result())
        .isEqualTo(RotateResult.INVALID);
  }

  @Test
  @DisplayName("폐기한 family로는 회전할 수 없다")
  void revoke_thenRotate_isInvalid() {
    store.save(USER_ID, FAMILY_ID, "jti-1");

    store.revoke(USER_ID, FAMILY_ID);

    assertThat(store.rotate(USER_ID, FAMILY_ID, "jti-1", "jti-2").result())
        .isEqualTo(RotateResult.INVALID);
  }

  @Test
  @DisplayName("없는 family를 폐기해도 예외가 나지 않는다")
  void revoke_unknownFamily_isSilent() {
    assertThatCode(() -> store.revoke(USER_ID, "no-such-family")).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("한 사용자의 family 폐기는 다른 사용자의 family에 영향이 없다")
  void revoke_isScopedToUser() {
    UUID other = UUID.fromString("22222222-2222-2222-2222-222222222222");
    store.save(USER_ID, FAMILY_ID, "jti-1");
    store.save(other, FAMILY_ID, "jti-1");

    store.revoke(USER_ID, FAMILY_ID);

    assertThat(store.rotate(other, FAMILY_ID, "jti-1", "jti-2").result())
        .isEqualTo(RotateResult.ROTATED);
  }

  @Test
  @DisplayName("회전하면 비활성 윈도우가 다시 refresh-ttl로 갱신된다 (슬라이딩)")
  void rotate_slidesInactivityWindow() {
    store.save(USER_ID, FAMILY_ID, "jti-1");

    storeAt(T0.plus(Duration.ofDays(13))).rotate(USER_ID, FAMILY_ID, "jti-1", "jti-2");

    assertThat(expireMillis()).isCloseTo(REFRESH_TTL.toMillis(), offset(5_000L));
  }

  @Test
  @DisplayName("jti에 구분자가 들어 있어도 마지막 구분자 기준으로 잘라 정상 회전한다")
  void rotate_jtiContainingDelimiter_stillRotates() {
    store.save(USER_ID, FAMILY_ID, "aaa|bbb");

    assertThat(store.rotate(USER_ID, FAMILY_ID, "aaa|bbb", "ccc|ddd").result())
        .isEqualTo(RotateResult.ROTATED);
    assertThat(store.rotate(USER_ID, FAMILY_ID, "ccc|ddd", "jti-3").result())
        .isEqualTo(RotateResult.ROTATED);
  }

  @Test
  @DisplayName("구분자가 없는 손상된 값은 예외 대신 INVALID로 처리하고 키를 지운다")
  void rotate_corruptedValue_isInvalid() {
    template.opsForValue().set("refresh:" + USER_ID + ":" + FAMILY_ID, "no-delimiter");

    assertThat(store.rotate(USER_ID, FAMILY_ID, "no-delimiter", "jti-2").result())
        .isEqualTo(RotateResult.INVALID);
    assertThat(template.hasKey("refresh:" + USER_ID + ":" + FAMILY_ID)).isFalse();
  }

  @Test
  @DisplayName("절대만료를 넘기면 계속 회전해 왔더라도 INVALID가 된다")
  void rotate_pastAbsoluteDeadline_isInvalid() {
    store.save(USER_ID, FAMILY_ID, "jti-1");

    assertThat(
        storeAt(T0.plus(Duration.ofDays(91))).rotate(USER_ID, FAMILY_ID, "jti-1", "jti-2").result())
        .isEqualTo(RotateResult.INVALID);
  }

  @Test
  @DisplayName("save는 키에 비활성 윈도우(refresh-ttl)를 걸고 그 값을 반환한다")
  void save_appliesAndReturnsRefreshTtl() {
    assertThat(store.save(USER_ID, FAMILY_ID, "jti-1")).isEqualTo(REFRESH_TTL);
    assertThat(expireMillis()).isCloseTo(REFRESH_TTL.toMillis(), offset(5_000L));
  }

  @Test
  @DisplayName("회전 결과의 TTL은 실제 키 TTL과 같고, 절대만료가 가까우면 함께 잘린다")
  void rotate_returnsAppliedTtl() {
    store.save(USER_ID, FAMILY_ID, "jti-1");

    Duration ttl = storeAt(T0.plus(Duration.ofDays(85)))
        .rotate(USER_ID, FAMILY_ID, "jti-1", "jti-2").ttl();

    assertThat(ttl.toMillis()).isCloseTo(Duration.ofDays(5).toMillis(), offset(5_000L));
    assertThat(ttl.toMillis()).isCloseTo(expireMillis(), offset(5_000L));
  }

  @Test
  @DisplayName("회전에 실패하면 TTL은 null이다")
  void rotate_failure_hasNoTtl() {
    assertThat(store.rotate(USER_ID, "no-such-family", "jti-1", "jti-2").ttl()).isNull();
  }

  @Test
  @DisplayName("같은 jti로 동시에 회전하면 정확히 하나만 성공하고 나머지는 재사용으로 처리된다")
  void rotate_concurrentSameJti_onlyOneWins() throws InterruptedException {
    int threads = 8;
    store.save(USER_ID, FAMILY_ID, "jti-1");
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);
    List<RotateResult> results = Collections.synchronizedList(new ArrayList<>());
    ExecutorService pool = Executors.newFixedThreadPool(threads);

    for (int i = 0; i < threads; i++) {
      String newJti = "jti-new-" + i;
      pool.execute(() -> {
        try {
          start.await();
          results.add(store.rotate(USER_ID, FAMILY_ID, "jti-1", newJti).result());
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
        } finally {
          done.countDown();
        }
      });
    }
    start.countDown();
    assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
    pool.shutdownNow();

    assertThat(results).hasSize(threads);
    assertThat(results).filteredOn(result -> result == RotateResult.ROTATED).hasSize(1);
    assertThat(results).filteredOn(result -> result == RotateResult.REUSED).isNotEmpty();
    assertThat(template.hasKey("refresh:" + USER_ID + ":" + FAMILY_ID)).isFalse();
  }
}
