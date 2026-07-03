package chaeso.zip.server.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

  private static final String SECRET = "test-secret-0123456789-0123456789-0123456789";
  private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  private JwtTokenProvider provider(Duration accessTtl) {
    return new JwtTokenProvider(new JwtProperties(SECRET, accessTtl, Duration.ofDays(14)));
  }

  @Test
  @DisplayName("access 토큰을 발급하고 파싱하면 userId가 복원된다")
  void accessRoundTrip() {
    JwtTokenProvider provider = provider(Duration.ofMinutes(30));

    String token = provider.createAccessToken(USER_ID);
    UserPrincipal principal = provider.parseAccess(token);

    assertThat(principal.userId()).isEqualTo(USER_ID);
  }

  @Test
  @DisplayName("refresh 토큰을 발급하고 파싱하면 userId/familyId/jti가 복원된다")
  void refreshRoundTrip() {
    JwtTokenProvider provider = provider(Duration.ofMinutes(30));

    String token = provider.createRefreshToken(USER_ID, "fam-1", "jti-1");
    RefreshTokenInfo info = provider.parseRefresh(token);

    assertThat(info.userId()).isEqualTo(USER_ID);
    assertThat(info.familyId()).isEqualTo("fam-1");
    assertThat(info.jti()).isEqualTo("jti-1");
  }

  @Test
  @DisplayName("refresh 토큰을 access로 파싱하면 예외가 발생한다(타입 분리)")
  void refreshCannotBeParsedAsAccess() {
    JwtTokenProvider provider = provider(Duration.ofMinutes(30));
    String refresh = provider.createRefreshToken(USER_ID, "fam", "jti");

    assertThatThrownBy(() -> provider.parseAccess(refresh)).isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("변조된 토큰은 파싱에 실패한다")
  void tamperedToken_throws() {
    JwtTokenProvider provider = provider(Duration.ofMinutes(30));
    String token = provider.createAccessToken(USER_ID) + "x";

    assertThatThrownBy(() -> provider.parseAccess(token)).isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("UUID 형식이 아닌 subject는 JWT 파싱 예외로 통일한다")
  void malformedSubject_throwsJwtException() {
    JwtTokenProvider provider = provider(Duration.ofMinutes(30));
    Date now = new Date();
    String token = Jwts.builder()
        .subject("not-a-uuid")
        .claim("type", "access")
        .issuedAt(now)
        .expiration(new Date(now.getTime() + Duration.ofMinutes(30).toMillis()))
        .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
        .compact();

    assertThatThrownBy(() -> provider.parseAccess(token))
        .isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("만료된 토큰은 시계 오차 허용범위를 넘으면 파싱에 실패한다")
  void expiredToken_throws() {
    JwtTokenProvider provider = provider(Duration.ofSeconds(-60));
    String token = provider.createAccessToken(USER_ID);

    assertThatThrownBy(() -> provider.parseAccess(token)).isInstanceOf(JwtException.class);
  }
}
