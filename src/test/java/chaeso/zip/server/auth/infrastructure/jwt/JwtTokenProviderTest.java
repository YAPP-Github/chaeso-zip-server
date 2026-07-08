package chaeso.zip.server.auth.infrastructure.jwt;

import static chaeso.zip.server.auth.infrastructure.jwt.JwtTestFixture.FIXED_NOW;
import static chaeso.zip.server.auth.infrastructure.jwt.JwtTestFixture.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import chaeso.zip.server.auth.application.UserPrincipal;
import chaeso.zip.server.auth.domain.InvalidTokenException;
import io.jsonwebtoken.Jwts;
import java.time.Duration;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

  @Test
  @DisplayName("access 토큰을 발급하고 파싱하면 사용자 식별자가 복원된다")
  void accessRoundTrip() {
    JwtTokenProvider provider = JwtTestFixture.provider();

    String token = provider.createAccessToken(USER_ID);
    UserPrincipal principal = provider.parseAccess(token);

    assertThat(principal.userId()).isEqualTo(USER_ID);
  }

  @Test
  @DisplayName("refresh 토큰을 발급하고 파싱하면 세션 식별자가 복원된다")
  void refreshRoundTrip() {
    JwtTokenProvider provider = JwtTestFixture.provider();

    String token = provider.createRefreshToken(USER_ID, "family-1", "jti-1");
    RefreshTokenInfo info = provider.parseRefresh(token);

    assertThat(info.userId()).isEqualTo(USER_ID);
    assertThat(info.familyId()).isEqualTo("family-1");
    assertThat(info.jti()).isEqualTo("jti-1");
  }

  @Test
  @DisplayName("Base64URL secret으로 외부에서 서명한 access 토큰을 파싱한다")
  void base64UrlSecretTokenIsParsed() {
    JwtTokenProvider provider = JwtTestFixture.provider();
    String token = Jwts.builder()
        .subject(USER_ID.toString())
        .claim("type", "access")
        .expiration(Date.from(FIXED_NOW.plus(Duration.ofMinutes(30))))
        .signWith(JwtTestFixture.signingKey())
        .compact();

    UserPrincipal principal = provider.parseAccess(token);

    assertThat(principal.userId()).isEqualTo(USER_ID);
  }

  @Test
  @DisplayName("refresh 토큰 claim 타입이 잘못되면 공통 토큰 예외로 변환한다")
  void invalidRefreshClaimTypeIsRejected() {
    JwtTokenProvider provider = JwtTestFixture.provider();
    String token = Jwts.builder()
        .subject(USER_ID.toString())
        .id("jti-1")
        .claim("familyId", 123)
        .claim("type", "refresh")
        .expiration(Date.from(FIXED_NOW.plus(Duration.ofDays(14))))
        .signWith(JwtTestFixture.signingKey())
        .compact();

    assertThatThrownBy(() -> provider.parseRefresh(token))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  @DisplayName("refresh 토큰 familyId가 없으면 파싱할 수 없다")
  void refreshWithoutFamilyIdIsRejected() {
    JwtTokenProvider provider = JwtTestFixture.provider();
    String token = Jwts.builder()
        .subject(USER_ID.toString())
        .id("jti-1")
        .claim("type", "refresh")
        .expiration(Date.from(FIXED_NOW.plus(Duration.ofDays(14))))
        .signWith(JwtTestFixture.signingKey())
        .compact();

    assertThatThrownBy(() -> provider.parseRefresh(token))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  @DisplayName("refresh 토큰 jti가 없으면 파싱할 수 없다")
  void refreshWithoutJtiIsRejected() {
    JwtTokenProvider provider = JwtTestFixture.provider();
    String token = Jwts.builder()
        .subject(USER_ID.toString())
        .claim("familyId", "family-1")
        .claim("type", "refresh")
        .expiration(Date.from(FIXED_NOW.plus(Duration.ofDays(14))))
        .signWith(JwtTestFixture.signingKey())
        .compact();

    assertThatThrownBy(() -> provider.parseRefresh(token))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  @DisplayName("refresh 토큰은 access 인증에 사용할 수 없다")
  void refreshCannotBeParsedAsAccess() {
    JwtTokenProvider provider = JwtTestFixture.provider();
    String refresh = provider.createRefreshToken(USER_ID, "family-1", "jti-1");

    assertThatThrownBy(() -> provider.parseAccess(refresh))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  @DisplayName("access 토큰은 refresh 파싱에 사용할 수 없다")
  void accessCannotBeParsedAsRefresh() {
    JwtTokenProvider provider = JwtTestFixture.provider();
    String access = provider.createAccessToken(USER_ID);

    assertThatThrownBy(() -> provider.parseRefresh(access))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  @DisplayName("변조된 토큰은 파싱할 수 없다")
  void tamperedTokenIsRejected() {
    JwtTokenProvider provider = JwtTestFixture.provider();
    String token = provider.createAccessToken(USER_ID);
    String tamperedToken = token.substring(0, token.length() - 1)
        + (token.endsWith("a") ? "b" : "a");

    assertThatThrownBy(() -> provider.parseAccess(tamperedToken))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  @DisplayName("빈 토큰은 공통 토큰 예외로 변환한다")
  void blankTokenIsRejected() {
    JwtTokenProvider provider = JwtTestFixture.provider();

    assertThatThrownBy(() -> provider.parseAccess(" "))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  @DisplayName("만료된 access 토큰은 파싱할 수 없다")
  void expiredTokenIsRejected() {
    JwtTokenProvider provider = JwtTestFixture.provider(Duration.ofSeconds(-60));
    String token = provider.createAccessToken(USER_ID);

    assertThatThrownBy(() -> provider.parseAccess(token))
        .isInstanceOf(InvalidTokenException.class);
  }
}
