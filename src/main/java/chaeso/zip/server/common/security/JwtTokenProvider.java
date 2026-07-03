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
 * 토큰 종류를 클레임으로 구분해 Access 토큰과 Refresh 토큰의 혼용을 막는다.
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
    return new UserPrincipal(parseUserId(claims));
  }

  public RefreshTokenInfo parseRefresh(String token) {
    Claims claims = parse(token, TYPE_REFRESH);
    return new RefreshTokenInfo(
        parseUserId(claims), claims.get(CLAIM_FAMILY, String.class), claims.getId());
  }

  private UUID parseUserId(Claims claims) {
    String subject = claims.getSubject();
    if (subject == null) {
      throw new JwtException("토큰 subject가 없습니다.");
    }
    try {
      return UUID.fromString(subject);
    } catch (IllegalArgumentException e) {
      throw new JwtException("토큰 subject가 UUID 형식이 아닙니다.", e);
    }
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
