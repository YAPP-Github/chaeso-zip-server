package chaeso.zip.server.auth.infrastructure.jwt;

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import javax.crypto.SecretKey;

/**
 * JWT 테스트 공용 픽스처. 고정 시계 기반.
 */
public final class JwtTestFixture {

  public static final String SECRET =
      "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWYwMTIzNDU2Nzg5YWJjZGVm";
  public static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  public static final Instant FIXED_NOW = Instant.parse("2026-07-05T00:00:00Z");
  public static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

  private JwtTestFixture() {
  }

  public static JwtTokenProvider provider() {
    return provider(Duration.ofMinutes(30));
  }

  public static JwtTokenProvider provider(Duration accessTtl) {
    return new JwtTokenProvider(
        new JwtProperties(SECRET, accessTtl, Duration.ofDays(14), Duration.ofDays(90)),
        FIXED_CLOCK);
  }

  /** 테스트에서 토큰을 직접 서명해 만들 때 사용하는 키. */
  public static SecretKey signingKey() {
    return Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(SECRET));
  }
}
