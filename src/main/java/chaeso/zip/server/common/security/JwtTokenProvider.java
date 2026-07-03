package chaeso.zip.server.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * 자체 JWT(Access/Refresh) 발급 및 검증(HS256, 시계 오차 30초 허용).
 * access/refresh 는 {@code type} 클레임으로 구분해 혼용 방지. sub = userId(uuid), role claim 없음.
 */
@Component
public class JwtTokenProvider {

  private static final String CLAIM_TYPE = "type";
  private static final String CLAIM_FAMILY = "familyId";
  private static final String TYPE_ACCESS = "access";
  private static final String TYPE_REFRESH = "refresh";
  private static final long CLOCK_SKEW_SECONDS = 30;

  private final SecretKey key;
  private final Duration accessTtl;
  private final Duration refreshTtl;

  public JwtTokenProvider(JwtProperties properties) {
    this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    this.accessTtl = properties.accessTtl();
    this.refreshTtl = properties.refreshTtl();
  }

  public String createAccessToken(UUID userId) {
    Date now = new Date();
    return Jwts.builder()
        .subject(userId.toString())
        .claim(CLAIM_TYPE, TYPE_ACCESS)
        .issuedAt(now)
        .expiration(new Date(now.getTime() + accessTtl.toMillis()))
        .signWith(key)
        .compact();
  }

  public String createRefreshToken(UUID userId, String familyId, String jti) {
    Date now = new Date();
    return Jwts.builder()
        .subject(userId.toString())
        .id(jti)
        .claim(CLAIM_FAMILY, familyId)
        .claim(CLAIM_TYPE, TYPE_REFRESH)
        .issuedAt(now)
        .expiration(new Date(now.getTime() + refreshTtl.toMillis()))
        .signWith(key)
        .compact();
  }

  public UserPrincipal parseAccess(String token) {
    Claims claims = parse(token, TYPE_ACCESS);
    return new UserPrincipal(UUID.fromString(claims.getSubject()));
  }

  public RefreshTokenInfo parseRefresh(String token) {
    Claims claims = parse(token, TYPE_REFRESH);
    return new RefreshTokenInfo(
        UUID.fromString(claims.getSubject()), claims.get(CLAIM_FAMILY, String.class), claims.getId());
  }

  private Claims parse(String token, String expectedType) {
    Claims claims = Jwts.parser()
        .verifyWith(key)
        .clockSkewSeconds(CLOCK_SKEW_SECONDS)
        .build()
        .parseSignedClaims(token)
        .getPayload();
    if (!expectedType.equals(claims.get(CLAIM_TYPE, String.class))) {
      throw new JwtException("토큰 타입이 올바르지 않습니다. 기대: " + expectedType);
    }
    return claims;
  }
}
